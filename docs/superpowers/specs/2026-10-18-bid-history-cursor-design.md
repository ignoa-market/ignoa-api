# 입찰 내역 커서 페이징 설계

- 작성일: 2026-10-18
- 브랜치: ignoa-api `fix/bid-history-limit`(develop 기준), ignoa-web `main`
- 분류: 읽기 성능 개선 — 기본 결함 1번

## 왜 필요한가

상품 상세의 입찰 내역 API(`GET /api/items/{itemId}/bids`)는 해당 상품의 입찰을 **전부** 반환한다. 입찰 최소 단위가 없어(의도적, 1원 이상이면 허용) 상품 하나의 입찰 수에는 상한이 없고, 인기 상품일수록 조회도 입찰도 많아져 "진입 수 × 입찰 수"로 부담이 함께 커진다. 지금 데이터(상품당 수십 건)에서는 체감 문제가 없지만, 응답 크기에 상한이 없다는 위험을 제거한다.

함께, 프론트에서 같은 입찰이 세 경로(입찰 응답, WebSocket 알림, 재조회)로 화면을 바꾸던 흐름을 단순하게 정리한다.

## 목표 / 비목표

목표
- 입찰 내역 응답 크기를 입찰 수와 무관하게 일정하게 만든다(커서 방식, 기본 20건).
- 화면 갱신 경로를 하나로 정리한다: 그래프·목록은 WebSocket 알림으로만 바꾼다.
- 차트는 "전체적인 흐름"을 보여 준다: 최근 20건 + 시작가 기준선 + 끌어서 과거 보기.

비목표
- 입찰 최소 단위 도입(의도적으로 없음).
- 전체 입찰 수 표시. "(N건)"과 `#N`은 제거한다. 필요해지면 나중에 `ItemDetail.bidCount`로 추가한다(필드 추가라 하위 호환).
- 시간 범위 버튼(1h/3h/1d). 데이터가 드물고 개수 기준 조회와 맞지 않는다.
- 끊긴 동안 놓친 입찰을 정밀하게 메우기. 재연결 시 첫 페이지만 다시 받으며, 끊긴 사이 20건 넘게 쌓이면 중간이 빌 수 있는 것을 허용한다.

실패로 볼 것
- 현재가가 표시 중인 값보다 낮게 바뀌는 것.
- 같은 입찰이 그래프·목록에 두 번 보이는 것.

## 전제 검증 — ID 순서 = 가격 순서

같은 상품 안에서는 bid ID 내림차순이 가격 내림차순과 같다. 커서를 ID로 잡는 근거다.

- [백엔드] `BidService.placeBid`는 `ItemRepository.raiseCurrentPriceIfHigher`(조건 `currentPrice < :bidPrice`)가 성공한 뒤에만 Bid를 INSERT한다.
- 같은 상품에 동시 입찰이 오면 뒤 트랜잭션의 UPDATE는 앞 트랜잭션 커밋까지 상품 행 잠금에서 대기하므로 INSERT 순서가 커밋 순서를 따른다. Redis 락이 풀려도 DB 행 잠금만으로 보장된다.
- Bid 저장 경로는 이 한 곳뿐이다. 운영 데모 시드(`~/Downloads/Mock-seed/scripts/build_bids.py`)도 가격 오름차순으로 순서대로 INSERT했다.

## 전체 흐름

