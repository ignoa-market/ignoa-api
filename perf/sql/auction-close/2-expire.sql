-- 측정 상품의 종료 시각을 바꿔 자동 마감을 시작합니다.
-- @spread_seconds = 0   : 전부 지금 만료 (동시 만료, 이번 측정에서 사용)
-- @spread_seconds = 600 : 10분에 걸쳐 고르게 만료 (시간차 만료)

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @run_id = 'PERF';
SET @spread_seconds = 0;

START TRANSACTION;

SET @expire_started_at = NOW(6);
SET @target_count = (
    SELECT COUNT(*) FROM items
    WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%') AND status = 'ACTIVE'
);

-- 상품 순서(n)대로 0초 ~ @spread_seconds 사이에 종료 시각을 나눠 줍니다.
UPDATE items i
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS n
    FROM items
    WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%') AND status = 'ACTIVE'
) t ON t.id = i.id
SET i.end_at = @expire_started_at
        + INTERVAL (t.n * @spread_seconds * 1000000 DIV GREATEST(@target_count, 1)) MICROSECOND
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
  AND i.status = 'ACTIVE';

COMMIT;

-- 이 시각을 측정 시작으로 기록합니다. expired_items가 target_items와 같아야 합니다.
-- ROW_COUNT()는 IntelliJ에서 0으로 보일 수 있어, 바뀐 종료 시각으로 직접 셉니다(마감돼도 end_at은 그대로).
SELECT
    @run_id AS run_id,
    @expire_started_at AS expire_started_at,
    @expire_started_at + INTERVAL @spread_seconds SECOND AS last_end_at,
    @target_count AS target_items,
    (SELECT COUNT(*) FROM items
      WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
        AND end_at BETWEEN @expire_started_at AND @expire_started_at + INTERVAL @spread_seconds SECOND) AS expired_items;

