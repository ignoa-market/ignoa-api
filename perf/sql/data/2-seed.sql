-- 읽기 성능 측정용 대량 데이터 (운영 ignoa_api)
-- 규모: 사용자 1만 / 상품 10만(ACTIVE 20%) / 이미지 약 20만 / 입찰 100만(인기 상품 쏠림) / 찜 약 30만
-- RAND(고정 시드)를 써서 같은 순서로 실행하면 같은 데이터가 만들어진다.
-- 실행 전 1-reset.sql로 비운다(사용자·상품 ID를 1부터 직접 넣는다). 실행 후 마지막 확인까지 돌린다.

USE ignoa_api;

-- 0. 숫자 테이블 (1 ~ 1,000,000)
DROP TABLE IF EXISTS perf_seq;
CREATE TABLE perf_seq (n INT PRIMARY KEY);

INSERT INTO perf_seq (n)
SELECT d0.d + d1.d * 10 + d2.d * 100 + d3.d * 1000 + d4.d * 10000 + d5.d * 100000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5;

-- 1. 사용자 1만 (로그인 불가: password NULL)
INSERT INTO users (id, created_at, updated_at, email, nickname, provider)
SELECT n,
       NOW(6) - INTERVAL (10001 - n) MINUTE,
       NOW(6) - INTERVAL (10001 - n) MINUTE,
       CONCAT('perf-', n, '@ignoa.perf'),
       CONCAT('perf', n),
       'LOCAL'
FROM perf_seq
WHERE n <= 10000;

-- 2. 상품 10만
--    상태: id % 5 = 0 → ACTIVE(2만, 마감 30~37일 뒤), 나머지는 마감된 상품(등록 7일 뒤 마감, 모두 과거)
--    등록 시각: id 순서대로 약 387일 전 → 40일 전 (마감 상품의 마감 시각이 과거가 되도록)
INSERT INTO items (id, created_at, updated_at, seller_id, title, description, category, item_condition,
                   start_price, current_price, buy_now_price, brand, status, end_at, extension_count,
                   bidder_count, wish_count)
SELECT n,
       NOW(6) - INTERVAL 40 DAY - INTERVAL (100001 - n) * 5 MINUTE,
       NOW(6) - INTERVAL 40 DAY - INTERVAL (100001 - n) * 5 MINUTE,
       1 + (n * 37) % 10000,
       CONCAT('perf 상품 ', n),
       '읽기 성능 측정용 상품입니다.',
       ELT(1 + n % 5, '가방', '시계', '지갑', '신발', '의류'),
       ELT(1 + n % 4, 'NEW', 'LIKE_NEW', 'GOOD', 'FAIR'),
       10000 + (n % 100) * 1000,
       10000 + (n % 100) * 1000,
       1000000 + (n % 100) * 10000,
       ELT(1 + n % 6, '샤넬', '루이비통', '구찌', '프라다', '에르메스', '롤렉스'),
       CASE
           WHEN n % 5 = 0 THEN 'ACTIVE'
           WHEN n % 5 = 1 THEN 'AUCTION_AWARDED'
           WHEN n % 5 = 2 THEN 'AUCTION_UNSOLD'
           WHEN n % 50 = 3 THEN 'DELETED'
           ELSE 'BUY_NOW_SOLD'
       END,
       CASE
           WHEN n % 5 = 0 THEN NOW(6) + INTERVAL 30 DAY + INTERVAL (n % 10080) MINUTE
           ELSE NOW(6) - INTERVAL 40 DAY - INTERVAL (100001 - n) * 5 MINUTE + INTERVAL 7 DAY
       END,
       0,
       0,
       0
FROM perf_seq
WHERE n <= 100000;

-- 3. 상품 이미지 (상품당 1~3장, 전부 IMAGE)
INSERT INTO item_media (created_at, updated_at, item_id, object_key, media_type)
SELECT i.created_at, i.created_at, i.id, CONCAT('perf/items/', i.id, '/', k.k, '.jpg'), 'IMAGE'
FROM items i
JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3) k ON k.k <= 1 + i.id % 3;

