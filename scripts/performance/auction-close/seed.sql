-- 문자열 비교 기준을 기존 테이블의 Collation과 맞춥니다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 모든 성능 측정 SQL에서 동일한 실행 ID를 사용합니다.
SET @run_id = '20260902-01';

START TRANSACTION;

-- 판매자 1명 생성
INSERT INTO users (
    email,
    password,
    nickname,
    provider,
    created_at,
    updated_at
)
VALUES (
    CONCAT('perf-seller-', @run_id, '@test.local'),
    NULL,
    CONCAT('perf-seller-', @run_id),
    'LOCAL',
    NOW(),
    NOW()
);

SET @seller_id = LAST_INSERT_ID();

-- 입찰자 1명 생성
INSERT INTO users (
    email,
    password,
    nickname,
    provider,
    created_at,
    updated_at
)
VALUES (
    CONCAT('perf-bidder-', @run_id, '@test.local'),
    NULL,
    CONCAT('perf-bidder-', @run_id),
    'LOCAL',
    NOW(),
    NOW()
);

SET @bidder_id = LAST_INSERT_ID();

-- Scheduler가 데이터 준비 중 마감하지 않도록 종료 시각을 하루 뒤로 설정합니다.
INSERT INTO items (
    seller_id,
    highest_bidder_id,
    title,
    description,
    category,
    item_condition,
    start_price,
    current_price,
    buy_now_price,
    brand,
    status,
    end_at,
    extension_count,
    version,
    created_at,
    updated_at
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS number

    UNION ALL

    SELECT number + 1
    FROM sequence
    WHERE number < 1000
)
SELECT
    @seller_id,
    @bidder_id,
    CONCAT('PERF-AUCTION-', @run_id, '-', LPAD(number, 4, '0')),
    '경매 자동 마감 성능 측정용 상품',
    'PERFORMANCE_TEST',
    'GOOD',
    1000,
    2000,
    10000,
    'PERF',
    'ACTIVE',
    NOW() + INTERVAL 1 DAY,
    0,
    0,
    NOW(),
    NOW()
FROM sequence;

-- 상품마다 최고가 입찰 1건 생성
INSERT INTO bids (
    item_id,
    bidder_id,
    price,
    status,
    created_at,
    updated_at
)
SELECT
    id,
    @bidder_id,
    2000,
    'ACTIVE',
    NOW(),
    NOW()
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

COMMIT;

-- 생성 결과 확인
SELECT COUNT(*) AS item_count
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

SELECT COUNT(*) AS bid_count
FROM bids b
JOIN items i ON i.id = b.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');
