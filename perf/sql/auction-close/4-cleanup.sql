-- 측정 데이터를 모두 삭제합니다. 거래의 결제 기한(24시간)이 지나기 전에 실행합니다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @run_id = 'PERF';

START TRANSACTION;

-- FK 순서: 채팅 메시지 → 채팅방 → 거래·입찰·찜·미디어 → 상품 → 사용자
DELETE cm FROM chat_messages cm
JOIN chat_rooms cr ON cr.id = cm.chat_room_id
JOIN items i ON i.id = cr.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE cr FROM chat_rooms cr
JOIN items i ON i.id = cr.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE t FROM trades t
JOIN items i ON i.id = t.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE b FROM bids b
JOIN items i ON i.id = b.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE w FROM wishes w
JOIN items i ON i.id = w.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE m FROM item_media m
JOIN items i ON i.id = m.item_id
WHERE i.title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE FROM items
WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%');

DELETE FROM users
WHERE email = CONCAT('perf-seller-', @run_id, '@test.local')
   OR email LIKE CONCAT('perf-bidder-', @run_id, '-__@test.local');

COMMIT;

-- 모두 0이면 정리가 끝난 것입니다.
SELECT
    (SELECT COUNT(*) FROM items WHERE title LIKE CONCAT('PERF-AUCTION-', @run_id, '-%')) AS remaining_items,
    (SELECT COUNT(*) FROM users
      WHERE email = CONCAT('perf-seller-', @run_id, '@test.local')
         OR email LIKE CONCAT('perf-bidder-', @run_id, '-__@test.local')) AS remaining_users;
