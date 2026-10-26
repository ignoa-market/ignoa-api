-- k6 전용 계정 51개 생성 (2-seed.sql 실행 후)
--   판매자: k6-seller@ignoa.perf / 입찰자: k6-bidder-1@ignoa.perf ~ k6-bidder-50@ignoa.perf
-- perf/k6/lib/config.js의 계정 상수와 같아야 한다.
-- 모두 같은 비밀번호이며 원문은 k6.env의 PASSWORD에 둔다.
-- 공개 저장소라 해시를 커밋하지 않는다. 실행 전에 아래 명령으로 만든 해시를 넣는다.
--   htpasswd -nbBC 10 "" '<PASSWORD>' | tr -d ':\n' | sed 's/^\$2y\$/$2a$/'

USE ignoa_api;

SET @password_hash = '<BCrypt 해시>';

INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES ('k6-seller@ignoa.perf', @password_hash, 'k6-seller', 'LOCAL', NOW(6), NOW(6));

INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 50
)
SELECT CONCAT('k6-bidder-', n, '@ignoa.perf'), @password_hash, CONCAT('k6-bidder-', n), 'LOCAL', NOW(6), NOW(6)
FROM seq;

-- 51이면 끝
SELECT COUNT(*) AS k6_accounts
FROM users
WHERE email LIKE 'k6-%@ignoa.perf'
  AND password IS NOT NULL;