```
[상세 진입 / WebSocket 재연결]
  [프론트] GET /api/items/{id}       → 상품 상세(시작가, 현재가 등)
  [프론트] GET /api/items/{id}/bids  → 최근 20건 + hasNext
  [프론트] 차트·목록을 새로 그림 (재연결이면 기존 목록을 이 결과로 교체)

[누군가 입찰]
  [프론트-입찰자] POST /api/items/{id}/bids
  [백엔드] 분산 락 → 트랜잭션(조건부 UPDATE → Bid INSERT → 이벤트 발행) → 커밋 → 201 응답
  [백엔드] AFTER_COMMIT → /topic/items/{id} 로 BidBroadcast(bidId 포함)
  [프론트-입찰자] 응답 수신: 현재가 = max(표시값, 내 입찰가)   ← 현재가만, 즉시성 목적
  [프론트-전원]   알림 수신: 현재가 = max(표시값, 알림값)
                  bidId가 목록에 없으면 차트 오른쪽 끝·목록 맨 위에 추가
                  (재조회 없음)

[차트를 과거로 끌기 / 목록 끝까지 스크롤]
  [프론트] GET /api/items/{id}/bids?beforeBidId={가장 오래된 bidId}
  [백엔드] 이전 20건 + hasNext
  [프론트] 왼쪽(목록은 아래)에 이어 붙임, hasNext=false면 더 요청하지 않음
```

입찰자에게 응답과 알림 중 무엇이 먼저 도착할지는 정해져 있지 않다. 그래서 현재가는 두 경로 모두 max 비교, 목록은 알림 한 경로 + bidId 중복 확인으로 처리한다.

## 백엔드 (ignoa-api) — 사용자가 직접 구현

구현 계획에는 체크리스트와 테스트 목록만 두고, 한 단계씩 확인하며 진행한다.

### API

`GET /api/items/{itemId}/bids?beforeBidId=&size=`

- 인증 불필요(기존과 동일).
- 요청 파라미터: `ChatMessagePageRequest`와 같은 모양의 record `BidHistoryCursorRequest`
  - `beforeBidId`: 선택, `@Min(1)`. 없으면 최신부터.
  - `size`: 선택, `@Min(1)`, `@Max(50)`, 기본 20.
- 조회: `BidRepository.findBidHistory` — `item_id = :itemId AND (:beforeBidId IS NULL OR id < :beforeBidId)`, `ORDER BY id DESC`, bidder fetch join(닉네임 필요). `Slice<Bid>` + `PageRequest.of(0, size)`로 OFFSET 없이 size+1건을 조회해 `hasNext`를 얻는다(커서 위치는 WHERE 조건이 정함).
- `nextCursor`: 다음 페이지가 있으면 이번 목록의 마지막(가장 작은) bid ID, 없으면 null.
- 상품 존재 확인(`itemRepository.existsById`)은 유지한다. 결과가 비었을 때 "없는 상품(404 ITEM_NOT_FOUND)"과 "입찰 0건"을 구분하기 위함. 삭제된 상품은 `@SQLRestriction` 때문에 404가 된다(기존과 동일).
- 응답: `ApiResponse<CursorResponse<BidHistory>>` — 공용 커서 응답 `global/common/CursorResponse(content, nextCursor, hasNext)`를 새로 둔다. Spring `Slice`는 페이지 번호(Offset) 기반 타입이라, 커서 방식임을 드러내고 다음 커서를 서버가 정하도록 분리했다.
  JSON은 snake_case: `{ "content": [...], "next_cursor": 6, "has_next": true }`. `BidHistory`(bid_id, bidder_nickname, price, status, created_at)는 항목으로 그대로 둔다.
- 기존 `BidRepository.findByItemIdWithBidder`(전체 조회, 가격순)는 삭제한다.
- 인덱스: InnoDB 보조 인덱스 `item_id`에 PK가 붙으므로 `(item_id, id)` 범위 조회에 쓸 수 있다. 운영 DB에 이 인덱스가 실제로 있는지는 미확인 — 기준선 측정 단계에서 `SHOW INDEX FROM bids`로 확인한다.

### 입찰 알림

- `BidBroadcast`와 `BidPlaceEvent`에 `bidId`를 추가한다. `BidPlaceEvent.of`는 `bidRepository.save` 뒤에 호출되므로(IDENTITY) ID가 채워져 있다.
- `BidEventListener`에서 그대로 옮겨 담는다. JSON 필드명은 `bid_id`.

