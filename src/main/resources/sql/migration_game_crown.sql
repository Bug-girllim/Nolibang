-- 기존 DB에 한 번 실행: MySQL 8, 현재 연결 DB를 nolibang으로 선택한 뒤 실행합니다.
-- 기존 user_avatars 테이블을 유지하며 두 열만 추가합니다. 반복 실행해도 중복 열을 만들지 않습니다.
SET @nb_outfit_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user_avatars' AND column_name = 'outfit_code');
SET @nb_crown_sql = IF(@nb_outfit_exists = 0,
  'ALTER TABLE user_avatars ADD COLUMN outfit_code VARCHAR(30) NULL', 'SELECT 1');
PREPARE nb_crown_stmt FROM @nb_crown_sql;
EXECUTE nb_crown_stmt;
DEALLOCATE PREPARE nb_crown_stmt;

SET @nb_head_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user_avatars' AND column_name = 'head_code');
SET @nb_crown_sql = IF(@nb_head_exists = 0,
  'ALTER TABLE user_avatars ADD COLUMN head_code VARCHAR(30) NULL', 'SELECT 1');
PREPARE nb_crown_stmt FROM @nb_crown_sql;
EXECUTE nb_crown_stmt;
DEALLOCATE PREPARE nb_crown_stmt;

-- 예전에는 왕관이 동물 선택 자체를 덮어썼으므로 예전 동물은 DB에 남아 있지 않습니다.
-- 왕관을 머리 슬롯으로 옮기고 동물은 초기값으로 돌립니다. 해당 사용자는 동물을 다시 선택해 주세요.
UPDATE user_avatars
SET head_code = 'crown', avatar_code = NULL
WHERE avatar_code = 'crown';
