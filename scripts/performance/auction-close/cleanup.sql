-- 문자열 비교 기준을 기존 테이블의 Collation과 맞춥니다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- seed.sql과 동일한 실행 ID를 사용합니다.
SET @run_id = '20260902-01';

START TRANSACTION;

-- FK 참조 순서에 맞춰 자식 데이터부터 삭제합니다.
DELETE cr
FROM chat_rooms cr
JOIN items i ON i.id = cr.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE b
FROM bids b
JOIN items i ON i.id = b.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE FROM users
WHERE email = CONCAT('perf-seller-', @run_id, '@test.local')
   OR email = CONCAT('perf-bidder-', @run_id, '@test.local')
   OR email LIKE CONCAT('perf-bidder-', @run_id, '-%@test.local');

COMMIT;

-- 모두 0건이면 정리가 끝난 것입니다.
SELECT COUNT(*) AS remaining_items
FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

SELECT COUNT(*) AS remaining_users
FROM users
WHERE email = CONCAT('perf-seller-', @run_id, '@test.local')
   OR email = CONCAT('perf-bidder-', @run_id, '@test.local')
   OR email LIKE CONCAT('perf-bidder-', @run_id, '-%@test.local');
