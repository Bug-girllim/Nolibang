-- 놀이방 LMS · seed.sql (lms-practice 적재용)
-- 구성: 팀 시드(04_NOLEEBANG_SEED.sql) + HTML 패치 H5(강의명) + [추가] 시연·개발용 데이터
-- 적재 순서: schema.sql → seed.sql
-- 11-06 팀 결정 반영: ⑤ 관리자 첫 로그인 비밀번호 변경 해제 ⑥ 부족한 시드 보충(사진·영상 취미 2개, 시연 계정, 분반, 수강·대기·진도·과제·평가·작품·댓글·문의·알림)

-- =================================================================
-- >>> 04_NOLEEBANG_SEED.sql
-- =================================================================
-- 놀이방 LMS · 시드 데이터 · 2026-11-06 (D-59: 카테고리·강의는 시드로 입력)
-- 적재 순서: 01 정본 → 02 추가분 → 03 세션 → 04 이 파일 → 05 HTML 패치
-- 출처: PRD.md·04-features.md (대분류 4 · 운영 취미 3 · 준비 중 취미 4 · 비공개 취미 2)
-- 강의 9개 × 3차시 = 27차시, 차시당 영상 1개 = 영상 27개 (D-61)
-- 수준 값: 1 = 입문, 2 = 중급, 3 = 고급 (정본 courses.level)
-- TODO 표시 값(설명·학습 목표·영상 키·영상 길이)은 콘텐츠 확정 후 교체합니다.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 관리자 1명: 초기 비밀번호 demo_Admin01 (BCrypt, 제출용 시연 계정 9개 표와 같은 값)
-- [11-06 팀 결정] 첫 로그인 비밀번호 변경(C-09)은 강사 전용 화면이므로 관리자는 must_change_password = FALSE.
--   (TRUE면 관리자가 로그인 직후 막힘) 운영 배포 전 비밀번호는 직접 바꾼다.
INSERT INTO users (login_id, password_hash, real_name, email, role, status, email_verified_at, must_change_password)
VALUES ('admin', '$2a$10$5BvrNiMG28hjIN4N4yY9dOvxycsez93yZr4oMqNC9VVwtlDiX2Pza',
        '운영자', 'admin@nolibang.local', 'ADMIN', 'ACTIVE', UTC_TIMESTAMP(6), FALSE);

INSERT INTO categories (id, code, name, state, sort_order) VALUES
  (1, 'art',    '미술·드로잉', 'OPERATING', 1),
  (2, 'cook',   '요리·베이킹', 'OPERATING', 2),
  (3, 'craft',  '공예',        'PREPARING', 3),
  (4, 'photo',  '사진·영상',   'PRIVATE',   4);

INSERT INTO hobbies (id, category_id, code, name, description, state, sort_order) VALUES
  (1, 1, 'colored-pencil', '색연필화',      '', 'OPERATING', 1),
  (2, 1, 'digital-drawing','디지털 드로잉', '', 'OPERATING', 2),
  (3, 2, 'dessert',        '디저트 만들기', '', 'OPERATING', 1),
  (4, 3, 'woodwork',       '목공',          '', 'PREPARING', 1),
  (5, 3, 'leather',        '가죽공예',      '', 'PREPARING', 2),
  (6, 3, 'ceramics',       '도자기',        '', 'PREPARING', 3),
  (7, 3, 'ring',           '반지공예',      '', 'PREPARING', 4),
  -- [추가] 사진·영상 하위 취미 2개 (04-features F-02: 비공개, 관리자만 조회)
  (8, 4, 'photo-shooting', '사진 촬영',     '', 'PRIVATE',   1),
  (9, 4, 'video-editing',  '영상 편집',     '', 'PRIVATE',   2);

INSERT INTO courses (id, hobby_id, level, title, description, supplies_text, assignment_title, assignment_description) VALUES
  (1, 1, 1, '색연필화 입문',      '', '', '색연필화 입문 최종 과제',      ''),
  (2, 1, 2, '색연필화 중급',      '', '', '색연필화 중급 최종 과제',      ''),
  (3, 1, 3, '색연필화 고급',      '', '', '색연필화 고급 최종 과제',      ''),
  (4, 2, 1, '디지털 드로잉 입문', '', '', '디지털 드로잉 입문 최종 과제', ''),
  (5, 2, 2, '디지털 드로잉 중급', '', '', '디지털 드로잉 중급 최종 과제', ''),
  (6, 2, 3, '디지털 드로잉 고급', '', '', '디지털 드로잉 고급 최종 과제', ''),
  (7, 3, 1, '디저트 만들기 입문', '', '', '디저트 만들기 입문 최종 과제', ''),
  (8, 3, 2, '디저트 만들기 중급', '', '', '디저트 만들기 중급 최종 과제', ''),
  (9, 3, 3, '디저트 만들기 고급', '', '', '디저트 만들기 고급 최종 과제', '');

