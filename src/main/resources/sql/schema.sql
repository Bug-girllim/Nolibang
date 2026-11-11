-- 놀이방 LMS · schema.sql (lms-practice 적재용)
-- 원본: 데이터베이스/스키마.md 의 01(정본) + 02(추가분) + 05(HTML 패치) — 정본·추가분은 원본 그대로
-- 11-06 팀 결정 반영 (원본과 다른 부분):
--   ① 약관·만 14세 확인 미구현 → 05 H3 terms_agreements 표 제외
--   ② 피드백 마감 임박 알림 제외 → 05 H1 알림 종류에서 REVIEW_DUE_SOON 제외 (13종)
--   ④ 로그인 세션은 Spring Security maximumSessions(1)(메모리) 사용 → 03 SPRING_SESSION 표 2개 제외
--   ③ 승인 과제 수정·삭제(D-63)는 [미정] → work_versions 표는 제출·재제출 버전 저장에 필요하므로 유지, 승인 후 수정·삭제 기능은 구현하지 않음
--   05 H5(시드 강의명 갱신)는 데이터이므로 seed.sql로 이동
-- 적재: CREATE DATABASE nolibang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; → schema.sql → seed.sql

-- =================================================================
-- >>> 01_NOLEEBANG_SCHEMA.sql
-- =================================================================
-- 놀이방 LMS · 핵심 스키마 초안 · 2026-11-06
-- 대상: MySQL 8.4 / InnoDB / utf8mb4. 핵심 16테이블 + 현재 통계 VIEW 1개.
-- 실행 전 사용할 빈 DB를 선택하세요. CREATE DATABASE / DROP / 기존 DB 변경은 포함하지 않습니다.
-- 최초 생성용이며 재실행용 마이그레이션이 아닙니다. 실제 MySQL 실행 검증은 미수행입니다.
-- 4가지 확정: 반려 50자, 차시당 영상 1개, 평가 대기=WAITING+IN_REVIEW, 승인 과제 수정·삭제 허용.
-- 50MB=52,428,800바이트, 기본 collation=0900_ai_ci는 구현 기본안(팀 확인 필요).
-- 아이디·이메일·닉네임은 저장 전 서비스 정규화. 대소문자·악센트를 무시하는 UNIQUE 기본안입니다.
-- UTC 저장: 앱 연결에도 time_zone='+00:00'를 적용하세요.
-- P1 댓글·좋아요·문의·로그인 세션 5개 표와 P2/확장은 이 핵심 파일에 미포함.
-- 정원/FIFO/담당 권한/정확히 3차시/이미지 최소 1장/실재생 60%/상태 전이는 서비스가 검증합니다.
-- 배정·반환·평가·분반 변경은 같은 courses 행 FOR UPDATE 잠금으로 직렬화하세요.
-- 승인 후 수정은 새 버전+표시본 교체. 삭제는 표시본 해제·평가 참조 해제·버전/파일 정리.
-- 과제 삭제 후에도 enrollments 수료·review_rounds 결과/피드백은 보존합니다.
-- 파일 정리는 영속 큐/별도 운영 작업 필요. DB 메타데이터 삭제만으로 파일이 삭제되지는 않습니다.
-- FK는 명시한 work_files 외에는 기본 RESTRICT. 원본 설계안의 상세 서비스 계약을 함께 적용하세요.
-- MySQL 공식 제약 문법 참고:
-- https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html
-- https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 계정
CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    nickname VARCHAR(50) NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_EMAIL',
    email_verified_at DATETIME(6) NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    first_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_login (login_id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_nickname (nickname),
    CONSTRAINT ck_users_role CHECK (role IN ('USER','INSTRUCTOR','ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_EMAIL','ACTIVE','INACTIVE')),
    CONSTRAINT ck_users_nickname CHECK (role <> 'USER' OR (nickname IS NOT NULL AND CHAR_LENGTH(TRIM(nickname)) > 0)),
    CONSTRAINT ck_users_password_change CHECK (must_change_password IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 인증·복구 토큰
CREATE TABLE auth_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_token (token_hash),
    KEY idx_auth_user_purpose (user_id,purpose),
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_auth_purpose CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD','RECOVER_LOGIN_ID')),
    CONSTRAINT ck_auth_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 대분류
CREATE TABLE categories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    sort_order INT NOT NULL DEFAULT 0,
    image_key VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_code (code),
    CONSTRAINT ck_categories_state CHECK (state IN ('OPERATING','PREPARING','PRIVATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 세부 취미
CREATE TABLE hobbies (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    thumbnail_key VARCHAR(512) NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_hobbies_code (code),
    CONSTRAINT fk_hobbies_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_hobbies_state CHECK (state IN ('OPERATING','PREPARING','PRIVATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 강사 취미 자격
CREATE TABLE instructor_hobby_grants (
    instructor_id BIGINT UNSIGNED NOT NULL,
    hobby_id BIGINT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    granted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (instructor_id,hobby_id),
    CONSTRAINT fk_grant_instructor FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT fk_grant_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    CONSTRAINT ck_grant_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 공통 강의
CREATE TABLE courses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hobby_id BIGINT UNSIGNED NOT NULL,
    level TINYINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    thumbnail_key VARCHAR(512) NULL,
    supplies_text TEXT NOT NULL,
    assignment_title VARCHAR(200) NOT NULL,
    assignment_description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_courses_hobby_level (hobby_id,level),
    CONSTRAINT fk_courses_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    CONSTRAINT ck_courses_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT ck_courses_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 차시·단일 영상
CREATE TABLE lessons (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    lesson_no TINYINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    learning_objective TEXT NOT NULL,
    content_text TEXT NULL,
    video_key VARCHAR(512) NOT NULL,
    duration_sec DECIMAL(12,3) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_lessons_no (course_id,lesson_no),
    UNIQUE KEY uq_lessons_id_course (id,course_id),
    CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_lessons_no CHECK (lesson_no BETWEEN 1 AND 3),
    CONSTRAINT ck_lessons_video CHECK (CHAR_LENGTH(TRIM(video_key)) > 0 AND duration_sec > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 강사 분반
CREATE TABLE course_sections (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    section_no INT UNSIGNED NOT NULL,
    instructor_id BIGINT UNSIGNED NULL,
    capacity TINYINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_sections_no (course_id,section_no),
    UNIQUE KEY uq_sections_id_course (id,course_id),
    KEY idx_sections_instructor (instructor_id,course_id),
    CONSTRAINT fk_sections_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_sections_instructor FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT ck_sections_capacity CHECK (capacity BETWEEN 1 AND 30),
    CONSTRAINT ck_sections_no CHECK (section_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 수강·기간·수료
CREATE TABLE enrollments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    section_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    confirmed_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_enroll_user_course (user_id,course_id),
    UNIQUE KEY uq_enroll_id_course (id,course_id),
    UNIQUE KEY uq_enroll_id_user_course (id,user_id,course_id),
    KEY idx_enroll_seat (section_id,status),
    KEY idx_enroll_expiry (status,expires_at),
    CONSTRAINT fk_enroll_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_enroll_section FOREIGN KEY (section_id,course_id) REFERENCES course_sections (id,course_id),
    CONSTRAINT ck_enroll_status CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_enroll_period CHECK (expires_at = DATE_ADD(confirmed_at, INTERVAL 30 DAY)),
    CONSTRAINT ck_enroll_complete CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL)),
    CONSTRAINT ck_enroll_cancel CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL)),
    CONSTRAINT ck_enroll_end CHECK ((status = 'ACTIVE' AND ended_at IS NULL) OR (status <> 'ACTIVE' AND ended_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 강의별 FIFO 대기
CREATE TABLE waitlist_entries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    enrollment_id BIGINT UNSIGNED NULL,
    confirmed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    waiting_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 'WAITING' THEN user_id ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_wait_active (course_id,waiting_user_id),
    UNIQUE KEY uq_wait_enrollment (enrollment_id),
    KEY idx_wait_fifo (course_id,status,id),
    CONSTRAINT fk_wait_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wait_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_wait_enrollment FOREIGN KEY (enrollment_id,user_id,course_id) REFERENCES enrollments (id,user_id,course_id),
    CONSTRAINT ck_wait_status CHECK (status IN ('WAITING','CONFIRMED','CANCELLED')),
    CONSTRAINT ck_wait_shape CHECK ((status='WAITING' AND enrollment_id IS NULL AND confirmed_at IS NULL AND cancelled_at IS NULL) OR (status='CONFIRMED' AND enrollment_id IS NOT NULL AND confirmed_at IS NOT NULL AND cancelled_at IS NULL) OR (status='CANCELLED' AND enrollment_id IS NULL AND confirmed_at IS NULL AND cancelled_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 수강별 진도
CREATE TABLE lesson_progresses (
    enrollment_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    played_sec DECIMAL(14,3) NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    last_position_sec DECIMAL(12,3) NOT NULL DEFAULT 0,
    last_ping_at DATETIME(6) NULL,
    last_ping_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    playback_stream_key VARCHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (enrollment_id,lesson_id),
    CONSTRAINT fk_progress_enrollment FOREIGN KEY (enrollment_id,course_id) REFERENCES enrollments (id,course_id),
    CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id,course_id) REFERENCES lessons (id,course_id),
    CONSTRAINT ck_progress_nonnegative CHECK (played_sec >= 0 AND last_position_sec >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 작품·공개·회고
CREATE TABLE works (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    author_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(20) NOT NULL,
    enrollment_id BIGINT UNSIGNED NULL,
    hobby_id BIGINT UNSIGNED NOT NULL,
    level TINYINT UNSIGNED NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    reflection VARCHAR(500) NULL,
    reference_review_id BIGINT UNSIGNED NULL,
    display_version_id BIGINT UNSIGNED NULL,
    admin_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_reason VARCHAR(1000) NULL,
    hidden_at DATETIME(6) NULL,
    hidden_by BIGINT UNSIGNED NULL,
    published_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_works_enrollment (enrollment_id),
    KEY idx_works_growth (author_id,hobby_id,published_at,id),
    KEY idx_works_public (visibility,admin_hidden,deleted_at,published_at,id),
    CONSTRAINT fk_works_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_works_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_works_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    CONSTRAINT fk_works_moderator FOREIGN KEY (hidden_by) REFERENCES users (id),
    CONSTRAINT ck_works_kind CHECK ((kind='FINAL' AND enrollment_id IS NOT NULL) OR (kind='GENERAL' AND enrollment_id IS NULL)),
    CONSTRAINT ck_works_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT ck_works_visibility CHECK (visibility IN ('PRIVATE','PUBLIC')),
    CONSTRAINT ck_works_hidden CHECK (admin_hidden IN (0,1)),
    CONSTRAINT ck_works_deleted CHECK (deleted_at IS NULL OR display_version_id IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 제출 내용 버전
CREATE TABLE work_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_versions_no (work_id,version_no),
    UNIQUE KEY uq_versions_work_id (work_id,id),
    CONSTRAINT fk_versions_work FOREIGN KEY (work_id) REFERENCES works (id),
    CONSTRAINT ck_versions_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 버전별 이미지
CREATE TABLE work_files (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_version_id BIGINT UNSIGNED NOT NULL,
    sort_order TINYINT UNSIGNED NOT NULL,
    original_key VARCHAR(512) NOT NULL,
    thumbnail_key VARCHAR(512) NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(30) NOT NULL,
    source_size_bytes BIGINT UNSIGNED NOT NULL,
    stored_size_bytes BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_files_order (work_version_id,sort_order),
    CONSTRAINT fk_files_version FOREIGN KEY (work_version_id) REFERENCES work_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_files_order CHECK (sort_order BETWEEN 1 AND 5),
    CONSTRAINT ck_files_mime CHECK (mime_type IN ('image/jpeg','image/png','image/webp')),
    CONSTRAINT ck_files_source CHECK (source_size_bytes BETWEEN 1 AND 52428800),
    CONSTRAINT ck_files_stored CHECK (stored_size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 평가·공식 피드백
CREATE TABLE review_rounds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    enrollment_id BIGINT UNSIGNED NOT NULL,
    work_version_id BIGINT UNSIGNED NULL,
    submission_deleted_at DATETIME(6) NULL,
    round_no INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    submitted_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    started_by BIGINT UNSIGNED NULL,
    draft_body TEXT NULL,
    draft_author_id BIGINT UNSIGNED NULL,
    draft_updated_at DATETIME(6) NULL,
    feedback_body TEXT NULL,
    reviewer_id BIGINT UNSIGNED NULL,
    finalized_at DATETIME(6) NULL,
    active_enrollment_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status IN ('WAITING','IN_REVIEW') THEN enrollment_id ELSE NULL END) STORED,
    approved_enrollment_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 'APPROVED' THEN enrollment_id ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_reviews_round (enrollment_id,round_no),
    UNIQUE KEY uq_reviews_version (work_version_id),
    UNIQUE KEY uq_reviews_active (active_enrollment_id),
    UNIQUE KEY uq_reviews_approved (approved_enrollment_id),
    KEY idx_reviews_due (status,due_at),
    CONSTRAINT fk_reviews_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_reviews_version FOREIGN KEY (work_version_id) REFERENCES work_versions (id),
    CONSTRAINT fk_reviews_starter FOREIGN KEY (started_by) REFERENCES users (id),
    CONSTRAINT fk_reviews_draft_author FOREIGN KEY (draft_author_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT ck_reviews_status CHECK (status IN ('WAITING','IN_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_reviews_round CHECK (round_no > 0),
    CONSTRAINT ck_reviews_due CHECK (due_at = DATE_ADD(submitted_at, INTERVAL 7 DAY)),
    CONSTRAINT ck_reviews_version CHECK ((work_version_id IS NOT NULL AND submission_deleted_at IS NULL) OR (work_version_id IS NULL AND submission_deleted_at IS NOT NULL AND status IN ('APPROVED','REJECTED'))),
    CONSTRAINT ck_reviews_shape CHECK ((status='WAITING' AND started_at IS NULL AND started_by IS NULL AND finalized_at IS NULL AND reviewer_id IS NULL AND feedback_body IS NULL AND draft_body IS NULL) OR (status='IN_REVIEW' AND started_at IS NOT NULL AND started_by IS NOT NULL AND finalized_at IS NULL AND reviewer_id IS NULL AND feedback_body IS NULL) OR (status IN ('APPROVED','REJECTED') AND started_at IS NOT NULL AND started_by IS NOT NULL AND finalized_at IS NOT NULL AND reviewer_id IS NOT NULL AND feedback_body IS NOT NULL AND CHAR_LENGTH(feedback_body)>=50 AND CHAR_LENGTH(TRIM(feedback_body))>0)),
    CONSTRAINT ck_reviews_time CHECK ((started_at IS NULL OR started_at >= submitted_at) AND (finalized_at IS NULL OR finalized_at >= started_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 사이트 알림
CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    enrollment_id BIGINT UNSIGNED NULL,
    review_round_id BIGINT UNSIGNED NULL,
    dedupe_key VARCHAR(150) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notifications_event (user_id,dedupe_key),
    KEY idx_notifications_inbox (user_id,read_at,created_at,id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_notifications_review FOREIGN KEY (review_round_id) REFERENCES review_rounds (id),
    CONSTRAINT ck_notifications_kind CHECK (kind IN ('WAITLIST_CONFIRMED','REVIEW_APPROVED','REVIEW_REJECTED','COURSE_COMPLETED')),
    CONSTRAINT ck_notifications_target CHECK ((kind IN ('WAITLIST_CONFIRMED','COURSE_COMPLETED') AND enrollment_id IS NOT NULL) OR (kind IN ('REVIEW_APPROVED','REVIEW_REJECTED') AND review_round_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 순환 참조는 대상 표 생성 후 연결합니다. 표시본은 같은 작품의 버전만 연결합니다.
ALTER TABLE works
    ADD CONSTRAINT fk_works_reference FOREIGN KEY (reference_review_id) REFERENCES review_rounds (id),
    ADD CONSTRAINT fk_works_display FOREIGN KEY (id,display_version_id) REFERENCES work_versions (work_id,id);

-- 수강당 최신 평가 1행만 집계: 과거 재제출 이력에 의한 중복 COUNT 방지.
-- 담당 교체 후에는 현재 분반의 숫자이며, 원 담당자의 과거 실적 통계가 아닙니다.
-- 수강 중·반려·평가 대기 지표는 중첩 가능. 합계를 정원으로 쓰면 안 됩니다.
CREATE VIEW v_section_current_stats AS
SELECT s.id AS section_id, s.course_id, s.instructor_id,
       COALESCE(SUM(e.status='ACTIVE'),0) AS active_count,
       COALESCE(SUM(e.status='COMPLETED'),0) AS completed_count,
       COALESCE(SUM(r.status='REJECTED'),0) AS rejected_count,
       COALESCE(SUM(r.status IN ('WAITING','IN_REVIEW')),0) AS pending_review_count
FROM course_sections s
LEFT JOIN enrollments e ON e.section_id=s.id
LEFT JOIN review_rounds r ON r.enrollment_id=e.id
 AND r.round_no=(SELECT MAX(r2.round_no) FROM review_rounds r2 WHERE r2.enrollment_id=e.id)
GROUP BY s.id,s.course_id,s.instructor_id;


-- =================================================================
-- >>> 02_NOLEEBANG_SCHEMA_ADDON.sql
-- =================================================================
-- 놀이방 LMS · 핵심 스키마 추가분 · 2026-11-06
-- 반드시 01_NOLEEBANG_SCHEMA.sql 적재 후 같은 DB에서 실행합니다.
-- 원칙: NOLEEBANG_SCHEMA.sql(정본)은 수정하지 않고, 이 파일은 "추가"만 합니다.
--       표 이름·칼럼·상태값·FK 기본 RESTRICT·DATETIME(6)·utf8mb4_0900_ai_ci 규칙은 정본을 따릅니다.
-- 구성: A. 정본 표에 CHECK 3개 추가
--       B. 조회용 VIEW 4개 (v_public_works는 C절) · 정본의 v_section_current_stats는 그대로 사용
--       C. P1 표 4개 (FR-033 좋아요·댓글, FR-042 1:1 문의)
-- FR-005 로그인 세션 표는 NOLEEBANG_SPRING_SESSION.sql (P1 5번째 표).
-- 대상: MySQL 8.4 (8.0.46에서 적재 검증)

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- =====================================================================
-- A. 정본 표에 CHECK 추가 (정본에서 DB로 막지 않던 3가지)
-- =====================================================================

-- FR-012·D-51: 임시 비밀번호 첫 로그인 변경은 강사·관리자 계정에만 해당
ALTER TABLE users
    ADD CONSTRAINT ck_users_temp_pw_staff CHECK (must_change_password = 0 OR role IN ('INSTRUCTOR','ADMIN'));

-- FR-036: 관리자 숨김에는 사유·시각 필수 (작성자가 사유를 확인, U-S16)
-- FR-034: 공개 작품은 공개 시각 필수 (게시판 기본 정렬 = 최신 공개순)
ALTER TABLE works
    ADD CONSTRAINT ck_works_hidden_reason CHECK (admin_hidden = 0 OR (hidden_reason IS NOT NULL AND CHAR_LENGTH(TRIM(hidden_reason)) > 0 AND hidden_at IS NOT NULL)),
    ADD CONSTRAINT ck_works_published CHECK (visibility = 'PRIVATE' OR published_at IS NOT NULL);

-- =====================================================================
-- B. 조회용 VIEW
-- =====================================================================

-- FR-052 배정: 분반별 남은 자리. 배정 대상 = assignable=1 AND remaining>0,
-- ORDER BY remaining DESC, section_no ASC (D-52)
CREATE VIEW v_section_seats AS
SELECT s.id AS section_id, s.course_id, s.section_no, s.instructor_id, s.capacity,
       COUNT(e.id) AS active_count,
       s.capacity - COUNT(e.id) AS remaining,
       (s.instructor_id IS NOT NULL) AS assignable
FROM course_sections s
LEFT JOIN enrollments e ON e.section_id = s.id AND e.status = 'ACTIVE'
GROUP BY s.id, s.course_id, s.section_no, s.instructor_id, s.capacity;

-- FR-024 수준 배지: 취미·수준별 수료 1건 이상. 수료는 enrollments에만 있으므로
-- 승인 과제를 수정·삭제해도 배지가 유지됩니다(D-63, NFR-06).
CREATE VIEW v_user_level_badges AS
SELECT e.user_id, c.hobby_id, c.level, MIN(e.completed_at) AS earned_at
FROM enrollments e
JOIN courses c ON c.id = e.course_id
WHERE e.status = 'COMPLETED'
GROUP BY e.user_id, c.hobby_id, c.level;

-- FR-024 마스터 배지: 같은 취미의 수준 1·2·3 모두
CREATE VIEW v_user_master_badges AS
SELECT user_id, hobby_id, MAX(earned_at) AS earned_at
FROM v_user_level_badges
GROUP BY user_id, hobby_id
HAVING COUNT(DISTINCT level) = 3;

-- FR-034 게시판 VIEW v_public_works는 work_likes를 참조하므로 C절에서 표 생성 뒤 정의합니다.

-- =====================================================================
-- C. P1 표
-- =====================================================================

-- FR-033 좋아요: 본인 작품 허용, 사용자별 1회, 취소 = 행 삭제
CREATE TABLE work_likes (
    work_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (work_id,user_id),
    KEY idx_likes_user (user_id),
    CONSTRAINT fk_likes_work FOREIGN KEY (work_id) REFERENCES works (id),
    CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FR-034 게시판: 일반 이용자에게 보이는 작품 (공개 + 숨김 아님 + 삭제 안 됨),
-- 제목은 현재 표시본(display_version_id)의 제목
CREATE VIEW v_public_works AS
SELECT w.id AS work_id, w.kind, w.hobby_id, w.level, v.title, w.published_at, w.created_at,
       u.nickname AS author_nickname,
       (SELECT COUNT(*) FROM work_likes l WHERE l.work_id = w.id) AS like_count
FROM works w
JOIN work_versions v ON v.id = w.display_version_id
JOIN users u ON u.id = w.author_id
WHERE w.visibility = 'PUBLIC' AND w.admin_hidden = 0 AND w.deleted_at IS NULL;

-- FR-033 댓글·1단계 답글
--   parent_id NULL = 댓글, 값 있음 = 답글 (답글의 답글 금지·배지 자격은 서비스)
--   자발 삭제 = status 'DELETED' + deleted_at, 화면은 '삭제된 댓글입니다', 답글 유지
CREATE TABLE work_comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    body VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_comments_work (work_id,parent_id,id),
    KEY idx_comments_author (author_id),
    CONSTRAINT fk_comments_work FOREIGN KEY (work_id) REFERENCES works (id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES work_comments (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT ck_comments_status CHECK (status IN ('NORMAL','DELETED')),
    CONSTRAINT ck_comments_deleted CHECK ((status = 'DELETED' AND deleted_at IS NOT NULL) OR (status = 'NORMAL' AND deleted_at IS NULL)),
    CONSTRAINT ck_comments_body CHECK (CHAR_LENGTH(TRIM(body)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FR-042 1:1 문의: 수강 건에 묶음 → 받는 강사 = 그 분반의 현재 담당 강사
--   (enrollment_id, learner_id, course_id) 3칼럼 FK로 본인 수강 건만 연결 (정본 waitlist와 같은 방식)
--   담당 공백 중 새 문의 금지·강사 일방 발송 금지는 서비스
CREATE TABLE inquiry_threads (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    enrollment_id BIGINT UNSIGNED NOT NULL,
    learner_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    last_message_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_inquiry_enrollment (enrollment_id,last_message_at),
    KEY idx_inquiry_learner (learner_id,last_message_at),
    CONSTRAINT fk_inquiry_enrollment FOREIGN KEY (enrollment_id,learner_id,course_id) REFERENCES enrollments (id,user_id,course_id),
    CONSTRAINT ck_inquiry_status CHECK (status IN ('OPEN','ANSWERED','CLOSED')),
    CONSTRAINT ck_inquiry_title CHECK (CHAR_LENGTH(TRIM(title)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inquiry_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    thread_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_inquiry_messages_thread (thread_id,id),
    CONSTRAINT fk_inquiry_messages_thread FOREIGN KEY (thread_id) REFERENCES inquiry_threads (id),
    CONSTRAINT fk_inquiry_messages_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT ck_inquiry_messages_body CHECK (CHAR_LENGTH(TRIM(body)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 참고: P1 알림(댓글·답글·문의 답변·숨김/복구)은 정본 notifications의
-- ck_notifications_kind / ck_notifications_target이 P0 4종만 허용하므로,
-- 해당 기능을 구현할 때 그 두 CHECK를 함께 확장합니다(07-screens U-S15: P1 알림은 기능 구현 시 연결).


-- =================================================================
-- >>> 05_NOLEEBANG_HTML_PATCH.sql
-- =================================================================
-- 놀이방 LMS · 시연 HTML 맞춤 패치 · 2026-11-06
-- 적재 순서: 01 정본 → 02 추가분 → 03 세션 → 04 시드 → 05 이 파일 (시드 다음에 실행해야 H5 강의명 갱신이 적용됨)
-- 원칙: 팀 정본·추가분 파일은 수정하지 않고 ALTER/CREATE로만 덧붙입니다.
-- 근거: nolibang-lms HTML 화면에 있는데 DB가 받쳐 주지 못하던 항목
-- [11-06 팀 결정 반영] H3 약관 동의 표는 제외(약관·만 14세 확인 미구현), H5 강의명 갱신은 seed.sql로 이동
--   H1 알림 종류 (U-S15·T-S09·공통 헤더 알림)
--   H2 강사 통계 정의 (T-S05·A-S13 "통계 정의" 문구)
--   H3 약관 동의 기록 → [제외]
--   H4 분반 이름 (A-S05 "분반 이름" 입력, 화면 전반의 "A분반")

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------
-- H1. 알림: 화면에 나오는 알림 종류를 모두 받을 수 있게 확장
--   학습자(U-S15): 수강 확정·평가 승인/반려·수료 + 작품 숨김/복구·댓글/답글·문의 답변
--   강사(T-S09):   새 문의·새 공개 댓글·과제 제출·분반 배정
--   [11-06 팀 결정] 피드백 마감 임박(REVIEW_DUE_SOON)은 제외 — 마감 임박·초과는 강사 평가 목록(T-S03) 화면 경고로만 표시
-- ---------------------------------------------------------------------
ALTER TABLE notifications
    ADD COLUMN work_id BIGINT UNSIGNED NULL AFTER review_round_id,
    ADD COLUMN comment_id BIGINT UNSIGNED NULL AFTER work_id,
    ADD COLUMN inquiry_thread_id BIGINT UNSIGNED NULL AFTER comment_id,
    ADD COLUMN section_id BIGINT UNSIGNED NULL AFTER inquiry_thread_id,
    ADD CONSTRAINT fk_notifications_work    FOREIGN KEY (work_id)           REFERENCES works (id),
    ADD CONSTRAINT fk_notifications_comment FOREIGN KEY (comment_id)        REFERENCES work_comments (id),
    ADD CONSTRAINT fk_notifications_inquiry FOREIGN KEY (inquiry_thread_id) REFERENCES inquiry_threads (id),
    ADD CONSTRAINT fk_notifications_section FOREIGN KEY (section_id)        REFERENCES course_sections (id),
    DROP CHECK ck_notifications_kind,
    DROP CHECK ck_notifications_target;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_kind CHECK (kind IN (
        'WAITLIST_CONFIRMED','REVIEW_APPROVED','REVIEW_REJECTED','COURSE_COMPLETED',  -- 정본 P0 4종 그대로
        'WORK_HIDDEN','WORK_RESTORED','COMMENT_ON_MY_WORK','REPLY_TO_MY_COMMENT','INQUIRY_ANSWERED',
        'INQUIRY_RECEIVED','PUBLIC_COMMENT','REVIEW_SUBMITTED','SECTION_ASSIGNED')),
    ADD CONSTRAINT ck_notifications_target CHECK (
        (kind IN ('WAITLIST_CONFIRMED','COURSE_COMPLETED') AND enrollment_id IS NOT NULL)
     OR (kind IN ('REVIEW_APPROVED','REVIEW_REJECTED','REVIEW_SUBMITTED') AND review_round_id IS NOT NULL)
     OR (kind IN ('WORK_HIDDEN','WORK_RESTORED') AND work_id IS NOT NULL)
     OR (kind IN ('COMMENT_ON_MY_WORK','REPLY_TO_MY_COMMENT','PUBLIC_COMMENT') AND comment_id IS NOT NULL)
     OR (kind IN ('INQUIRY_ANSWERED','INQUIRY_RECEIVED') AND inquiry_thread_id IS NOT NULL)
     OR (kind = 'SECTION_ASSIGNED' AND section_id IS NOT NULL));

-- ---------------------------------------------------------------------
-- H2. 강사 통계: 화면(T-S05·A-S13)의 "통계 정의" 그대로
--   수강 중   = 학습 중(평가 대기·진행 제외)  → ACTIVE 이면서 마지막 회차가 대기·진행이 아님
--   평가 대기 = 제출 후 평가 대기·진행 중     → 마지막 회차 WAITING + IN_REVIEW (D-62와 같음)
--   수료      = 수료 승인 누적                → COMPLETED 수
--   반려      = 반려 확정 누적                → REJECTED 회차 수(재제출 전후 모두)
--   정본 VIEW v_section_current_stats는 그대로 두고 화면용 VIEW를 따로 둡니다.
-- ---------------------------------------------------------------------
CREATE VIEW v_section_stats_screen AS
SELECT s.id AS section_id, s.course_id, s.instructor_id,
       COALESCE(SUM(e.status = 'ACTIVE' AND (lr.status IS NULL OR lr.status NOT IN ('WAITING','IN_REVIEW'))), 0) AS learning_count,
       COALESCE(SUM(lr.status IN ('WAITING','IN_REVIEW')), 0) AS pending_review_count,
       COALESCE(SUM(e.status = 'COMPLETED'), 0) AS completed_count,
       COALESCE(SUM(rj.cnt), 0) AS rejected_total
FROM course_sections s
LEFT JOIN enrollments e ON e.section_id = s.id
LEFT JOIN review_rounds lr ON lr.enrollment_id = e.id
 AND lr.round_no = (SELECT MAX(r2.round_no) FROM review_rounds r2 WHERE r2.enrollment_id = e.id)
LEFT JOIN (SELECT enrollment_id, COUNT(*) AS cnt FROM review_rounds WHERE status = 'REJECTED' GROUP BY enrollment_id) rj
  ON rj.enrollment_id = e.id
GROUP BY s.id, s.course_id, s.instructor_id;

-- ---------------------------------------------------------------------
-- H4. 분반 이름 (A-S05 "분반 이름" 입력, 화면의 "A분반·B분반")
--   비워 두면 화면에서 section_no로 표시(1→A분반). 정렬·동률 기준은 계속 section_no.
-- ---------------------------------------------------------------------
ALTER TABLE course_sections
    ADD COLUMN name VARCHAR(30) NULL AFTER section_no,
    ADD UNIQUE KEY uq_sections_name (course_id,name);


-- ---------------------------------------------------------------------
-- H5. 작품 숨김·복구 조치 이력 (#20, A-S08 "조치 이력" · U-S16 작성자 사유 확인)
--   works 는 "지금" 숨김 상태(admin_hidden·hidden_*)만 가지므로, 숨김·복구를 할 때마다 한 줄씩 남긴다.
--   action 'HIDE'/'RESTORE', reason = 숨김 사유 또는 복구 사유(F-10 ⑤). 행은 지우거나 고치지 않는다(이력).
--   표가 없으면 #20(관리자 작품 숨김·복구)과 U-S16 화면이 동작하지 않는다 → 새로 schema.sql 을 적용하거나 이 블록만 실행.
-- ---------------------------------------------------------------------
CREATE TABLE work_moderation_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    admin_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_modlogs_work (work_id,id),
    KEY idx_modlogs_created (created_at,id),
    CONSTRAINT fk_modlogs_work FOREIGN KEY (work_id) REFERENCES works (id),
    CONSTRAINT fk_modlogs_admin FOREIGN KEY (admin_id) REFERENCES users (id),
    CONSTRAINT ck_modlogs_action CHECK (action IN ('HIDE','RESTORE')),
    CONSTRAINT ck_modlogs_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =================================================================
-- 회원 탈퇴(WITHDRAWN) 지원 — migration_withdraw.sql과 같은 내용
-- =================================================================
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL AFTER first_login_at,
    DROP CHECK ck_users_status;

ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING_EMAIL','ACTIVE','INACTIVE','WITHDRAWN')),
    ADD CONSTRAINT ck_users_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL));

-- =================================================================
-- RPG 성장 요소(캐릭터·퀘스트·꾸미기) — migration_game.sql과 같은 내용
-- =================================================================
CREATE TABLE quest_claims (
    user_id BIGINT UNSIGNED NOT NULL,
    quest_key VARCHAR(40) NOT NULL,
    period_key VARCHAR(20) NOT NULL,
    reward_xp INT UNSIGNED NOT NULL,
    claimed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, quest_key, period_key),
    KEY idx_quest_claims_claimed (claimed_at),
    CONSTRAINT fk_quest_claims_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_avatars (
    user_id BIGINT UNSIGNED NOT NULL,
    avatar_code VARCHAR(30) NULL,
    frame_code VARCHAR(30) NULL,
    name_color_code VARCHAR(30) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 업적 배지 획득 기록 — migration_game.sql과 같은 내용
CREATE TABLE user_achievements (
    user_id BIGINT UNSIGNED NOT NULL,
    badge_code VARCHAR(40) NOT NULL,
    earned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, badge_code),
    CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 대표 배지 장착(최대 3개) — migration_game.sql과 같은 내용
CREATE TABLE user_equipped_badges (
    user_id BIGINT UNSIGNED NOT NULL,
    slot_no TINYINT UNSIGNED NOT NULL,
    badge_code VARCHAR(40) NOT NULL,
    equipped_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, slot_no),
    UNIQUE KEY uq_equipped_badge (user_id, badge_code),
    CONSTRAINT fk_equipped_badges_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_equipped_badges_slot CHECK (slot_no BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