### 테스트

- 첫 페이지: 최신 size건, ID 내림차순, hasNext 정확성(정확히 size건일 때 false).
- 커서: `beforeBidId` 이전 건만, 페이지 경계에서 중복·누락 없음.
- 다른 상품 입찰이 섞이지 않음.
- 없는 상품 → 404, size 0·51 → 400, size 미지정 → 20.
- 알림 payload에 `bid_id`가 포함됨.

## 프론트 (ignoa-web) — Claude 구현

### 데이터·흐름 (`ProductDetailPage.tsx`, `src/api/item.ts`, `src/lib/bidSocket.ts`)

- `bidApi.getBids(itemId, beforeBidId?)` → `{ content, next_cursor, has_next }` 타입으로 변경.
- 입찰 성공 후 재조회(현재 344행 부근)를 **삭제**한다. 소켓이 연결돼 있으면 알림으로, 끊겨 있으면 재연결 처리로 맞춰진다.
- 알림 처리: `bid_id`로 중복을 확인하고 실제 ID로 추가한다. 임시 음수 ID와 "가격+시각" 중복 비교(`mergeBids`)는 제거한다.
- 재연결 처리: 상품 상세 + 첫 페이지를 다시 받아 **교체**한다(이전에 불러온 과거 페이지는 버림).
- 다음 페이지 요청에는 마지막 응답의 `next_cursor`를 그대로 `beforeBidId`로 넣는다.
- 현재가 max 비교: 이미 반영됨(2026-10-18, 미커밋).

### 차트

- 처음 최근 20건. 가로축은 입찰 순서대로 같은 간격(시간 비례 아님).
- 점 라벨·툴팁 시각 형식: `10.18 14:32`(월.일 시:분, 연도 생략).
- 시작가(`item.start_price`)를 가로 점선 기준선으로 표시.
- 잡고 끌어서 과거로 이동, 왼쪽 끝 근처에 닿으면 이전 20건 자동 로드, `has_next=false`면 멈춤.
- 모바일: 가로 끌기 = 차트 이동, 세로 = 페이지 스크롤. 기존 `min-w-[600px]` 가로 스크롤 박스 제거.
- 새 알림은 오른쪽 끝에 추가하되, 사용자가 과거를 보고 있으면 화면을 강제로 옮기지 않는다(Claude 판단).
- recharts 2.15에는 끌어서 이동이 없다. 직접 구현할지 라이브러리를 바꿀지는 구현 시 결정한다.

### 목록

- 시각 형식 `10.18 14:32`.
- `#N` 번호와 제목의 "(N건)" 제거.
- 목록 끝까지 스크롤하면 같은 API로 이전 20건 추가 로드(차트와 같은 데이터를 공유).

## 배포

응답이 배열 → 객체로 바뀌어 기존 프론트와 호환되지 않는다. 옛 프론트가 객체를 받으면 `bids.map`에서 예외가 나고 ErrorBoundary가 없어 상세 페이지 전체가 흰 화면이 된다. 배포 전에 열어 둔 탭은 새로고침 전까지 옛 번들을 쓰므로 동시 배포로는 막을 수 없다.

그래서 세 단계로 배포한다.
1. [프론트] 배열·객체 응답을 모두 받는 버전을 먼저 push(배열이면 `has_next=false`, `next_cursor=null`로 취급). 알림의 `bid_id`가 없으면 기존 방식으로 처리.
2. [백엔드] 릴리스(develop → main, merge commit).
3. [프론트] 배열 호환 코드 제거(옛 번들 탭이 충분히 사라진 뒤).

## 남은 것 (이번 범위 밖)

- 운영 `bids` 인덱스 확인과 기준선 측정.
- 인기 상품 캐시 단계에서 공용 캐시 전략.
- `/me/items`·`/me/bids`·`/me/purchases`·채팅방 목록 페이징.
