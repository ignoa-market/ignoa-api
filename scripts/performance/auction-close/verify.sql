-- 문자열 비교 기준을 기존 테이블의 Collation과 맞춥니다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- seed.sql과 동일한 실행 ID를 사용합니다.
SET @run_id = '20260902-01';

-- 상품 상태: BID_CLOSED 1,000건을 기대합니다.
SELECT status, COUNT(*) AS item_count
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
GROUP BY status
ORDER BY status;

-- 입찰 상태: WON 1,000건과 LOST 9,000건을 기대합니다.
SELECT b.status, COUNT(*) AS bid_count
FROM bids b
JOIN items i ON i.id = b.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
GROUP BY b.status
ORDER BY b.status;

-- 상품마다 입찰 10건, WON 1건, LOST 9건이 있고 낙찰자가 최고가 입찰자와 일치하는지 확인합니다.
SELECT COUNT(*) AS invalid_bid_result_items
FROM (
    SELECT i.id
    FROM items i
    LEFT JOIN bids b ON b.item_id = i.id
    WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
    GROUP BY i.id
    HAVING COUNT(b.id) <> 10
        OR COALESCE(SUM(b.status = 'WON'), 0) <> 1
        OR COALESCE(SUM(b.status = 'LOST'), 0) <> 9
        OR MAX(CASE WHEN b.status = 'WON' THEN b.bidder_id END)
            <> MAX(i.highest_bidder_id)
) invalid_items;

-- 채팅방 1,000개를 기대합니다.
SELECT COUNT(*) AS chat_room_count
FROM chat_rooms cr
JOIN items i ON i.id = cr.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

-- 한눈에 확인할 최종 요약
SELECT
    COUNT(*) AS total_items,
    SUM(status = 'BID_CLOSED') AS closed_items,
    SUM(status = 'ACTIVE') AS remaining_active_items,
    SUM(status NOT IN ('BID_CLOSED', 'ACTIVE')) AS unexpected_status_items
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');
