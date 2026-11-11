-- =================================================================
-- 회원 탈퇴 개인정보 정리(U-15, D-25) — schema.sql 맨 끝 "회원 탈퇴 개인정보 정리" 블록과 같은 내용
-- 이미 schema.sql 을 적재한 DB에 이 파일만 한 번 실행한다. (변경 요청서: docs/DB_변경요청_회원탈퇴_개인정보정리.md)
-- 1) withdrawn_email_holds : 이메일 원문 대신 재가입 제한 확인값(HMAC-SHA256)만 30×24시간 보관
-- 2) work_comments.author_id NULL 허용 : 탈퇴자 댓글을 원 계정과 끊어 익명 보존
-- 3) works.anonymous_like_count : 탈퇴자가 누른 좋아요를 계정 연결 없이 숫자로만 남김
-- 4) v_public_works : 좋아요 수 = 회원 좋아요 행 수 + 익명 좋아요 수
-- =================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE withdrawn_email_holds (
    email_hash CHAR(64) NOT NULL,
    withdrawn_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (email_hash),
    KEY idx_email_holds_expiry (expires_at),
    CONSTRAINT ck_email_holds_period CHECK (expires_at = DATE_ADD(withdrawn_at, INTERVAL 30 DAY))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE work_comments
    MODIFY author_id BIGINT UNSIGNED NULL;

ALTER TABLE works
    ADD COLUMN anonymous_like_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER published_at;

CREATE OR REPLACE VIEW v_public_works AS
SELECT w.id AS work_id, w.kind, w.hobby_id, w.level, v.title, w.published_at, w.created_at,
       u.nickname AS author_nickname,
       (SELECT COUNT(*) FROM work_likes l WHERE l.work_id = w.id) + w.anonymous_like_count AS like_count
FROM works w
JOIN work_versions v ON v.id = w.display_version_id
JOIN users u ON u.id = w.author_id
WHERE w.visibility = 'PUBLIC' AND w.admin_hidden = 0 AND w.deleted_at IS NULL;
