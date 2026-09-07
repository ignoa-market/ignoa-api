-- ShedLock 잠금 저장소 테이블
-- JPA 엔티티가 아니므로 ddl-auto로 생성되지 않는다. 새 환경마다 직접 실행한다.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until DATETIME(3)  NOT NULL,
    locked_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
