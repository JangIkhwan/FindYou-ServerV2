ALTER TABLE sync_job
    ADD COLUMN last_heartbeat_at DATETIME(6) NULL COMMENT '작업자가 마지막으로 heartbeat를 기록한 시각',
    ADD COLUMN lease_until DATETIME(6) NULL COMMENT '이 시각까지 active job으로 간주',
    ADD COLUMN owner_id VARCHAR(255) NULL COMMENT '동기화 작업을 소유한 서버 인스턴스 ID';

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