-- 4. 입찰 100만 (인기 상품 쏠림)
--    rnk = 100000 * RAND()^3 → 앞 순위일수록 입찰이 몰린다
--    (rnk * 7919) % 100000 으로 순위를 상품 ID에 흩어서 인기 상품이 특정 ID 구간에 몰리지 않게 한다
--    입찰 상태: ACTIVE 상품은 ACTIVE, 마감 상품은 LOST (조회 측정 대상이 아니라 WON은 생략)
--    RAND 결과를 먼저 테이블에 저장한다. 서브쿼리로 바로 JOIN하면 옵티마이저가 서브쿼리를 풀어
--    PK 조회 대신 해시 조인(상품 10만 × 숫자 100만)을 골라 끝나지 않는다.
DROP TABLE IF EXISTS perf_bid;
CREATE TABLE perf_bid (n INT PRIMARY KEY, item_id BIGINT NOT NULL, bidder_id BIGINT NOT NULL);

INSERT INTO perf_bid (n, item_id, bidder_id)
SELECT n,
       1 + (FLOOR(100000 * POW(RAND(42), 3)) * 7919) % 100000,
       1 + FLOOR(10000 * RAND(7))
FROM perf_seq;

INSERT INTO bids (created_at, updated_at, item_id, bidder_id, price, status)
SELECT NOW(6) - INTERVAL (1000001 - b.n) * 30 SECOND,
       NOW(6) - INTERVAL (1000001 - b.n) * 30 SECOND,
       b.item_id,
       b.bidder_id,
       i.start_price + b.n,
       IF(i.status = 'ACTIVE', 'ACTIVE', 'LOST')
FROM perf_bid b
JOIN items i ON i.id = b.item_id
ORDER BY b.n;

DROP TABLE perf_bid;

-- 5. 찜 약 30만 (같은 쏠림, 사용자·상품 중복은 INSERT IGNORE로 버림 → 30만보다 조금 적다)
INSERT IGNORE INTO wishes (created_at, updated_at, user_id, item_id)
SELECT NOW(6) - INTERVAL (300001 - n) MINUTE,
       NOW(6) - INTERVAL (300001 - n) MINUTE,
       1 + FLOOR(10000 * RAND(11)),
       1 + (FLOOR(100000 * POW(RAND(13), 3)) * 7919) % 100000
FROM perf_seq
WHERE n <= 300000;

-- 6. 현재가·최고 입찰자를 입찰 결과에 맞춤 (ACTIVE 상품만)
UPDATE items i
JOIN (SELECT item_id, MAX(price) AS max_price
      FROM bids
      GROUP BY item_id) m ON m.item_id = i.id
SET i.current_price = m.max_price
WHERE i.status = 'ACTIVE';

UPDATE items i
JOIN bids b ON b.item_id = i.id AND b.price = i.current_price
SET i.highest_bidder_id = b.bidder_id
WHERE i.status = 'ACTIVE';

-- 인기순 정렬 칼럼(입찰자 수·찜 수)을 실제 개수로 맞춤
UPDATE items i
LEFT JOIN (SELECT item_id, COUNT(DISTINCT bidder_id) c FROM bids GROUP BY item_id) b ON b.item_id = i.id
LEFT JOIN (SELECT item_id, COUNT(*) c FROM wishes GROUP BY item_id) w ON w.item_id = i.id
SET i.bidder_count = COALESCE(b.c, 0),
    i.wish_count   = COALESCE(w.c, 0)
WHERE i.id > 0;

-- 7. 정리 및 통계 갱신
DROP TABLE perf_seq;
ANALYZE TABLE users, items, item_media, bids, wishes;

-- 8. 확인
SELECT 'users' t, COUNT(*) FROM users
UNION ALL SELECT 'items', COUNT(*) FROM items
UNION ALL SELECT 'items ACTIVE', COUNT(*) FROM items WHERE status = 'ACTIVE'
UNION ALL SELECT 'item_media', COUNT(*) FROM item_media
UNION ALL SELECT 'bids', COUNT(*) FROM bids
UNION ALL SELECT 'wishes', COUNT(*) FROM wishes;
