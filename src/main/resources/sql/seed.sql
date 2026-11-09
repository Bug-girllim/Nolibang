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

-- 관리자 1명: 초기 비밀번호 Admin!2026 (BCrypt)
-- [11-06 팀 결정] 첫 로그인 비밀번호 변경(C-09)은 강사 전용 화면이므로 관리자는 must_change_password = FALSE.
--   (TRUE면 관리자가 로그인 직후 막힘) 운영 배포 전 비밀번호는 직접 바꾼다.
INSERT INTO users (login_id, password_hash, real_name, email, role, status, email_verified_at, must_change_password)
VALUES ('admin', '$2a$10$5PybLmgjUf5RcCF2cWWla.COxExjHWKpchzPLhA1jYCLmVgnlaigO',
        '운영자', 'admin@nolibang.local', 'ADMIN', 'ACTIVE', UTC_TIMESTAMP(6), FALSE);

INSERT INTO categories (id, code, name, state, sort_order) VALUES
  (1, 'art',    '미술·드로잉', 'OPERATING', 1),
  (2, 'cook',   '요리·베이킹', 'OPERATING', 2),
  (3, 'craft',  '공예',        'PREPARING', 3),
  (4, 'photo',  '사진·영상',   'PRIVATE',   4);

INSERT INTO hobbies (id, category_id, code, name, description, state, sort_order) VALUES
  (1, 1, 'colored-pencil', '색연필화',      'TODO: 취미 소개', 'OPERATING', 1),
  (2, 1, 'digital-drawing','디지털 드로잉', 'TODO: 취미 소개', 'OPERATING', 2),
  (3, 2, 'dessert',        '디저트 만들기', 'TODO: 취미 소개', 'OPERATING', 1),
  (4, 3, 'woodwork',       '목공',          'TODO: 취미 소개', 'PREPARING', 1),
  (5, 3, 'leather',        '가죽공예',      'TODO: 취미 소개', 'PREPARING', 2),
  (6, 3, 'ceramics',       '도자기',        'TODO: 취미 소개', 'PREPARING', 3),
  (7, 3, 'ring',           '반지공예',      'TODO: 취미 소개', 'PREPARING', 4),
  -- [추가] 사진·영상 하위 취미 2개 (04-features F-02: 비공개, 관리자만 조회)
  (8, 4, 'photo-shooting', '사진 촬영',     'TODO: 취미 소개', 'PRIVATE',   1),
  (9, 4, 'video-editing',  '영상 편집',     'TODO: 취미 소개', 'PRIVATE',   2);

INSERT INTO courses (id, hobby_id, level, title, description, supplies_text, assignment_title, assignment_description) VALUES
  (1, 1, 1, '색연필화 입문',      'TODO: 강의 소개', 'TODO: 준비물', '색연필화 입문 최종 과제',      'TODO: 과제 안내'),
  (2, 1, 2, '색연필화 중급',      'TODO: 강의 소개', 'TODO: 준비물', '색연필화 중급 최종 과제',      'TODO: 과제 안내'),
  (3, 1, 3, '색연필화 고급',      'TODO: 강의 소개', 'TODO: 준비물', '색연필화 고급 최종 과제',      'TODO: 과제 안내'),
  (4, 2, 1, '디지털 드로잉 입문', 'TODO: 강의 소개', 'TODO: 준비물', '디지털 드로잉 입문 최종 과제', 'TODO: 과제 안내'),
  (5, 2, 2, '디지털 드로잉 중급', 'TODO: 강의 소개', 'TODO: 준비물', '디지털 드로잉 중급 최종 과제', 'TODO: 과제 안내'),
  (6, 2, 3, '디지털 드로잉 고급', 'TODO: 강의 소개', 'TODO: 준비물', '디지털 드로잉 고급 최종 과제', 'TODO: 과제 안내'),
  (7, 3, 1, '디저트 만들기 입문', 'TODO: 강의 소개', 'TODO: 준비물', '디저트 만들기 입문 최종 과제', 'TODO: 과제 안내'),
  (8, 3, 2, '디저트 만들기 중급', 'TODO: 강의 소개', 'TODO: 준비물', '디저트 만들기 중급 최종 과제', 'TODO: 과제 안내'),
  (9, 3, 3, '디저트 만들기 고급', 'TODO: 강의 소개', 'TODO: 준비물', '디저트 만들기 고급 최종 과제', 'TODO: 과제 안내');

