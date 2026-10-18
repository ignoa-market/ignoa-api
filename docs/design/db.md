# DB

## users

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 사용자 고유 ID |
| email | VARCHAR | NULL, UNIQUE | 이메일 |
| password | VARCHAR | NULL | 비밀번호 (OAuth 사용자는 NULL) |
| profile_image_source | VARCHAR | NULL | 프로필 이미지 출처 (MANAGED, EXTERNAL) |
| profile_image_reference | VARCHAR | NULL | 프로필 이미지 참조값 (S3 Object Key or 외부 URL) |
| nickname | VARCHAR | NOT NULL, UNIQUE | 닉네임 |
| address | VARCHAR | NULL | 주소 |
| bio | VARCHAR(100) | NULL | 자기소개 (최대 100자) |
| provider | VARCHAR | NOT NULL | 가입 경로 (LOCAL, KAKAO) |
| oauth_id | VARCHAR | NULL | 소셜 로그인 제공자 고유 ID |
| | | UNIQUE(provider, oauth_id) | 소셜 로그인 중복 가입 방지 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 가입 일시 |
| updated_at | DATETIME | NULL | 수정 일시 |
| deleted_at | DATETIME | NULL | 탈퇴 일시 (NULL이면 활성 계정, 파기 후에도 유지) |
| purged_at | DATETIME | NULL | 개인정보 파기 일시 (NULL이면 미파기) |

## items

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 상품 고유 ID |
| seller_id | BIGINT | FK(User.id), NOT NULL | 판매자 ID |
| highest_bidder_id | BIGINT | FK(User.id), NULL | 최고 입찰자 ID (경매 종료 시 확정) |
| title | VARCHAR | NOT NULL | 상품 제목 |
| description | TEXT | NOT NULL | 상품 설명 |
| status | VARCHAR | NOT NULL | 판매 상태 (ACTIVE, BUY_NOW_PENDING, BID_CLOSED, NO_BID_CLOSED, BUY_NOW_CLOSED, DELETED) |
| item_condition | VARCHAR | NOT NULL | 상품 상태 (NEW, LIKE_NEW, GOOD, FAIR) |
| category | VARCHAR | NOT NULL | 카테고리 |
| brand | VARCHAR | NOT NULL | 브랜드명 |
| start_price | BIGINT | NOT NULL | 경매 시작가 |
| current_price | BIGINT | NOT NULL | 현재 최고 입찰가 (경매 종료 = 낙찰가) |
| buy_now_price | BIGINT | NOT NULL | 즉시 구매가 |
| extension_count | INT | NOT NULL | 마감 연장 횟수 (최대 3회) |
| version | BIGINT | NULL | 조건부 UPDATE 시 직접 증가시키는 버전 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 경매 시작 일시 (= 상품 등록 일시) |
| updated_at | DATETIME | NULL | 상품 수정 일시 |
| end_at | DATETIME | NOT NULL | 경매 종료 일시 |
| | | INDEX idx_items_status_created(status, created_at) | 최신순 목록 |
| | | INDEX idx_items_status_end_at(status, end_at) | 마감 임박순 목록, 자동 마감 대상 조회 |

## item_media

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 이미지 고유 ID |
| item_id | BIGINT | FK(Item.id), NOT NULL | 상품 고유 ID |
| object_key | VARCHAR | NOT NULL | S3 객체 식별자 |
| media_type | VARCHAR | NOT NULL | IMAGE, VIDEO |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 업로드 일시 |
| updated_at | DATETIME | NULL | - |

## bids

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 입찰 고유 번호 |
| item_id | BIGINT | FK(Item.id), NOT NULL | 어느 상품에 대한 입찰인지 |
| bidder_id | BIGINT | FK(User.id), NOT NULL | 입찰자 ID |
| price | BIGINT | NOT NULL | 입찰 금액 |
| status | VARCHAR | NOT NULL | 입찰 상태 (ACTIVE, WON, LOST) |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 입찰 시간 |
| updated_at | DATETIME | NULL | - |

## wishes

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 찜 고유 ID |
| user_id | BIGINT | FK(User.id), NOT NULL | 사용자 고유 ID |
| item_id | BIGINT | FK(Item.id), NOT NULL | 상품 고유 ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 찜한 시간 |
| updated_at | DATETIME | NULL | - |
| | | UNIQUE(user_id, item_id) | 중복 찜 방지 |