-- 27차시, 차시당 영상 1개
-- 영상 경로: file.upload-dir(./uploads) 아래 실제 파일 — uploads/videos/{drawing|cooking}/{취미}/{취미}_{입문|중급|고급}_{N}차시_10분.mp4
--   hobby_id 1 색연필화 → drawing/색연필화, 2 디지털 드로잉 → drawing/디지털드로잉, 3 디저트 만들기 → cooking/디저트만들기
--   (FROM·JOIN은 바꾸지 않는다 — 차시 id 부여 순서가 달라지면 id를 전제로 한 테스트가 깨진다)
INSERT INTO lessons (course_id, lesson_no, title, learning_objective, video_key, duration_sec)
SELECT c.id, n.no,
       CONCAT(c.title, ' ', n.no, '차시'),
       '',
       CONCAT('videos/',
              CASE c.hobby_id WHEN 1 THEN 'drawing/색연필화/색연필화'
                              WHEN 2 THEN 'drawing/디지털드로잉/디지털드로잉'
                              WHEN 3 THEN 'cooking/디저트만들기/디저트만들기' END,
              '_', ELT(c.level, '입문', '중급', '고급'), '_', n.no, '차시_10분.mp4'),
       600.000  -- 영상 실제 길이 10분 (uploads/videos/… 파일 27개 모두 600초)
FROM courses c
CROSS JOIN (SELECT 1 AS no UNION ALL SELECT 2 UNION ALL SELECT 3) n;

-- ---------------------------------------------------------------------
-- H5. 시드 강의명을 화면(U-S04-0·A-S12·T-S04 등) 표기와 맞춤
--   화면: "색연필화 기초/응용/심화", "디지털 드로잉 기초/응용/심화", "디저트 기초/응용/심화"
--   수준 표시는 그대로 입문(1)·중급(2)·고급(3)
--   과제 기본 제목: 화면 T-S04 "색연필화 기초 — 최종 과제"
-- ---------------------------------------------------------------------
UPDATE courses c
JOIN hobbies h ON h.id = c.hobby_id
SET c.title = CONCAT(CASE h.code WHEN 'colored-pencil' THEN '색연필화'
                                 WHEN 'digital-drawing' THEN '디지털 드로잉'
                                 WHEN 'dessert' THEN '디저트' END,
                     ' ', ELT(c.level, '기초', '응용', '심화')),
    c.assignment_title = CONCAT(CASE h.code WHEN 'colored-pencil' THEN '색연필화'
                                            WHEN 'digital-drawing' THEN '디지털 드로잉'
                                            WHEN 'dessert' THEN '디저트' END,
                                ' ', ELT(c.level, '기초', '응용', '심화'), ' — 최종 과제')
WHERE h.code IN ('colored-pencil','digital-drawing','dessert');

UPDATE lessons l JOIN courses c ON c.id = l.course_id
SET l.title = CONCAT(c.title, ' ', l.lesson_no, '차시');

-- =====================================================================
-- [추가] 제출용 시연 데이터 — 시연 계정 9개(관리자 포함) 기준
--   계정·비밀번호·초기 상태: README "시연 계정" 표, 로그인 화면(C-02) 안내와 같은 값.
--   비밀번호는 계정마다 다르다(BCrypt). 모두 가입 규칙(8~16자, 영문·숫자·특수문자)을 지킨다.
--   시각은 적재 시점(@now) 기준 상대값 → 시연 직전에 적재하면 30일·7일 기한이 그대로 살아 있다.
--   운영 배포 시에는 이 구간을 빼고 적재한다.
-- =====================================================================
SET @now = UTC_TIMESTAMP(6);

