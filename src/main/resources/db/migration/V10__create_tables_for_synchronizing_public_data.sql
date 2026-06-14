-- =========================================================
-- 1. 동기화 Job 메타데이터 테이블
-- =========================================================
CREATE TABLE sync_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '동기화 작업 ID',

    job_type VARCHAR(100) NOT NULL COMMENT '작업 유형. 예를 들어 ANIMAL_PUBLIC_SYNC',
    status VARCHAR(30) NOT NULL COMMENT 'RUNNING, STAGING_COMPLETED, MERGING, SUCCESS, FAILED, VALIDATION_FAILED',

    total_expected_count INT NULL COMMENT '외부 API 기준 예상 전체 건수',
    total_staged_count INT NOT NULL DEFAULT 0 COMMENT '스테이징 적재 성공 건수',
    total_merged_count INT NOT NULL DEFAULT 0 COMMENT '본 테이블 반영 건수',
    failed_batch_no INT NOT NULL DEFAULT 0 COMMENT '실패한 배치 번호',

    started_at DATETIME(6) NOT NULL COMMENT '작업 시작 시각',
    finished_at DATETIME(6) NULL COMMENT '작업 종료 시각',

    error_message VARCHAR(1000) NULL COMMENT '전체 작업 실패 사유',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',

    PRIMARY KEY (id)
);

-- =========================================================
-- 2. 배치 단위 실행 결과 메타데이터 테이블
-- =========================================================
CREATE TABLE sync_job_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '배치 실행 ID',

    sync_job_id BIGINT NOT NULL COMMENT '상위 동기화 작업 ID',
    batch_no INT NOT NULL COMMENT '배치 순번',
    request_page_no INT NULL COMMENT '외부 API 요청 페이지 번호',

    requested_count INT NOT NULL DEFAULT 0 COMMENT '외부 API에서 요청한 건수',
    staged_count INT NOT NULL DEFAULT 0 COMMENT '스테이징에 실제 저장 성공한 건수',

    status VARCHAR(30) NOT NULL COMMENT 'SUCCESS, FAILED',

    error_message VARCHAR(1000) NULL COMMENT '배치 실패 사유',

    started_at DATETIME(6) NOT NULL COMMENT '배치 시작 시각',
    finished_at DATETIME(6) NULL COMMENT '배치 종료 시각',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',

    PRIMARY KEY (id),
    CONSTRAINT fk_sync_job_batch_job FOREIGN KEY (sync_job_id) REFERENCES sync_job(id),
    CONSTRAINT uk_sync_job_batch_job_batch_no UNIQUE (sync_job_id, batch_no)
);

-- =========================================================
-- 3. 공공데이터 스테이징 테이블
-- =========================================================
CREATE TABLE public_animal_staging (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '스테이징 레코드 ID',

    sync_job_id BIGINT NOT NULL COMMENT '동기화 작업 ID',
    batch_no INT NOT NULL COMMENT '배치 번호',

    -- =========================
    -- 공공데이터 식별
    -- =========================
    notice_number VARCHAR(30) NOT NULL COMMENT '공고번호',

    -- =========================
    -- Report 공통 필드
    -- =========================
    species VARCHAR(100) NULL COMMENT '축종',
    breed VARCHAR(20) NULL COMMENT '품종',

    happen_date DATE NULL COMMENT '발견일 → Report.date',
    address VARCHAR(200) NULL COMMENT '보호소 주소 → Report.address',

    latitude DECIMAL(9,6) NULL COMMENT '위도',
    longitude DECIMAL(9,6) NULL COMMENT '경도',

    -- =========================
    -- ProtectingReport 필드
    -- =========================
    sex CHAR(1) NULL COMMENT '성별 (M/F)',
    neutering CHAR(1) NULL COMMENT '중성화 여부 (Y/N)',

    age VARCHAR(10) NULL COMMENT '나이',
    weight VARCHAR(10) NULL COMMENT '체중',
    fur_color VARCHAR(100) NULL COMMENT '털 색상',

    significant VARCHAR(255) NULL COMMENT '특징',
    found_location VARCHAR(255) NULL COMMENT '발견 장소',

    notice_start_date DATE NULL COMMENT '공고 시작일',
    notice_end_date DATE NULL COMMENT '공고 종료일',

    care_name VARCHAR(50) NULL COMMENT '보호소명',
    care_tel VARCHAR(14) NULL COMMENT '보호소 전화번호',

    authority VARCHAR(50) NULL COMMENT '관할 기관',

    image_url1 VARCHAR(2083) NULL COMMENT '공고이미지1',
    image_url2 VARCHAR(2083) NULL COMMENT '공고이미지2',

    -- =========================
    -- 원본데이터와 메타데이터
    -- =========================
    raw_data JSON NOT NULL COMMENT '공공 API 원본 JSON',
    raw_hash CHAR(64) NOT NULL COMMENT '변경 감지용 해시',
    staged_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_public_animal_staging_job FOREIGN KEY (sync_job_id) REFERENCES sync_job(id),
    CONSTRAINT uk_public_animal_staging_job_public UNIQUE (sync_job_id, notice_number)
);

ALTER TABLE protecting_reports ADD CONSTRAINT uq_notice_number UNIQUE (notice_number);