## outbox

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| aggregate_id | VARCHAR | NOT NULL | 도메인 레코드 식별자 |
| aggregate_type | VARCHAR | NOT NULL | 도메인 구분 (Ex: "USER") |
| event_type | VARCHAR | NOT NULL | 워커 분기 기준 (DELETE_PROFILE_IMAGE, DELETE_ITEM_MEDIA) |
| payload | TEXT | NOT NULL | JSON 처리 데이터 |
| status | VARCHAR | NOT NULL | PENDING, DONE, DEAD |
| retry_count | INT | NOT NULL | 재시도 횟수 |
| processed_at | DATETIME | NULL | 처리 완료 시각 |

## shedlock

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| name | VARCHAR | PK | 잠금을 구분하는 Scheduler 이름 |
| lock_until | DATETIME | NOT NULL | 다른 인스턴스가 잠금을 획득할 수 없는 시각 |
| locked_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP(3) | 현재 잠금을 획득한 시각 |
| locked_by | VARCHAR | NOT NULL | 잠금을 획득한 인스턴스 식별자 |

## chat_rooms

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 채팅방 고유 ID |
| item_id | BIGINT | FK(Item.id), NOT NULL | 대상 상품 ID |
| seller_id | BIGINT | FK(User.id), NOT NULL | 판매자 ID |
| buyer_id | BIGINT | FK(User.id), NOT NULL | 구매자 ID |
| last_message_id | BIGINT | FK(ChatMessage.id), NULL | 마지막 메시지 ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 채팅방 생성 시간 |
| updated_at | DATETIME | NULL | - |
| | | UNIQUE(item_id, buyer_id) | 중복 채팅방 방지 |

## chat_messages

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 메시지 고유 ID |
| chat_room_id | BIGINT | FK(ChatRoom.id), NOT NULL | 채팅방 ID |
| sender_id | BIGINT | FK(User.id), NOT NULL | 보낸 사람 ID |
| content | VARCHAR | NOT NULL | 메시지 내용 (1000자) |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 보낸 시간 |
| updated_at | DATETIME | NULL | - |

## trades

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 거래 ID (결제 서버로 보내는 trade_id) |
| item_id | BIGINT | FK(item.id), NOT NULL | 상품 |
| buyer_id | BIGINT | FK(User.id), NOT NULL | 구매자 |
| seller_id | BIGINT | FK(User.id), NOT NULL | 판매자 |
| amount | BIGINT | NOT NULL | 결제 금액 |
| type | VARCHAR | NOT NULL | 낙찰(AUCTION) / 즉시구매(BUY_NOW) |
| status | VARCHAR | NOT NULL | PAYMENT_PENDING, CONFIRMING, PAID, CANCELED |
| confirming_order_id | VARCHAR | NULL | 지금 승인 중인 order_id (계약, 필수) |
| payment_deadline | DATETIME | NOT NULL | 결제 기한 (낙찰 24시간, 즉시구매 30분) |
| paid_at | DATETIME | NULL | PAID가 된 시각 |
| confirm_started_at | DATETIME | NULL | 승인 시작 시각 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | DATETIME | NULL | 수정 일시 |
| | | INDEX idx_trades_status_payment_deadline(status, payment_deadline) | 기한 지난 미결제 거래 조회 |

## payment (ignoa-payment)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 결제 시도 ID |
| order_id | VARCHAR(64) | NOT NULL, UNIQUE | IGN-… (결제 서버가 발급) |
| trade_id | BIGINT | NOT NULL | api의 trade.id (FK 아님, 서버가 다름) |
| payment_key | VARCHAR(200) | NULL, UNIQUE | Toss 결제 키 (승인 시 채움) |
| amount | BIGINT | NOT NULL | 준비 때 받은 금액 |
| order_name | VARCHAR | NOT NULL | 상품명 |
| status | VARCHAR | NOT NULL | READY, CONFIRMING, DONE, FAILED, CANCELED |
| paid_trade_id | BIGINT | NULL, UNIQUE | DONE일 때만 채움, trade당 성공 1건 보장 |
| failure_code | VARCHAR | NULL | 실패 사유 |
| failure_message | VARCHAR | NULL | 실패 사유 |
| approved_at | DATETIME | NULL | 승인 시각 |
| confirm_requested_at | DATETIME | NULL | 승인 시작 시각 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | DATETIME | NULL | 수정 시각 |
| | | INDEX idx_payment_trade_id(trade_id) | 거래별 결제 시도 조회 |
| | | INDEX idx_payment_status_confirm_requested_at(status, confirm_requested_at) | 미확정 결제 재조회 |