-- ---------------------------------------------------------------------
-- D1. 계정 (id 2~9, 관리자 admin은 위 id 1)
--   learnerA : 이메일 인증 완료, 수강 이력 없음 (빈 '나의 학습' → 빈자리 있는 강의 신청)
--   learnerB : 색연필화 입문 수강 중(영상 1개 남음·과제 미제출) / 디지털 드로잉 입문 평가 대기(강사A)
--              디저트 만들기 중급 수료(30일 수강기간 경과) / 공개 작품 2개 + 관리자 숨김 작품 1개
--   learnerC : 디지털 드로잉 입문 강사B 분반 수강 중(1회차 반려 → 재제출 → 평가 진행 중)
--              색연필화 입문 정원 마감으로 대기 중 / 공개 작품 2개
--   newbie   : 가입 완료, 이메일 미인증 (로그인 불가 시연)
--   teacherA : 디지털 드로잉 입문 A분반(learnerB) · 색연필화 입문 A분반(learnerB) 담당
--   teacherB : 디지털 드로잉 입문 B분반(learnerC) · 디저트 만들기 담당
--   teacherC : 비활성 강사 (로그인 불가 시연)
--   teacherD : 첫 로그인 전, 임시 비밀번호 발급 상태 (로그인 직후 비밀번호 변경 화면 C-09)
-- ---------------------------------------------------------------------
INSERT INTO users (id, login_id, password_hash, real_name, email, nickname, role, status, email_verified_at, must_change_password, first_login_at) VALUES
  (2, 'learnerA', '$2a$10$PQMzNgll/lg2rr9MRm1oNuvTtPC1tDMq0BsOzS6Zp9owmG8LELQ1W', '김하나', 'learnera@nolibang.local', '새싹하나',     'USER',       'ACTIVE',        @now - INTERVAL 2 DAY,  FALSE, @now - INTERVAL 2 DAY),
  (3, 'learnerB', '$2a$10$DiXaB2cxKc72Cqn7k5WrtuiMNBdDRXBrqwvBiOWiaRU.p.hILoAy6', '이배움', 'learnerb@nolibang.local', '차분한수달',   'USER',       'ACTIVE',        @now - INTERVAL 60 DAY, FALSE, @now - INTERVAL 60 DAY),
  (4, 'learnerC', '$2a$10$pA9HUyM.TJZS3JwL0DsceeA3tvy2vmuG42N0CnVmOExWBs68y3rue', '박연습', 'learnerc@nolibang.local', '용감한펭귄',   'USER',       'ACTIVE',        @now - INTERVAL 20 DAY, FALSE, @now - INTERVAL 20 DAY),
  (5, 'newbie',   '$2a$10$UPrdyUUrpFkfC74Ev1ZIOOwKh1s5VtKoZ1TrAFPZOxj1KvJ76I0F.', '최신규', 'newbie@nolibang.local',   '수줍은다람쥐', 'USER',       'PENDING_EMAIL', NULL,                   FALSE, NULL),
  (6, 'teacherA', '$2a$10$L/0As7WqLdx9LZ7ZGpU1re382mB.nbAIO4TzLebwRxdxbm8YAlLCC', '정강사', 'teachera@nolibang.local', '강사A',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 90 DAY, FALSE, @now - INTERVAL 90 DAY),
  (7, 'teacherB', '$2a$10$yIbuxjtqOW3ivG0SRJFEe.3L5bXCNWG6b6S5jKDPgvevmTZ1qfoRK', '한강사', 'teacherb@nolibang.local', '강사B',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 90 DAY, FALSE, @now - INTERVAL 90 DAY),
  (8, 'teacherC', '$2a$10$oWqpZbTHy9rDfo3GjVEmCOqsDTOtaa214.gCoV4RSCL7m8Kz2xQxu', '오강사', 'teacherc@nolibang.local', '강사C',        'INSTRUCTOR', 'INACTIVE',      @now - INTERVAL 90 DAY, FALSE, @now - INTERVAL 80 DAY),
  (9, 'teacherD', '$2a$10$IwMGQSCSYXdg7mLx1GBVG.eYmufbt6q0nZMRb56BWg6r62zRo5NrS', '윤강사', 'teacherd@nolibang.local', '강사D',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 1 DAY,  TRUE,  NULL);

-- ---------------------------------------------------------------------
-- D2. 강사 취미 권한
-- ---------------------------------------------------------------------
INSERT INTO instructor_hobby_grants (instructor_id, hobby_id) VALUES
  (6, 1), (6, 2),      -- teacherA: 색연필화, 디지털 드로잉
  (7, 2), (7, 3),      -- teacherB: 디지털 드로잉, 디저트 만들기
  (8, 3),              -- teacherC(비활성): 디저트 만들기
  (9, 2);              -- teacherD: 디지털 드로잉

