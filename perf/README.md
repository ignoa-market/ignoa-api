# 성능 측정

## 시나리오

세 시나리오 모두 조회는 같다. 기본은 방문자가 초당 19명씩 들어와 약 200 RPS가 된다. 다른 건 입찰뿐이다. 조회 RPS를 넘기면 방문자 수만 그만큼 바꾼다(아래 "측정").

| 시나리오 | 입찰 | 시간 (1회) | 용도 |
|---|---|---|---|
| `browse` | 없음 | 6분 10초 | 조회 성능. 조회 RPS를 올려 조회 단독 한계 확인 |
| `browse-bid` | 상품 1개에 50 RPS | 6분 10초 | 운영 목표 부하. `browse`와 비교하면 입찰 영향 |
| `stress` | 상품 1개에 100 → 200 → 400 RPS | 12분 10초 | 한계 지점 |

방문자 한 명의 흐름 (평균 약 10.6건 요청):

1. 메인: 인기순, 마감 임박, 최신순 첫 페이지를 동시에
2. 최신순을 0~5페이지 스크롤
3. 10명 중 7명이 본 상품을 1~3개 누름 → 상세와 입찰 이력을 동시에. 5명 중 1명은 이력을 더 내림
4. 다음 상품을 보러 메인으로 돌아오면 메인 3종을 다시 요청
5. 행동 사이 1~3초 쉼

> 경매 자동 마감 측정은 [sql/auction-close/README.md](sql/auction-close/README.md).

## 실행

### 1. 데이터 준비 (처음 한 번)

1. `k6/k6.env.example`을 복사해 `k6/k6.env`를 만들고 값을 채운다 (커밋 안 됨).
2. 운영 DB에서 `sql/data/`의 SQL을 번호 순서대로 실행한다. `3-accounts.sql`은 실행 전에 `PASSWORD`로 만든 해시를 넣는다 (명령은 파일 주석에 있음).

| 파일 | 하는 일 |
|---|---|
| `1-reset.sql` | 서비스 데이터 전체 삭제 (outbox, shedlock 제외) |
| `2-seed.sql` | 사용자 1만, 상품 10만(진행 중 2만), 입찰 100만, 찜 약 30만 |
| `3-accounts.sql` | k6 계정 51개 (판매자 1, 입찰자 50) |

### 2. 접속 확인 (측정 전, 10초)

```bash
set -a; source perf/k6/k6.env; set +a
k6 run -e THINK_SCALE=0 perf/k6/tests/browse-smoke.js
```

`read_errors`, `page_errors`가 0%면 된다.

### 3. 측정

`ignoa-api` 폴더에서 실행한다.

```bash
# 기본: 워밍업 1회 + 측정 3회 (사이 2분 쉼). 세 시나리오 모두 같다.
./perf/k6/run.sh browse
./perf/k6/run.sh browse-bid
./perf/k6/run.sh stress

# 뒤에 once를 붙이면 1회만 (워밍업 없음). 세 시나리오 모두 된다.
./perf/k6/run.sh browse-bid once

# 시나리오 뒤에 조회 RPS를 넣으면 조회 부하만 바꾼다 (방문자 수 = RPS ÷ 10.6, 반올림)
./perf/k6/run.sh browse 500 once        # 조회만 약 500 RPS
./perf/k6/run.sh browse-bid 400 once   # 조회 약 400 RPS + 입찰 50 RPS

# 앞에 K6_WEB_DASHBOARD=true를 붙이면 실시간 그래프 (터미널에 뜨는 주소를 브라우저로 연다)
K6_WEB_DASHBOARD=true ./perf/k6/run.sh browse
```

- 결과: `results/k6/날짜/시나리오[-RPSrps][-once]-…/`에 회차별 요약 `.json`과 로그 `.log`

### 4. 정리

`browse-bid`, `stress`를 돌렸으면 `k6/cleanup.sql`을 실행한다. `browse`는 정리할 것이 없다.

1. 그대로 실행 → 지울 상품 목록만 나옴
2. `@apply_cleanup = 1`로 바꿔 다시 실행 → 삭제
3. `remaining_items = 0`이면 끝

## 특이사항

- **결과 볼 구간**: 로그의 `유지 구간 UTC` 시각만 본다. Grafana는 KST라 9시간을 더한다.
- **목표**: 조회 종류별 p95 150ms, p99 300ms 이하 / 입찰 p95 500ms 이하 / 오류 0건. `BID_CONFLICT`는 가격 경합에 따른 정상 거절이라 오류가 아니다.
- **오래된 상품**: 방문자는 목록에서 본 상품만 누른다. 그래서 오래된 상품을 읽는 경우는 측정되지 않는다.
- **DB CPU 크레딧**: RDS t4g는 크레딧이 바닥나도 느려지지 않고 초과분이 과금된다. 측정 중에는 CloudWatch `CPUCreditBalance`도 같이 본다.
- **k6 코드를 고친 뒤**: `node --test perf/k6/tests/*.test.mjs`. 가짜 서버로 돌아서 실제 서버에는 요청하지 않는다.
