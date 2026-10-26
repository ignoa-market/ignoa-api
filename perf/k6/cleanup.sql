-- k6 부하 테스트 데이터 수동 정리용. 테스트 실행 중에는 실행하지 않는다.
-- 기본값 0: 대상 조회만. 대상 확인 후 @apply_cleanup을 1로 바꾸고 전체 실행한다.
-- 테스트 판매자는 현재 k6.env의 SELLER_EMAIL을 반영했다.
-- 거래·채팅·입찰·찜·미디어·상품을 삭제하며 테스트 계정은 유지한다.
-- 파일은 Outbox로 삭제한다. 완료 여부는 맨 아래 조회로 확인한다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @seller_email = 'k6-seller@ignoa.perf' COLLATE utf8mb4_unicode_ci;
SET @apply_cleanup = 0;

DROP TEMPORARY TABLE IF EXISTS k6_legacy_candidates;
CREATE TEMPORARY TABLE k6_legacy_candidates (id BIGINT PRIMARY KEY);
INSERT INTO k6_legacy_candidates
SELECT i.id FROM items i JOIN users u ON u.id = i.seller_id
WHERE u.email = @seller_email COLLATE utf8mb4_unicode_ci
  AND i.brand = 'k6'
  AND i.description = 'Automatically created for a k6 load test.'
  AND i.title REGEXP '^k6 load test [0-9]+$';

SELECT @apply_cleanup AS apply_cleanup, COUNT(*) AS target_items FROM k6_legacy_candidates;
SELECT i.id, i.title, i.status, i.created_at
FROM items i JOIN k6_legacy_candidates c ON c.id = i.id ORDER BY i.id;

DROP TEMPORARY TABLE IF EXISTS k6_cleanup_ids;
CREATE TEMPORARY TABLE k6_cleanup_ids (id BIGINT PRIMARY KEY);
INSERT INTO k6_cleanup_ids SELECT id FROM k6_legacy_candidates WHERE @apply_cleanup = 1;

-- k6_cleanup_ids에는 이번 실행에서 검증된 상품 ID만 들어 있어야 한다.
-- 트랜잭션 오류 시 중단하는 클라이언트에서 실행한다(mysql --force 금지).
SET SESSION innodb_lock_wait_timeout = 5;
START TRANSACTION;

-- 갱신·마감 등과의 경합을 피하기 위해 먼저 상품 행을 잠근다.
SELECT i.id FROM items i JOIN k6_cleanup_ids c ON c.id = i.id ORDER BY i.id FOR UPDATE;

-- 파일은 앱의 기존 Outbox 작업으로 삭제한다. 기존 삭제 이벤트는 중복 생성하지 않는다.
INSERT INTO outbox (aggregate_id, aggregate_type, event_type, payload, status, retry_count, created_at, updated_at)
SELECT CAST(m.item_id AS CHAR), 'ITEM', 'DELETE_ITEM_MEDIA', m.object_key, 'PENDING', 0, NOW(6), NOW(6)
FROM item_media m JOIN k6_cleanup_ids c ON c.id = m.item_id
WHERE NOT EXISTS (
    SELECT 1 FROM outbox o
    WHERE o.aggregate_type = 'ITEM' AND o.aggregate_id = CAST(m.item_id AS CHAR)
      AND o.event_type = 'DELETE_ITEM_MEDIA' AND o.payload = m.object_key
);

-- 채팅방 ↔ 마지막 메시지의 상호 FK를 먼저 해제한다.
UPDATE chat_rooms r JOIN k6_cleanup_ids c ON c.id = r.item_id
SET r.last_message_id = NULL
WHERE r.item_id = c.id;
DELETE m FROM chat_messages m JOIN chat_rooms r ON r.id = m.chat_room_id
JOIN k6_cleanup_ids c ON c.id = r.item_id
WHERE r.item_id = c.id;
DELETE r FROM chat_rooms r JOIN k6_cleanup_ids c ON c.id = r.item_id
WHERE r.item_id = c.id;
DELETE t FROM trades t JOIN k6_cleanup_ids c ON c.id = t.item_id
WHERE t.item_id = c.id;
DELETE b FROM bids b JOIN k6_cleanup_ids c ON c.id = b.item_id
WHERE b.item_id = c.id;
DELETE w FROM wishes w JOIN k6_cleanup_ids c ON c.id = w.item_id
WHERE w.item_id = c.id;
DELETE m FROM item_media m JOIN k6_cleanup_ids c ON c.id = m.item_id
WHERE m.item_id = c.id;
DELETE i FROM items i JOIN k6_cleanup_ids c ON c.id = i.id
WHERE i.id = c.id;
COMMIT;


SELECT COUNT(*) AS remaining_items
FROM items i JOIN k6_legacy_candidates c ON c.id = i.id;
SELECT o.status, COUNT(*) AS file_deletion_events
FROM outbox o JOIN k6_legacy_candidates c ON o.aggregate_id = CAST(c.id AS CHAR)
WHERE o.aggregate_type = 'ITEM' AND o.event_type = 'DELETE_ITEM_MEDIA'
GROUP BY o.status;
-- PENDING: 파일 삭제 대기, DONE: 파일 삭제 완료, DEAD: 수동 확인 필요.
-- PERF-AUCTION-* 등 별도 테스트 데이터와 일반 상품·사용자 계정은 대상에서 제외한다.