-- ---------------------------------------------------------------------
-- D3. 분반 (9강의 모두 신청 가능하도록 최소 1개)
--   s1 색연필화 입문 A분반 정원 1 = learnerB 수강으로 만석 → learnerC 대기열 시연
--      (관리자가 정원을 늘리면 대기 1순위 learnerC가 자동 확정되고 알림을 받는다)
--   s4·s5 디지털 드로잉 입문 A분반(teacherA)·B분반(teacherB) — 같은 강의의 다른 분반
--   s8 디지털 드로잉 고급 B분반 담당 공백 → 배정 제외 시연
-- ---------------------------------------------------------------------
INSERT INTO course_sections (id, course_id, section_no, name, instructor_id, capacity) VALUES
  (1,  1, 1, 'A분반', 6,    1),
  (2,  2, 1, 'A분반', 6,    5),
  (3,  3, 1, 'A분반', 6,    5),
  (4,  4, 1, 'A분반', 6,    5),
  (5,  4, 2, 'B분반', 7,    5),
  (6,  5, 1, 'A분반', 9,    5),
  (7,  6, 1, 'A분반', 6,    5),
  (8,  6, 2, 'B분반', NULL, 5),
  (9,  7, 1, 'A분반', 7,   10),
  (10, 8, 1, 'A분반', 7,   10),
  (11, 9, 1, 'A분반', 7,   10);

-- ---------------------------------------------------------------------
-- D4. 수강 (expires_at = confirmed_at + 30일 CHECK)
--   e1 learnerB c1 색연필화 입문     s1 ACTIVE    (대기 → 자동 확정, 3차시 중 2차시 완료, 과제 미제출)
--   e2 learnerB c4 디지털 드로잉 입문 s4 ACTIVE    (3차시 완료, 최종 과제 평가 대기 — teacherA)
--   e3 learnerB c8 디저트 만들기 중급 s10 COMPLETED (승인 → 수료, 30일 수강기간 경과 → 기간 후 재시청)
--   e4 learnerC c4 디지털 드로잉 입문 s5 ACTIVE    (1회차 반려 → 재제출 → 평가 진행 중, 마감 임박 — teacherB)
-- ---------------------------------------------------------------------
SET @e1 = @now - INTERVAL 8 DAY, @e2 = @now - INTERVAL 12 DAY, @e3 = @now - INTERVAL 45 DAY, @e4 = @now - INTERVAL 14 DAY;
SET @done3 = @now - INTERVAL 26 DAY;   -- e3 수료 승인 시각 (수강 기간 안)

INSERT INTO enrollments (id, user_id, course_id, section_id, status, confirmed_at, expires_at, completed_at, ended_at) VALUES
  (1, 3, 1, 1,  'ACTIVE',    @e1, @e1 + INTERVAL 30 DAY, NULL,   NULL),
  (2, 3, 4, 4,  'ACTIVE',    @e2, @e2 + INTERVAL 30 DAY, NULL,   NULL),
  (3, 3, 8, 10, 'COMPLETED', @e3, @e3 + INTERVAL 30 DAY, @done3, @done3),
  (4, 4, 4, 5,  'ACTIVE',    @e4, @e4 + INTERVAL 30 DAY, NULL,   NULL);

-- ---------------------------------------------------------------------
-- D5. 대기열 (강의별 FIFO)
--   w1 learnerB c1 → CONFIRMED (e1로 자동 확정됨)
--   w2 learnerC c1 → WAITING  (s1 만석, 대기 1번)
-- ---------------------------------------------------------------------
INSERT INTO waitlist_entries (id, user_id, course_id, status, enrollment_id, confirmed_at, created_at) VALUES
  (1, 3, 1, 'CONFIRMED', 1,    @e1,  @now - INTERVAL 11 DAY),
  (2, 4, 1, 'WAITING',   NULL, NULL, @now - INTERVAL 3 DAY);

-- ---------------------------------------------------------------------
-- D6. 차시 진도 (차시 길이 600초, 60% = 360초 이상이면 완료)
-- ---------------------------------------------------------------------
INSERT INTO lesson_progresses (enrollment_id, lesson_id, course_id, played_sec, completed_at, last_position_sec)
SELECT p.enrollment_id, l.id, l.course_id, p.played_sec, p.completed_at, p.played_sec
FROM (
            SELECT 1 AS enrollment_id, 1 AS course_id, 1 AS lesson_no, 600.000 AS played_sec, @now - INTERVAL 7 DAY AS completed_at
  UNION ALL SELECT 1, 1, 2, 420.000, @now - INTERVAL 5 DAY
  UNION ALL SELECT 1, 1, 3, 120.000, NULL                      -- 3차시 학습 중 → 필수 영상 1개 남음
  UNION ALL SELECT 2, 4, 1, 600.000, @now - INTERVAL 11 DAY
  UNION ALL SELECT 2, 4, 2, 600.000, @now - INTERVAL 10 DAY
  UNION ALL SELECT 2, 4, 3, 600.000, @now - INTERVAL 9 DAY
  UNION ALL SELECT 3, 8, 1, 600.000, @now - INTERVAL 44 DAY
  UNION ALL SELECT 3, 8, 2, 600.000, @now - INTERVAL 43 DAY
  UNION ALL SELECT 3, 8, 3, 600.000, @now - INTERVAL 42 DAY
  UNION ALL SELECT 4, 4, 1, 600.000, @now - INTERVAL 13 DAY
  UNION ALL SELECT 4, 4, 2, 455.000, @now - INTERVAL 12 DAY
  UNION ALL SELECT 4, 4, 3, 600.000, @now - INTERVAL 12 DAY
) p
JOIN lessons l ON l.course_id = p.course_id AND l.lesson_no = p.lesson_no;

