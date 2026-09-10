-- 문자열 비교 기준을 기존 테이블의 Collation과 맞춥니다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- seed.sql과 동일한 실행 ID를 사용합니다.
SET @run_id = '20260902-01';

-- 이 UPDATE가 Commit되면 다음 Scheduler 실행에서 마감 처리가 시작됩니다.
START TRANSACTION;

SET @made_expired_at = NOW(6);

UPDATE items
SET end_at = NOW() - INTERVAL 1 SECOND
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')
  AND status = 'ACTIVE';

SET @expired_item_count = ROW_COUNT();

COMMIT;

SELECT
    @run_id AS run_id,
    @made_expired_at AS made_expired_at,
    @expired_item_count AS expired_item_count;
