-- 서비스 데이터 전체 삭제 (2-seed.sql 실행 전 빈 상태 만들기)
-- outbox·shedlock은 남긴다. TRUNCATE는 ID를 1부터 다시 쓰므로 ignoa_payment의 기존 거래 ID와 겹치지 않게 DELETE를 쓴다.
-- WHERE는 IntelliJ의 Unsafe query 경고를 피하기 위한 것으로, 모든 행이 대상이다.

START TRANSACTION;

-- 채팅방과 메시지가 서로 참조하므로 연결을 먼저 끊는다.
UPDATE chat_rooms SET last_message_id = NULL WHERE last_message_id IS NOT NULL;
DELETE FROM chat_messages WHERE id > 0;
DELETE FROM chat_rooms WHERE id > 0;
DELETE FROM trades WHERE id > 0;
DELETE FROM bids WHERE id > 0;
DELETE FROM wishes WHERE id > 0;
DELETE FROM item_media WHERE id > 0;
DELETE FROM items WHERE id > 0;
DELETE FROM users WHERE id > 0;

COMMIT;

-- 모두 0이면 끝
SELECT
  (SELECT COUNT(*) FROM users) users,
  (SELECT COUNT(*) FROM items) items,
  (SELECT COUNT(*) FROM bids) bids,
  (SELECT COUNT(*) FROM trades) trades,
  (SELECT COUNT(*) FROM chat_rooms) chat_rooms;
