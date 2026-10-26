-- 경매 자동 마감 측정용 데이터 생성
-- 판매자 1명, 입찰자 10명, 상품 @item_count건(상품마다 입찰 10건, 최고가 11,000원 = 10번 입찰자)
-- 종료 시각은 하루 뒤로 두어, 2-expire.sql을 실행하기 전에는 마감되지 않게 합니다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 네 파일 모두 같은 run_id를 사용합니다. 매 회차 cleanup으로 지우므로 고정값을 씁니다.
SET @run_id = 'PERF';
SET @item_count = 1500;

-- 재귀 CTE 기본 한도(1,000)보다 많은 상품을 만들 수 있게 합니다.
SET SESSION cte_max_recursion_depth = 100000;

START TRANSACTION;

INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES (CONCAT('perf-seller-', @run_id, '@test.local'), NULL, CONCAT('perf-seller-', @run_id), 'LOCAL', NOW(6), NOW(6));

SET @seller_id = LAST_INSERT_ID();

INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10
)
SELECT
    CONCAT('perf-bidder-', @run_id, '-', LPAD(n, 2, '0'), '@test.local'),
    NULL,
    CONCAT('perf-bidder-', @run_id, '-', LPAD(n, 2, '0')),
    'LOCAL',
    NOW(6),
    NOW(6)
FROM seq;

SET @highest_bidder_id = (
    SELECT id FROM users WHERE email = CONCAT('perf-bidder-', @run_id, '-10@test.local')
);

INSERT INTO items (
    seller_id, highest_bidder_id, title, description, category, item_condition,
    start_price, current_price, buy_now_price, brand, status, end_at,
    extension_count, bidder_count, wish_count, created_at, updated_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @item_count
)
SELECT
    @seller_id,
    @highest_bidder_id,
    CONCAT('PERF-AUCTION-', @run_id, '-', LPAD(n, 5, '0')),
    '경매 자동 마감 측정용 상품',
    'PERFORMANCE_TEST',
    'GOOD',
    1000,
    11000,
    20000,
    'PERF',
    'ACTIVE',
    NOW(6) + INTERVAL 1 DAY,
    0,
    10,
    0,
    NOW(6),
    NOW(6)
FROM seq;

INSERT INTO bids (item_id, bidder_id, price, status, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10
)
SELECT i.id, u.id, 1000 + seq.n * 1000, 'ACTIVE', NOW(6), NOW(6)
FROM items i
CROSS JOIN seq
JOIN users u ON u.email = CONCAT('perf-bidder-', @run_id, '-', LPAD(seq.n, 2, '0'), '@test.local')
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

COMMIT;

-- 기대: items = @item_count, bids = @item_count × 10, bidders = 10
SELECT
    (SELECT COUNT(*) FROM items WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')) AS items,
    (SELECT COUNT(*) FROM bids b JOIN items i ON i.id = b.item_id
      WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')) AS bids,
    (SELECT COUNT(*) FROM users WHERE email LIKE CONCAT('perf-bidder-', @run_id, '-%@test.local')) AS bidders;
