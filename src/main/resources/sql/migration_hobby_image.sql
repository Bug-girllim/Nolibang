-- =================================================================
-- 관리자 대표 사진 — 대분류에 사진 칸 추가 (세부 취미는 hobbies.thumbnail_key 를 그대로 쓴다)
-- schema.sql 의 categories.image_key 와 같은 내용. 이미 있는 DB에 한 번만 실행한다(두 번 실행하면 "Duplicate column" 오류 — 무시해도 된다).
-- =================================================================
ALTER TABLE categories ADD COLUMN image_key VARCHAR(512) NULL AFTER sort_order;