-- ---------------------------------------------------------------------
-- D7. 작품·버전·이미지 (7개)
--   learnerB: work 1 디저트 중급 최종 과제(승인·공개), work 2 일반 작품(공개),
--             work 3 디지털 드로잉 입문 최종 과제(평가 대기·비공개), work 6 일반 작품(공개 → 관리자 숨김)
--   learnerC: work 4 일반 작품(공개, 반려 사유를 참고 피드백으로 연결), work 5 일반 작품(공개),
--             work 7 디지털 드로잉 입문 최종 과제(1판 반려 → 2판 재제출·평가 진행 중, 비공개)
--   이미지 파일: uploads/seed/*.png (file.upload-dir 기준 상대 경로, 저장소에 포함).
--     키가 seed/ 로 시작하는 파일은 작품 이미지 교체·탈퇴 때 앱이 지우지 않으므로(FileStorage.deleteQuietly)
--     같은 작성자의 여러 칸이 한 파일을 함께 써도 안전하다 → 작품 6(재게시)·작품 7(1·2판)은 같은 그림을 다시 쓴다.
--   표시본(display_version_id)은 버전을 만든 뒤 연결한다 (같은 작품 버전만 FK 허용)
-- ---------------------------------------------------------------------
INSERT INTO works (id, author_id, kind, enrollment_id, hobby_id, level, visibility, reflection, published_at, created_at) VALUES
  (1, 3, 'FINAL',   3,    3, 2, 'PUBLIC',  '시트를 두 번 구워 보고 나서야 크림 온도를 맞출 수 있었다.',      @now - INTERVAL 25 DAY, @now - INTERVAL 33 DAY),
  (2, 3, 'GENERAL', NULL, 1, 1, 'PUBLIC',  '물결은 색을 세 겹 쌓아야 반짝임이 살아난다는 걸 알았다.',     @now - INTERVAL 6 DAY,  @now - INTERVAL 6 DAY),
  (3, 3, 'FINAL',   2,    2, 1, 'PRIVATE', NULL, NULL, @now - INTERVAL 2 DAY),
  (4, 4, 'GENERAL', NULL, 2, 1, 'PUBLIC',  '반려 피드백대로 선화·채색·그림자 레이어를 나눠 다시 그려 봤다.', @now - INTERVAL 7 DAY,  @now - INTERVAL 7 DAY),
  (5, 4, 'GENERAL', NULL, 1, 1, 'PUBLIC',  '도자기 무늬를 따라 그리느라 시간이 가장 오래 걸렸다.',         @now - INTERVAL 4 DAY,  @now - INTERVAL 4 DAY),
  (6, 3, 'GENERAL', NULL, 1, 1, 'PUBLIC',  '액자에 넣을 원화로 다시 정리했다.',                            @now - INTERVAL 9 DAY,  @now - INTERVAL 9 DAY),
  (7, 4, 'FINAL',   4,    2, 1, 'PRIVATE', NULL, NULL, @now - INTERVAL 11 DAY);

INSERT INTO work_versions (id, work_id, version_no, title, description, created_at) VALUES
  (1, 1, 1, '딸기 생크림 케이크',      '시트 두 장 사이에 생크림과 딸기를 채우고, 윗면은 크림을 짜서 베리로 장식했습니다.', @now - INTERVAL 33 DAY),
  (2, 2, 1, '항구 마을 풍경',          '색연필로 하늘과 바다를 겹쳐 칠하고, 돛은 종이의 흰 바탕을 남겨 표현했습니다.',       @now - INTERVAL 6 DAY),
  (3, 3, 1, '폭포 위의 성과 용',       '배경·성·용·폭포를 레이어로 나눠 그리고 금빛 액자 테두리를 더했습니다.',             @now - INTERVAL 2 DAY),
  (4, 4, 1, '구름 위 정원',            '멀리 있는 구름과 나무는 채도를 낮추고, 정자와 꽃은 밝게 올려 거리감을 냈습니다.',   @now - INTERVAL 7 DAY),
  (5, 5, 1, '붓과 소묘가 있는 정물',   '붓꽂이·인물 소묘·책을 한 화면에 두고 재질마다 다른 질감을 연습했습니다.',           @now - INTERVAL 4 DAY),
  (6, 6, 1, '항구 마을 풍경 (원화 판매)', '같은 그림의 원화를 판매합니다. 구매 문의는 외부 판매 링크로 주세요.',            @now - INTERVAL 9 DAY),
  (7, 7, 1, '구름 위 정원',            '정자와 구름, 나무를 한 레이어에 모두 그렸습니다.',                                   @now - INTERVAL 11 DAY),
  (8, 7, 2, '구름 위 정원',            '반려 사유대로 선화·채색·그림자 레이어를 나눠 다시 제출합니다.',                      @now - INTERVAL 6 DAY);

UPDATE works SET display_version_id = id WHERE id BETWEEN 1 AND 6;   -- 작품 1~6은 버전 id = 작품 id
UPDATE works SET display_version_id = 8  WHERE id = 7;               -- 작품 7은 재제출본(2판)

-- 칸 = 작품 버전에 붙은 이미지 1장. 썸네일(thumbnail_key)은 앱이 만들지 않아 NULL — 목록 카드는 표시본의 1번 이미지를 쓴다.
INSERT INTO work_files (work_version_id, sort_order, original_key, original_name, mime_type, source_size_bytes, stored_size_bytes) VALUES
  (1, 1, 'seed/strawberry_cake.png',            'strawberry_cake.png',            'image/png', 2796342, 2796342),
  (2, 1, 'seed/harbor_colored_pencil.png',      'harbor_colored_pencil.png',      'image/png', 2488437, 2488437),
  (3, 1, 'seed/castle_dragon_illustration.png', 'castle_dragon_illustration.png', 'image/png', 4169926, 4169926),
  (4, 1, 'seed/cloud_garden_illustration.png',  'cloud_garden_illustration.png',  'image/png', 3059427, 3059427),
  (5, 1, 'seed/still_life_sketch.png',          'still_life_sketch.png',          'image/png', 2475387, 2475387),
  (6, 1, 'seed/harbor_colored_pencil.png',      'harbor_colored_pencil.png',      'image/png', 2488437, 2488437),
  (7, 1, 'seed/cloud_garden_illustration.png',  'cloud_garden_illustration.png',  'image/png', 3059427, 3059427),
  (8, 1, 'seed/cloud_garden_illustration.png',  'cloud_garden_illustration.png',  'image/png', 3059427, 3059427);

-- ---------------------------------------------------------------------
-- D8. 평가 회차 (due_at = submitted_at + 7일 CHECK, 확정 피드백·반려 사유 50자 이상)
--   r1 e3 APPROVED  (teacherB) → learnerB 디저트 중급 수료
--   r2 e2 WAITING   (teacherA 평가 대기) → 강사A가 learnerB 최종 과제 평가
--   r3 e4 REJECTED  (teacherB, 1회차)
--   r4 e4 IN_REVIEW (teacherB 시작 + 초안, 2회차, 마감 1일 남음 → 마감 임박 경고)
-- ---------------------------------------------------------------------
SET @s1 = @now - INTERVAL 32 DAY, @s2 = @now - INTERVAL 2 DAY, @s3 = @now - INTERVAL 11 DAY, @s4 = @now - INTERVAL 6 DAY;

INSERT INTO review_rounds (id, enrollment_id, work_version_id, round_no, status, submitted_at, due_at,
                           started_at, started_by, draft_body, draft_author_id, draft_updated_at,
                           feedback_body, reviewer_id, finalized_at) VALUES
  (1, 3, 1, 1, 'APPROVED',  @s1, @s1 + INTERVAL 7 DAY, @now - INTERVAL 30 DAY, 7, NULL, NULL, NULL,
   '시트의 결이 고르고 크림을 바른 두께도 일정해서 단면이 아주 깔끔합니다. 딸기 배치 간격만 조금 더 넓히면 자를 때 모양이 더 잘 살아납니다. 중급 과정 수료를 축하합니다.', 7, @done3),
  (2, 2, 3, 1, 'WAITING',   @s2, @s2 + INTERVAL 7 DAY, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
  (3, 4, 7, 1, 'REJECTED',  @s3, @s3 + INTERVAL 7 DAY, @now - INTERVAL 10 DAY, 7, NULL, NULL, NULL,
   '기초 과제 기준인 레이어 분리가 아직 보이지 않습니다. 선화, 기본 채색, 그림자를 각각 다른 레이어로 나누고 그림자 레이어에 곱하기 혼합을 적용한 뒤 다시 제출해 주세요.', 7, @now - INTERVAL 9 DAY),
  (4, 4, 8, 2, 'IN_REVIEW', @s4, @s4 + INTERVAL 7 DAY, @now - INTERVAL 1 DAY, 7,
   '레이어 구분이 깔끔해졌습니다. 그림자 레이어 혼합 모드를', 7, @now - INTERVAL 1 DAY, NULL, NULL, NULL);

-- 일반 작품 4가 반려 사유(r3)를 참고 피드백으로 연결 (D-17·D-47)
UPDATE works SET reference_review_id = 3 WHERE id = 4;

-- 관리자 숨김 (work 6 「항구 마을 풍경 (원화 판매)」)
UPDATE works SET admin_hidden = TRUE, hidden_reason = '작품 설명에 외부 판매 링크가 포함되어 공개 노출을 제한했습니다.',
                 hidden_at = @now - INTERVAL 8 DAY, hidden_by = 1
WHERE id = 6;

-- ---------------------------------------------------------------------
-- D9. 좋아요·댓글 (P1)
-- ---------------------------------------------------------------------
INSERT INTO work_likes (work_id, user_id, created_at) VALUES
  (2, 4, @now - INTERVAL 5 DAY),
  (2, 3, @now - INTERVAL 5 DAY),    -- 본인 작품 좋아요 허용
  (4, 3, @now - INTERVAL 6 DAY);

INSERT INTO work_comments (id, work_id, parent_id, author_id, body, created_at) VALUES
  (1, 2, NULL, 6, '돛을 종이의 흰 바탕으로 남긴 표현이 인상적이에요. 공개해 주셔서 고맙습니다.', @now - INTERVAL 5 DAY),
  (2, 2, 1,    3, '감사합니다! 다음 작품에서 배경도 신경 써 볼게요.',             @now - INTERVAL 4 DAY),
  (3, 5, NULL, 6, '소묘 선을 살리면서 도자기 무늬를 차분하게 올린 점이 좋아요. 책 모서리의 그림자만 조금 더 진하게 눌러 보세요.', @now - INTERVAL 3 DAY);
  -- 댓글 자격(CommentPolicy): 강사는 작품 취미 권한, 학습자는 같은 취미의 작품 수준 이상 배지 → 색연필화 작품 5에는 teacherA(색연필화 권한)가 남김

-- ---------------------------------------------------------------------
-- D10. 1:1 문의 (P1)
--   t1 learnerB e3(디저트 중급) → teacherB 답변 완료 / t2 learnerB e1(색연필화 입문) → teacherA 미답변
-- ---------------------------------------------------------------------
INSERT INTO inquiry_threads (id, enrollment_id, learner_id, course_id, title, status, last_message_at, created_at) VALUES
  (1, 3, 3, 8, '크림 온도 질문', 'ANSWERED', @now - INTERVAL 38 DAY,  @now - INTERVAL 39 DAY),
  (2, 1, 3, 1, '준비물 질문',    'OPEN',     @now - INTERVAL 12 HOUR, @now - INTERVAL 12 HOUR);

INSERT INTO inquiry_messages (thread_id, author_id, body, created_at) VALUES
  (1, 3, '2차시에서 생크림은 몇 도까지 식힌 뒤에 발라야 하나요?',     @now - INTERVAL 39 DAY),
  (1, 7, '손등에 대었을 때 미지근한 정도, 25도 안팎이면 충분합니다.', @now - INTERVAL 38 DAY),
  (2, 3, '색연필은 수채 색연필이어도 괜찮나요?',                       @now - INTERVAL 12 HOUR);

-- ---------------------------------------------------------------------
-- D11. 알림 (dedupe_key = 종류:대상id, 사용자별 UNIQUE)
-- ---------------------------------------------------------------------
INSERT INTO notifications (user_id, kind, title, body, enrollment_id, review_round_id, work_id, comment_id, inquiry_thread_id, section_id, dedupe_key, read_at, created_at) VALUES
  -- learnerB
  (3, 'INQUIRY_ANSWERED',    '문의 답변',  '디저트 응용 문의에 강사 B가 답변했어요.',                         NULL, NULL, NULL, NULL, 1, NULL, 'INQUIRY_ANSWERED:1',    @now - INTERVAL 37 DAY, @now - INTERVAL 38 DAY),
  (3, 'REVIEW_APPROVED',     '평가 승인',  '디저트 응용 과제가 승인되었습니다.',                              NULL, 1, NULL, NULL, NULL, NULL, 'REVIEW_APPROVED:1',     @done3, @done3),
  (3, 'COURSE_COMPLETED',    '수료',       '디저트 응용을 수료했어요. 중급 배지를 받았습니다.',               3, NULL, NULL, NULL, NULL, NULL, 'COURSE_COMPLETED:3',    @done3, @done3),
  (3, 'WAITLIST_CONFIRMED',  '수강 확정',  '색연필화 기초 · 입문 대기 순서가 되어 수강이 자동 확정됐어요.',   1, NULL, NULL, NULL, NULL, NULL, 'WAITLIST_CONFIRMED:1', @now - INTERVAL 7 DAY, @e1),
  (3, 'WORK_HIDDEN',         '작품 숨김',  '「항구 마을 풍경 (원화 판매)」의 공개 노출이 제한되었습니다.',              NULL, NULL, 6, NULL, NULL, NULL, 'WORK_HIDDEN:6',         NULL, @now - INTERVAL 8 DAY),
  (3, 'COMMENT_ON_MY_WORK',  '새 댓글',    '내 작품에 강사A님이 댓글을 남겼어요.',                            NULL, NULL, NULL, 1, NULL, NULL, 'COMMENT_ON_MY_WORK:1',  NULL, @now - INTERVAL 5 DAY),
  -- learnerC
  (4, 'REVIEW_REJECTED',     '평가 반려',  '디지털 드로잉 기초 과제가 반려되었습니다. 사유를 확인하고 재제출하세요.', NULL, 3, NULL, NULL, NULL, NULL, 'REVIEW_REJECTED:3', @now - INTERVAL 8 DAY, @now - INTERVAL 9 DAY),
  (4, 'COMMENT_ON_MY_WORK',  '새 댓글',    '내 작품에 강사A님이 댓글을 남겼어요.',                       NULL, NULL, NULL, 3, NULL, NULL, 'COMMENT_ON_MY_WORK:3',  NULL, @now - INTERVAL 3 DAY),
  -- teacherA
  (6, 'SECTION_ASSIGNED',    '분반 배정',  '디지털 드로잉 기초 · 입문 A분반 담당으로 배정되었어요.',          NULL, NULL, NULL, NULL, NULL, 4, 'SECTION_ASSIGNED:4',    @now - INTERVAL 80 DAY, @now - INTERVAL 85 DAY),
  (6, 'REVIEW_SUBMITTED',    '과제 제출',  '디지털 드로잉 기초 최종 과제가 제출되었어요.',                    NULL, 2, NULL, NULL, NULL, NULL, 'REVIEW_SUBMITTED:2',    NULL, @s2),
  (6, 'INQUIRY_RECEIVED',    '새 문의',    '색연필화 기초 수강생 문의가 새로 들어왔어요.',                    NULL, NULL, NULL, NULL, 2, NULL, 'INQUIRY_RECEIVED:2',    NULL, @now - INTERVAL 12 HOUR),
  -- teacherB
  (7, 'SECTION_ASSIGNED',    '분반 배정',  '디지털 드로잉 기초 · 입문 B분반 담당으로 배정되었어요.',          NULL, NULL, NULL, NULL, NULL, 5, 'SECTION_ASSIGNED:5',    @now - INTERVAL 80 DAY, @now - INTERVAL 85 DAY),
  (7, 'REVIEW_SUBMITTED',    '과제 제출',  '디지털 드로잉 기초 최종 과제가 다시 제출되었어요.',               NULL, 4, NULL, NULL, NULL, NULL, 'REVIEW_SUBMITTED:4',    @now - INTERVAL 1 DAY, @s4);

-- ---------------------------------------------------------------------
-- 작품 숨김 조치 이력 (#20) — 위 "관리자 숨김"(work 6)과 같은 사건
-- ---------------------------------------------------------------------
INSERT INTO work_moderation_logs (work_id, action, reason, admin_id, created_at) VALUES
  (6, 'HIDE', '작품 설명에 외부 판매 링크가 포함되어 공개 노출을 제한했습니다.', 1, @now - INTERVAL 8 DAY);
