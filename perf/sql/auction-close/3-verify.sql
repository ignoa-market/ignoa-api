-- 마감 결과의 정합성과, DB 기록으로 계산한 마감 지연을 확인합니다.
-- 마감 트랜잭션 안에서 만든 거래(trades.created_at)를 마감 완료 시각으로 봅니다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @run_id = 'PERF';
SET @target_seconds = 10;  -- 마감 확정 목표(초)

-- 1. 요약: 기대값은 closed = total, remaining_active = 0, unexpected = 0
SELECT
    COUNT(*) AS total_items,
    SUM(status = 'AUCTION_AWARDED') AS closed_items,
    SUM(status = 'ACTIVE') AS remaining_active_items,
    SUM(status NOT IN ('AUCTION_AWARDED', 'ACTIVE')) AS unexpected_status_items
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

-- 2. 상품별 정합성: 모두 0건이어야 합니다.
--    입찰 10건(WON 1, LOST 9), 낙찰자 = 최고가 입찰자, 채팅방 1개, 거래 1건(결제 대기, 11,000원, 구매자 = 낙찰자)
SELECT COUNT(*) AS invalid_items
FROM items i
LEFT JOIN (
    SELECT item_id,
           COUNT(*) AS bid_count,
           SUM(status = 'WON') AS won,
           SUM(status = 'LOST') AS lost,
           MAX(CASE WHEN status = 'WON' THEN bidder_id END) AS winner_id
    FROM bids GROUP BY item_id
) b ON b.item_id = i.id
LEFT JOIN (SELECT item_id, COUNT(*) AS room_count FROM chat_rooms GROUP BY item_id) cr ON cr.item_id = i.id
LEFT JOIN (
    SELECT item_id,
           COUNT(*) AS trade_count,
           MAX(buyer_id) AS buyer_id,
           MAX(amount) AS amount,
           MAX(status) AS status
    FROM trades GROUP BY item_id
) t ON t.item_id = i.id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
  AND (
        COALESCE(b.bid_count, 0) <> 10
     OR COALESCE(b.won, 0) <> 1
     OR COALESCE(b.lost, 0) <> 9
     OR b.winner_id <> i.highest_bidder_id
     OR COALESCE(cr.room_count, 0) <> 1
     OR COALESCE(t.trade_count, 0) <> 1
     OR t.buyer_id <> i.highest_bidder_id
     OR t.amount <> i.current_price
     OR t.status <> 'PAYMENT_PENDING'
  );

-- 3. 마감 지연 (DB 기준, 초): 종료 시각 → 거래 생성 시각
--    Grafana의 마감 지연과 비교하는 교차 확인용입니다.
WITH close_lag AS (
    SELECT TIMESTAMPDIFF(MICROSECOND, i.end_at, t.created_at) / 1000000 AS lag_seconds,
           ROW_NUMBER() OVER (ORDER BY TIMESTAMPDIFF(MICROSECOND, i.end_at, t.created_at)) AS rn,
           COUNT(*) OVER () AS cnt
    FROM items i
    JOIN trades t ON t.item_id = i.id
    WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
)
SELECT
    MAX(cnt) AS closed_items,
    ROUND(MIN(lag_seconds), 3) AS min_s,
    ROUND(MAX(CASE WHEN rn = CEIL(cnt * 0.50) THEN lag_seconds END), 3) AS p50_s,
    ROUND(MAX(CASE WHEN rn = CEIL(cnt * 0.95) THEN lag_seconds END), 3) AS p95_s,
    ROUND(MAX(CASE WHEN rn = CEIL(cnt * 0.99) THEN lag_seconds END), 3) AS p99_s,
    ROUND(MAX(lag_seconds), 3) AS max_s,
    SUM(lag_seconds <= @target_seconds) / MAX(cnt) AS within_target_ratio
FROM close_lag;

-- 4. 전체 처리 구간: 첫 마감 ~ 마지막 마감 (기존 마감 단독 측정 평균 4.60초와 비교)
SELECT
    MIN(t.created_at) AS first_closed_at,
    MAX(t.created_at) AS last_closed_at,
    ROUND(TIMESTAMPDIFF(MICROSECOND, MIN(t.created_at), MAX(t.created_at)) / 1000000, 3) AS close_span_s
FROM items i
JOIN trades t ON t.item_id = i.id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');