-- 27차시, 차시당 영상 1개
INSERT INTO lessons (course_id, lesson_no, title, learning_objective, video_key, duration_sec)
SELECT c.id, n.no,
       CONCAT(c.title, ' ', n.no, '차시'),
       'TODO: 학습 목표',
       CONCAT('videos/course-', c.id, '/lesson-', n.no, '.mp4'),
       600.000  -- TODO: 실제 영상 길이(초)
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
-- [추가] 시연·개발용 데이터 (HTML C-02 시연 계정 + 이슈 #03 테스트 데이터)
--   비밀번호: 시연 계정 모두 1234 (HTML C-02 표기, BCrypt). 가입 규칙(8~16자)은 서버 입력 검사용이라 시드에는 적용하지 않는다.
--   관리자 admin은 위 정본 시드 값(Admin!2026) 그대로.
--   시각은 적재 시점(@now) 기준 상대값 → 언제 적재해도 30일·7일 기한이 살아 있다.
--   운영 배포 시에는 이 구간을 빼고 적재한다.
-- =====================================================================
SET @now = UTC_TIMESTAMP(6);
SET @pw  = '$2a$10$Di2oihWbxzHAUsFVWjsc7.Hjzu8bj6AMPlzZ7PdooeYnk2wHX.hUu';

-- ---------------------------------------------------------------------
-- D1. 계정 (id 2~11)
--   learner   : 메인 시연 학습자 (색연필화 입문 배지 보유, 수강·대기·반려·작품 숨김 상태 모두 보유)
--   learner2  : 평가 진행 중 과제 보유 (디지털 드로잉 기초 — 분반 만석)
--   learner3  : 대기열 대기 중 + 1:1 문의 미답변
--   newbie    : 이메일 미인증 (로그인 불가 시연)
--   teacherA  : 강사 A — 색연필화·디지털 드로잉
--   teacherB  : 강사 B — 디저트 만들기·색연필화
--   teacherC  : 비활성 강사 (로그인 불가 시연)
--   teacherD  : 임시 비밀번호 강사 (첫 로그인 C-09 시연)
-- ---------------------------------------------------------------------
INSERT INTO users (id, login_id, password_hash, real_name, email, nickname, role, status, email_verified_at, must_change_password, first_login_at) VALUES
  (2,  'learner',  @pw, '김학습', 'learner@nolibang.local',  '차분한수달',   'USER',       'ACTIVE',        @now - INTERVAL 40 DAY, FALSE, @now - INTERVAL 40 DAY),
  (3,  'learner2', @pw, '이배움', 'learner2@nolibang.local', '느긋한고양이', 'USER',       'ACTIVE',        @now - INTERVAL 20 DAY, FALSE, @now - INTERVAL 20 DAY),
  (4,  'learner3', @pw, '박연습', 'learner3@nolibang.local', '용감한펭귄',   'USER',       'ACTIVE',        @now - INTERVAL 5 DAY,  FALSE, @now - INTERVAL 5 DAY),
  (5,  'newbie',   @pw, '최신규', 'newbie@nolibang.local',   '수줍은다람쥐', 'USER',       'PENDING_EMAIL', NULL,                   FALSE, NULL),
  (6,  'teacherA', @pw, '정강사', 'teachera@nolibang.local', '강사A',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 60 DAY, FALSE, @now - INTERVAL 60 DAY),
  (7,  'teacherB', @pw, '한강사', 'teacherb@nolibang.local', '강사B',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 60 DAY, FALSE, @now - INTERVAL 60 DAY),
  (8,  'teacherC', @pw, '오강사', 'teacherc@nolibang.local', '강사C',        'INSTRUCTOR', 'INACTIVE',      @now - INTERVAL 60 DAY, FALSE, @now - INTERVAL 50 DAY),
  (9,  'teacherD', @pw, '윤강사', 'teacherd@nolibang.local', '강사D',        'INSTRUCTOR', 'ACTIVE',        @now - INTERVAL 1 DAY,  TRUE,  NULL);

-- ---------------------------------------------------------------------
-- D2. 강사 취미 권한
-- ---------------------------------------------------------------------
INSERT INTO instructor_hobby_grants (instructor_id, hobby_id) VALUES
  (6, 1), (6, 2),      -- teacherA: 색연필화, 디지털 드로잉
  (7, 3), (7, 1),      -- teacherB: 디저트 만들기, 색연필화
  (8, 3),              -- teacherC(비활성): 디저트 만들기
  (9, 2);              -- teacherD: 디지털 드로잉

-- ---------------------------------------------------------------------
-- D3. 분반 (9강의 모두 신청 가능하도록 최소 1개, 일부 상태 시연용)
--   c4 A분반 정원 1 = 만석 → 대기열 시연 / c6 B분반 담당 공백 → 배정 제외 시연
-- ---------------------------------------------------------------------
INSERT INTO course_sections (id, course_id, section_no, name, instructor_id, capacity) VALUES
  (1,  1, 1, 'A분반', 6,    5),
  (2,  1, 2, 'B분반', 7,    5),
  (3,  2, 1, 'A분반', 6,    5),
  (4,  3, 1, 'A분반', 6,    5),
  (5,  4, 1, 'A분반', 6,    1),
  (6,  5, 1, 'A분반', 9,    5),
  (7,  6, 1, 'A분반', 6,    5),
  (8,  6, 2, 'B분반', NULL, 5),
  (9,  7, 1, 'A분반', 7,   10),
  (10, 8, 1, 'A분반', 7,   10),
  (11, 9, 1, 'A분반', 7,   10);

-- ---------------------------------------------------------------------
-- D4. 수강 (expires_at = confirmed_at + 30일 CHECK)
--   e1 learner  c1 색연필화 기초  COMPLETED (승인 → 입문 배지)
--   e2 learner  c2 색연필화 응용  ACTIVE (대기 → 자동 확정)
--   e3 learner  c3 색연필화 심화  ACTIVE (1회차 반려 → 재제출 가능)
--   e4 learner  c7 디저트 기초    ACTIVE (평가 대기)
--   e5 learner2 c4 디지털 기초    ACTIVE (평가 진행 중, 마감 임박)
--   e6 learner3 c1 색연필화 기초  ACTIVE (학습 시작 전)
-- ---------------------------------------------------------------------
SET @e1 = @now - INTERVAL 25 DAY, @e2 = @now - INTERVAL 3 DAY, @e3 = @now - INTERVAL 10 DAY,
    @e4 = @now - INTERVAL 5 DAY,  @e5 = @now - INTERVAL 12 DAY, @e6 = @now - INTERVAL 1 DAY;
SET @done1 = @now - INTERVAL 18 DAY;   -- e1 수료 승인 시각

INSERT INTO enrollments (id, user_id, course_id, section_id, status, confirmed_at, expires_at, completed_at, ended_at) VALUES
  (1, 2, 1, 1, 'COMPLETED', @e1, @e1 + INTERVAL 30 DAY, @done1, @done1),
  (2, 2, 2, 3, 'ACTIVE',    @e2, @e2 + INTERVAL 30 DAY, NULL,   NULL),
  (3, 2, 3, 4, 'ACTIVE',    @e3, @e3 + INTERVAL 30 DAY, NULL,   NULL),
  (4, 2, 7, 9, 'ACTIVE',    @e4, @e4 + INTERVAL 30 DAY, NULL,   NULL),
  (5, 3, 4, 5, 'ACTIVE',    @e5, @e5 + INTERVAL 30 DAY, NULL,   NULL),
  (6, 4, 1, 1, 'ACTIVE',    @e6, @e6 + INTERVAL 30 DAY, NULL,   NULL);

-- ---------------------------------------------------------------------
-- D5. 대기열 (강의별 FIFO)
--   w1 learner  c2 → CONFIRMED (e2로 자동 확정됨)
--   w2 learner3 c4 → WAITING  (c4 A분반 만석, 대기 1번)
-- ---------------------------------------------------------------------
INSERT INTO waitlist_entries (id, user_id, course_id, status, enrollment_id, confirmed_at, created_at) VALUES
  (1, 2, 2, 'CONFIRMED', 2,    @e2,  @now - INTERVAL 8 DAY),
  (2, 4, 4, 'WAITING',   NULL, NULL, @now - INTERVAL 2 DAY);

-- ---------------------------------------------------------------------
-- D6. 차시 진도 (차시 길이 600초, 60% = 360초 이상이면 완료)
-- ---------------------------------------------------------------------
INSERT INTO lesson_progresses (enrollment_id, lesson_id, course_id, played_sec, completed_at, last_position_sec)
SELECT p.enrollment_id, l.id, l.course_id, p.played_sec, p.completed_at, p.played_sec
FROM (
            SELECT 1 AS enrollment_id, 1 AS course_id, 1 AS lesson_no, 420.000 AS played_sec, @now - INTERVAL 24 DAY AS completed_at
  UNION ALL SELECT 1, 1, 2, 455.000, @now - INTERVAL 23 DAY
  UNION ALL SELECT 1, 1, 3, 600.000, @now - INTERVAL 22 DAY
  UNION ALL SELECT 2, 2, 1, 380.000, @now - INTERVAL 2 DAY
  UNION ALL SELECT 2, 2, 2, 150.000, NULL                      -- 2차시 학습 중
  UNION ALL SELECT 3, 3, 1, 400.000, @now - INTERVAL 9 DAY
  UNION ALL SELECT 3, 3, 2, 410.000, @now - INTERVAL 8 DAY
  UNION ALL SELECT 3, 3, 3, 500.000, @now - INTERVAL 7 DAY
  UNION ALL SELECT 4, 7, 1, 600.000, @now - INTERVAL 4 DAY
  UNION ALL SELECT 4, 7, 2, 600.000, @now - INTERVAL 3 DAY
  UNION ALL SELECT 4, 7, 3, 365.000, @now - INTERVAL 3 DAY
  UNION ALL SELECT 5, 4, 1, 600.000, @now - INTERVAL 10 DAY
  UNION ALL SELECT 5, 4, 2, 600.000, @now - INTERVAL 9 DAY
  UNION ALL SELECT 5, 4, 3, 600.000, @now - INTERVAL 8 DAY
) p
JOIN lessons l ON l.course_id = p.course_id AND l.lesson_no = p.lesson_no;

-- ---------------------------------------------------------------------
-- D7. 작품·버전·이미지
--   최종 과제(FINAL): work 1(e1 승인, 공개), 2(e3 반려), 3(e4 평가 대기), 4(e5 평가 진행)
--   일반 작품(GENERAL): work 5 「나의 케이크」 공개 → 관리자 숨김, work 6 색연필 연습(반려 사유를 참고 피드백으로 연결)
--   이미지 파일: lms-practice/uploads/seed/*.png (file.upload-dir 기준 상대 경로)
--   표시본(display_version_id)은 버전을 만든 뒤 연결한다 (같은 작품 버전만 FK 허용)
-- ---------------------------------------------------------------------
INSERT INTO works (id, author_id, kind, enrollment_id, hobby_id, level, visibility, reflection, published_at, created_at) VALUES
  (1, 2, 'FINAL',   1,    1, 1, 'PUBLIC',  '명암 단계를 처음으로 다섯 단계까지 나눠 봤다.', @now - INTERVAL 17 DAY, @now - INTERVAL 20 DAY),
  (2, 2, 'FINAL',   3,    1, 3, 'PRIVATE', NULL, NULL, @now - INTERVAL 6 DAY),
  (3, 2, 'FINAL',   4,    3, 1, 'PRIVATE', NULL, NULL, @now - INTERVAL 2 DAY),
  (4, 3, 'FINAL',   5,    2, 1, 'PRIVATE', NULL, NULL, @now - INTERVAL 6 DAY),
  (5, 2, 'GENERAL', NULL, 3, 1, 'PUBLIC',  '크림 아이싱이 아직 고르지 않다.', @now - INTERVAL 7 DAY, @now - INTERVAL 7 DAY),
  (6, 2, 'GENERAL', NULL, 1, 1, 'PRIVATE', '반려 사유대로 배경 명암을 먼저 깔아 봤다.', NULL, @now - INTERVAL 3 DAY);

INSERT INTO work_versions (id, work_id, version_no, title, description, created_at) VALUES
  (1, 1, 1, '색연필화 기초 — 최종 과제', '사과와 유리컵 정물. 빛 방향을 왼쪽 위로 두고 그렸습니다.', @now - INTERVAL 20 DAY),
  (2, 2, 1, '색연필화 심화 — 최종 과제', '풍경 스케치에 색을 올렸습니다.',                             @now - INTERVAL 6 DAY),
  (3, 3, 1, '디저트 기초 — 최종 과제',   '기본 마들렌 12개를 구웠습니다.',                             @now - INTERVAL 2 DAY),
  (4, 4, 1, '디지털 드로잉 기초 — 최종 과제', '레이어 3개로 캐릭터 채색을 했습니다.',                  @now - INTERVAL 6 DAY),
  (5, 5, 1, '나의 케이크',               '생일 케이크를 만들었어요.',                                  @now - INTERVAL 7 DAY),
  (6, 6, 1, '색연필 정물 연습',          '배경 명암 연습입니다.',                                      @now - INTERVAL 3 DAY);

UPDATE works SET display_version_id = id WHERE id BETWEEN 1 AND 6;   -- 각 작품의 버전 id = 작품 id

INSERT INTO work_files (work_version_id, sort_order, original_key, original_name, mime_type, source_size_bytes, stored_size_bytes) VALUES
  (1, 1, 'seed/work1-1.png', 'apple.png',     'image/png', 3364, 3364),
  (1, 2, 'seed/work1-2.png', 'glass.png',     'image/png', 3414, 3414),
  (2, 1, 'seed/work2-1.png', 'landscape.png', 'image/png', 3722, 3722),
  (3, 1, 'seed/work3-1.png', 'madeleine.png', 'image/png', 3544, 3544),
  (4, 1, 'seed/work4-1.png', 'character.png', 'image/png', 3529, 3529),
  (5, 1, 'seed/work5-1.png', 'cake.png',      'image/png', 3407, 3407),
  (6, 1, 'seed/work6-1.png', 'still.png',     'image/png', 3313, 3313);

-- ---------------------------------------------------------------------
-- D8. 평가 회차 (due_at = submitted_at + 7일 CHECK, 확정 피드백·반려 사유 50자 이상)
--   r1 e1 APPROVED  (teacherA)
--   r2 e3 REJECTED  (teacherA) → 기간 내라 재제출 가능
--   r3 e4 WAITING   (teacherB 평가 대기)
--   r4 e5 IN_REVIEW (teacherA 시작 + 초안, 마감 1일 남음 → 마감 임박 경고)
-- ---------------------------------------------------------------------
SET @s1 = @now - INTERVAL 20 DAY, @s2 = @now - INTERVAL 6 DAY, @s3 = @now - INTERVAL 2 DAY, @s4 = @now - INTERVAL 6 DAY;

INSERT INTO review_rounds (id, enrollment_id, work_version_id, round_no, status, submitted_at, due_at,
                           started_at, started_by, draft_body, draft_author_id, draft_updated_at,
                           feedback_body, reviewer_id, finalized_at) VALUES
  (1, 1, 1, 1, 'APPROVED',  @s1, @s1 + INTERVAL 7 DAY, @now - INTERVAL 19 DAY, 6, NULL, NULL, NULL,
   '사과의 둥근 면을 따라 명암을 쌓은 점이 아주 좋습니다. 유리컵의 반사광은 조금 더 밝게 남기면 투명한 느낌이 살아납니다. 다음에는 배경 색을 한 단계 어둡게 깔아 보세요.', 6, @done1),
  (2, 3, 2, 1, 'REJECTED',  @s2, @s2 + INTERVAL 7 DAY, @now - INTERVAL 5 DAY, 6, NULL, NULL, NULL,
   '심화 과제 기준인 원근에 따른 채도 변화가 아직 보이지 않습니다. 먼 산은 채도를 낮추고 가까운 나무는 진하게 올려 거리감을 표현한 뒤 다시 제출해 주세요.', 6, @now - INTERVAL 4 DAY),
  (3, 4, 3, 1, 'WAITING',   @s3, @s3 + INTERVAL 7 DAY, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
  (4, 5, 4, 1, 'IN_REVIEW', @s4, @s4 + INTERVAL 7 DAY, @now - INTERVAL 1 DAY, 6,
   '레이어 구분이 깔끔합니다. 그림자 레이어 혼합 모드를', 6, @now - INTERVAL 1 DAY, NULL, NULL, NULL);

-- 일반 작품 6이 반려 사유(r2)를 참고 피드백으로 연결 (D-17·D-47)
UPDATE works SET reference_review_id = 2 WHERE id = 6;

-- 관리자 숨김 (work 5 「나의 케이크」)
UPDATE works SET admin_hidden = TRUE, hidden_reason = '작품 설명에 외부 판매 링크가 포함되어 공개 노출을 제한했습니다.',
                 hidden_at = @now - INTERVAL 6 DAY, hidden_by = 1
WHERE id = 5;

-- ---------------------------------------------------------------------
-- D9. 좋아요·댓글 (P1)
-- ---------------------------------------------------------------------
INSERT INTO work_likes (work_id, user_id, created_at) VALUES
  (1, 3, @now - INTERVAL 16 DAY),
  (1, 2, @now - INTERVAL 16 DAY);   -- 본인 작품 좋아요 허용

INSERT INTO work_comments (id, work_id, parent_id, author_id, body, created_at) VALUES
  (1, 1, NULL, 6, '유리컵 반사광 표현이 인상적이에요. 공개해 주셔서 고맙습니다.', @now - INTERVAL 16 DAY),
  (2, 1, 1,    2, '감사합니다! 다음 작품에서 배경도 신경 써 볼게요.',             @now - INTERVAL 15 DAY);

-- ---------------------------------------------------------------------
-- D10. 1:1 문의 (P1)
--   t1 learner  e1 → teacherA 답변 완료 / t2 learner3 e6 → 미답변
-- ---------------------------------------------------------------------
INSERT INTO inquiry_threads (id, enrollment_id, learner_id, course_id, title, status, last_message_at, created_at) VALUES
  (1, 1, 2, 1, '명암 단계 질문', 'ANSWERED', @now - INTERVAL 21 DAY, @now - INTERVAL 22 DAY),
  (2, 6, 4, 1, '준비물 질문',    'OPEN',     @now - INTERVAL 12 HOUR, @now - INTERVAL 12 HOUR);

INSERT INTO inquiry_messages (thread_id, author_id, body, created_at) VALUES
  (1, 2, '2차시에서 명암을 몇 단계로 나누는 게 좋을까요?',             @now - INTERVAL 22 DAY),
  (1, 6, '입문 단계에서는 다섯 단계로 나눠 연습하는 걸 추천합니다.', @now - INTERVAL 21 DAY),
  (2, 4, '색연필은 수채 색연필이어도 괜찮나요?',                       @now - INTERVAL 12 HOUR);

-- ---------------------------------------------------------------------
-- D11. 알림 (dedupe_key = 종류:대상id, 사용자별 UNIQUE)
-- ---------------------------------------------------------------------
INSERT INTO notifications (user_id, kind, title, body, enrollment_id, review_round_id, work_id, comment_id, inquiry_thread_id, section_id, dedupe_key, read_at, created_at) VALUES
  -- learner
  (2, 'REVIEW_APPROVED',     '평가 승인',  '색연필화 기초 과제가 승인되었습니다.',                         NULL, 1, NULL, NULL, NULL, NULL, 'REVIEW_APPROVED:1',     @done1, @done1),
  (2, 'COURSE_COMPLETED',    '수료',       '색연필화 기초를 수료했어요. 입문 배지를 받았습니다.',          1, NULL, NULL, NULL, NULL, NULL, 'COURSE_COMPLETED:1',    @done1, @done1),
  (2, 'COMMENT_ON_MY_WORK',  '새 댓글',    '내 작품에 강사A님이 댓글을 남겼어요.',                         NULL, NULL, NULL, 1, NULL, NULL, 'COMMENT_ON_MY_WORK:1',  @now - INTERVAL 15 DAY, @now - INTERVAL 16 DAY),
  (2, 'INQUIRY_ANSWERED',    '문의 답변',  '색연필화 기초 문의에 강사 A가 답변했어요.',                    NULL, NULL, NULL, NULL, 1, NULL, 'INQUIRY_ANSWERED:1',    @now - INTERVAL 20 DAY, @now - INTERVAL 21 DAY),
  (2, 'WORK_HIDDEN',         '작품 숨김',  '「나의 케이크」의 공개 노출이 제한되었습니다.',                NULL, NULL, 5, NULL, NULL, NULL, 'WORK_HIDDEN:5',         NULL, @now - INTERVAL 6 DAY),
  (2, 'REVIEW_REJECTED',     '평가 반려',  '색연필화 심화 과제가 반려되었습니다. 사유를 확인하고 재제출하세요.', NULL, 2, NULL, NULL, NULL, NULL, 'REVIEW_REJECTED:2', NULL, @now - INTERVAL 4 DAY),
  (2, 'WAITLIST_CONFIRMED',  '수강 확정',  '색연필화 응용 · 중급 대기 순서가 되어 수강이 자동 확정됐어요.', 2, NULL, NULL, NULL, NULL, NULL, 'WAITLIST_CONFIRMED:2', NULL, @e2),
  -- teacherA
  (6, 'SECTION_ASSIGNED',    '분반 배정',  '색연필화 응용 · 중급 A분반 담당으로 배정되었어요.',            NULL, NULL, NULL, NULL, NULL, 3, 'SECTION_ASSIGNED:3',    @now - INTERVAL 50 DAY, @now - INTERVAL 55 DAY),
  (6, 'REVIEW_SUBMITTED',    '과제 제출',  '디지털 드로잉 기초 최종 과제가 제출되었어요.',                 NULL, 4, NULL, NULL, NULL, NULL, 'REVIEW_SUBMITTED:4',    @now - INTERVAL 1 DAY, @s4),
  (6, 'INQUIRY_RECEIVED',    '새 문의',    '색연필화 기초 수강생 문의가 새로 들어왔어요.',                 NULL, NULL, NULL, NULL, 2, NULL, 'INQUIRY_RECEIVED:2',    NULL, @now - INTERVAL 12 HOUR),
  -- teacherB
  (7, 'REVIEW_SUBMITTED',    '과제 제출',  '디저트 기초 최종 과제가 제출되었어요.',                        NULL, 3, NULL, NULL, NULL, NULL, 'REVIEW_SUBMITTED:3',    NULL, @s3);

-- ---------------------------------------------------------------------
-- 작품 숨김 조치 이력 (#20) — 위 "관리자 숨김"(work 5)과 같은 사건
-- ---------------------------------------------------------------------
INSERT INTO work_moderation_logs (work_id, action, reason, admin_id, created_at) VALUES
  (5, 'HIDE', '작품 설명에 외부 판매 링크가 포함되어 공개 노출을 제한했습니다.', 1, @now - INTERVAL 6 DAY);
