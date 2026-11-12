-- =====================================================================
-- 놀이방 LMS · DB와 접속 계정 만들기 (MySQL 8, root 계정으로 한 번 실행)
-- 실행 순서: setup_database.sql (root) → schema.sql → seed.sql
-- 계정·비밀번호 wanted / wanted 는 application.yaml 기본값(LOCAL_DB_USERNAME / LOCAL_DB_PASSWORD)과 같다.
--   다른 값으로 바꾸면 환경변수 LOCAL_DB_USERNAME·LOCAL_DB_PASSWORD 도 같이 바꾼다.
-- 이 파일은 기존 DB를 지우지 않는다. 이미 nolibang 에 표가 있으면 schema.sql 이 실패하므로,
--   처음부터 다시 적재하려면 먼저 DROP DATABASE nolibang; 을 실행한 뒤 이 파일부터 다시 실행한다.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS nolibang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'wanted'@'localhost' IDENTIFIED BY 'wanted';
CREATE USER IF NOT EXISTS 'wanted'@'%' IDENTIFIED BY 'wanted';

GRANT ALL PRIVILEGES ON nolibang.* TO 'wanted'@'localhost';
GRANT ALL PRIVILEGES ON nolibang.* TO 'wanted'@'%';
FLUSH PRIVILEGES;
