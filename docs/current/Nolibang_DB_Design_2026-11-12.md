# 놀이방 LMS · 최종 DB 설계서

2026-11-12. 저장소의 [`schema.sql`][schema]과 [최신 ERD][erd]를 기준으로 테이블 역할, 데이터 제약, 조회용 뷰, 11월 6일 설계 이후의 변경사항을 정리한다. 관계도와 외래 키 전체 목록은 ERD 문서에 있고, 변경 배경과 정책 해석은 [DB·정책 변경 정리][change]와 [최종 정책 명세서][policy-spec]에 있다.

## 1. 설계 기준

| 항목 | 기준 |
| --- | --- |
| 대상 DB | MySQL 8.4(8.0에서 적재 검증 기록), InnoDB |
| 문자셋 | `utf8mb4`, `utf8mb4_0900_ai_ci` — 아이디·이메일·닉네임 UNIQUE는 대소문자·악센트를 구분하지 않는다. 저장 전 정규화는 서비스가 한다 |
| 시각 | `DATETIME(6)`, UTC 저장(`time_zone = '+00:00'`) |
| 규모 | **테이블 26개 · 외래 키 50개 · 최종 뷰 6개** |
| 외래 키 동작 | `work_files → work_versions`만 `ON DELETE CASCADE`. 나머지는 기본값(RESTRICT)이며 삭제 순서는 서비스가 맞춘다 |
| 신규 설치 | `CREATE DATABASE nolibang …` → [`schema.sql`][schema] → [`seed.sql`][seed]. JPA `ddl-auto: none` |
| 기존 설치 | 추가분은 `migration_*.sql`로 제공한다. 실제 DB 반영 여부는 DB 담당자가 확인한다 |

`schema.sql`은 11월 6일 정본(01) · 추가분(02) · HTML 패치(05)를 합친 뒤 11-06 팀 결정을 반영하고, 이후 추가된 탈퇴·성장 테이블을 덧붙인 파일이다.

## 2. 테이블 역할

### 2-1. 계정과 취미 분류

| 테이블 | 역할 | PK | 핵심 UNIQUE · CHECK |
| --- | --- | --- | --- |
| `users` | 학습자·강사·관리자 계정 | `id` | UNIQUE `login_id`, `email`, `nickname` · `role` ∈ USER/INSTRUCTOR/ADMIN · `status` ∈ PENDING_EMAIL/ACTIVE/INACTIVE/WITHDRAWN · 학습자는 닉네임 필수 · 임시 비밀번호 변경은 강사·관리자만 · `WITHDRAWN` ⇔ `withdrawn_at` 존재 |
| `auth_tokens` | 이메일 인증·비밀번호 재설정·아이디 찾기 토큰 | `id` | UNIQUE `token_hash`(BINARY 32) · `purpose` 3종 · 만료 > 생성 |
| `categories` | 대분류 | `id` | UNIQUE `code` · `state` ∈ OPERATING/PREPARING/PRIVATE |
| `hobbies` | 세부 취미 | `id` | UNIQUE `code` · 상태값은 대분류와 같음 |
| `instructor_hobby_grants` | 강사별 취미 활동 권한 | (`instructor_id`, `hobby_id`) | 권한 회수는 `is_active`·`revoked_at`으로 기록 |

### 2-2. 강의·분반·수강·대기·진도

| 테이블 | 역할 | PK | 핵심 UNIQUE · CHECK |
| --- | --- | --- | --- |
| `courses` | 취미별 수준 강의(공통 강의) | `id` | UNIQUE (`hobby_id`, `level`) · `level` 1~3 · `status` ∈ OPEN/CLOSED |
| `lessons` | 강의 차시와 영상 | `id` | UNIQUE (`course_id`, `lesson_no`), (`id`, `course_id`) · `lesson_no` 1~3 · 영상 키와 길이 필수 |
| `course_sections` | 강의 분반, 담당 강사, 정원 | `id` | UNIQUE (`course_id`, `section_no`), (`course_id`, `name`), (`id`, `course_id`) · 정원 1~30 · 담당 강사 NULL 허용(담당 공백) |
| `enrollments` | 수강 건(정원 점유 단위) | `id` | UNIQUE (`user_id`, `course_id`) — 같은 강의 1회 수강 · `status` ∈ ACTIVE/COMPLETED/CANCELLED/EXPIRED · `expires_at` = `confirmed_at` + 30일 · 상태별 완료·취소·종료 시각 짝 검사 |
| `waitlist_entries` | 강의별 단일 대기열 | `id` | UNIQUE (`course_id`, `waiting_user_id`), `enrollment_id` · 상태별 열 모양 검사(WAITING/CONFIRMED/CANCELLED) |
| `lesson_progresses` | 차시별 재생 누적·완료 | (`enrollment_id`, `lesson_id`) | 재생 시간·위치 0 이상 |

### 2-3. 작품·과제 평가·게시판

| 테이블 | 역할 | PK | 핵심 UNIQUE · CHECK |
| --- | --- | --- | --- |
| `works` | 최종 과제와 일반 작품 | `id` | UNIQUE `enrollment_id` · `FINAL`은 수강 연결 필수, `GENERAL`은 연결 없음 · `level` 1~3 · 숨김이면 사유·시각 필수 · 공개면 공개 시각 필수 · 삭제되면 표시 버전 없음 |
| `work_versions` | 작품의 제출·재제출 버전 | `id` | UNIQUE (`work_id`, `version_no`), (`work_id`, `id`) |
| `work_files` | 버전별 이미지 | `id` | UNIQUE (`work_version_id`, `sort_order`) · 순서 1~5 · JPEG/PNG/WebP · 원본 1B~50MB |
| `review_rounds` | 최종 과제 평가 회차 | `id` | UNIQUE (`enrollment_id`, `round_no`), `work_version_id`, 생성 열 2개 · `status` ∈ WAITING/IN_REVIEW/APPROVED/REJECTED · `due_at` = `submitted_at` + 7일 · 확정 시 피드백 50자 이상 · 상태별 열 모양과 시각 순서 검사 |
| `work_likes` | 회원 좋아요 | (`work_id`, `user_id`) | 사용자당 1회, 취소 = 행 삭제 |
| `work_comments` | 댓글과 1단계 답글 | `id` | `status` ∈ NORMAL/DELETED와 `deleted_at` 짝 · 본문 공백 불가 · 탈퇴 익명화로 `author_id` NULL 허용 |
| `work_moderation_logs` | 관리자 숨김·복구 이력 | `id` | `action` ∈ HIDE/RESTORE · 사유 공백 불가. 수정·삭제하지 않는 이력 행 |

### 2-4. 1:1 문의와 알림

| 테이블 | 역할 | PK | 핵심 UNIQUE · CHECK |
| --- | --- | --- | --- |
| `inquiry_threads` | 수강 건에 묶인 학습자↔담당 강사 문의 | `id` | (`enrollment_id`, `learner_id`, `course_id`) 복합 FK로 본인 수강 건만 연결 · `status` ∈ OPEN/ANSWERED/CLOSED |
| `inquiry_messages` | 문의 메시지 | `id` | 본문 공백 불가 |
| `notifications` | 사이트 알림 | `id` | UNIQUE (`user_id`, `dedupe_key`) — 같은 사건 중복 알림 방지 · `kind` 13종 · 종류별 대상 열 필수 |

### 2-5. 성장 기록과 탈퇴 이메일 보관

| 테이블 | 역할 | PK | 핵심 UNIQUE · CHECK |
| --- | --- | --- | --- |
| `quest_claims` | 퀘스트 보상 수령 기록 | (`user_id`, `quest_key`, `period_key`) | 같은 기간 중복 수령 방지 |
| `user_avatars` | 꾸미기 장착 상태 | `user_id` | 사용자당 1행 |
| `user_achievements` | 업적 배지 획득 기록 | (`user_id`, `badge_code`) | 같은 업적 1회 |
| `user_equipped_badges` | 대표 배지 장착 | (`user_id`, `slot_no`) | UNIQUE (`user_id`, `badge_code`) · 슬롯 1~3 |
| `withdrawn_email_holds` | 탈퇴 이메일 재가입 제한 확인값 | `email_hash` | 사용자 FK 없음 · `expires_at` = `withdrawn_at` + 30일 |

경험치·레벨·직업·길드 순위는 저장하지 않고 수강·진도·작품·댓글·좋아요 기록에서 계산한다.

## 3. 무결성을 지키는 핵심 제약

### 3-1. 복합 외래 키

단일 FK만 쓰면 다른 강의나 다른 학습자의 행을 가리킬 수 있는 곳은 복합 FK로 같은 강의·같은 학습자임을 DB에서 보장한다.

| 자식 → 부모 | 보장하는 내용 |
| --- | --- |
| `enrollments(section_id, course_id)` → `course_sections(id, course_id)` | 수강 건의 분반이 같은 강의의 분반 |
| `waitlist_entries(enrollment_id, user_id, course_id)` → `enrollments(id, user_id, course_id)` | 대기에서 확정된 수강 건이 같은 학습자·같은 강의 |
| `lesson_progresses(enrollment_id, course_id)` · `(lesson_id, course_id)` | 진도의 수강 건과 차시가 같은 강의 |
| `inquiry_threads(enrollment_id, learner_id, course_id)` → `enrollments(id, user_id, course_id)` | 문의 작성자가 그 수강 건의 학습자 |
| `works(id, display_version_id)` → `work_versions(work_id, id)` | 표시 버전이 같은 작품의 버전 |

`works ↔ work_versions`, `works → review_rounds`는 순환 참조라 두 테이블을 만든 뒤 `ALTER TABLE`로 연결한다.

### 3-2. 상태별 생성 열

| 테이블 · 생성 열 | 값 | UNIQUE로 막는 것 |
| --- | --- | --- |
| `waitlist_entries.waiting_user_id` | 상태가 WAITING일 때만 `user_id` | 한 학습자가 같은 강의에 두 번 대기 |
| `review_rounds.active_enrollment_id` | WAITING·IN_REVIEW일 때만 `enrollment_id` | 수강 건당 평가 대기·진행 회차 2개 이상 |
| `review_rounds.approved_enrollment_id` | APPROVED일 때만 `enrollment_id` | 수강 건당 승인 2회 이상 |

### 3-3. DB 밖에서 서비스가 지키는 규칙

정원 초과 방지, FIFO 순번, 분반 자동 배정, 강의별 정확히 3차시, 실제 재생 60% 완료, 최종 과제 이미지 최소 1장, 상태 전이 순서, 댓글 자격, 답글 1단계는 서비스가 검사한다. 신청·자리 반환·평가 확정·분반 변경은 해당 강의의 `course_sections` 행을 `FOR UPDATE`로 먼저 잠가 직렬화한다.

## 4. 조회용 뷰

| 뷰 | 기준 | 사용처 |
| --- | --- | --- |
| `v_section_seats` | 분반별 ACTIVE 수강 수, 남은 자리, 담당 강사 존재 여부(`assignable`) | 자동 배정, 강의 정원 표시 |
| `v_section_current_stats` | 분반별 수강 상태와 수강 건별 최신 평가 회차 집계 | 정본 통계(현재 숫자) |
| `v_section_stats_screen` | 학습 중·평가 대기·수료·누적 반려 | 강사·관리자 통계 화면 |
| `v_user_level_badges` | 취미·수준별 COMPLETED 수강 1건 이상 | 수준 배지, 댓글 자격, 작품 수준 태그 |
| `v_user_master_badges` | 같은 취미의 3개 수준 모두 수료 | 마스터 배지 |
| `v_public_works` | 공개 · 숨김 아님 · 삭제 안 됨, 좋아요 = 회원 행 수 + `anonymous_like_count` | 작품 게시판 |

배지는 수강 기록에서 계산하므로 별도 배지 테이블이 없다. `v_public_works`는 탈퇴 익명 좋아요를 합산하도록 마지막에 `CREATE OR REPLACE`로 다시 정의된다.

## 5. 11월 6일 설계 이후 주요 변경사항

| 구분 | 변경 | 근거 |
| --- | --- | --- |
| 제외 | 약관 동의 테이블 `terms_agreements` | 11-06 팀 결정(약관·만 14세 확인 미구현) |
| 제외 | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | 11-06 팀 결정. Spring Security `maximumSessions(1)` 메모리 세션 사용 |
| 축소 | 알림 종류 14종 → 13종(`REVIEW_DUE_SOON` 제외) | 11-06 팀 결정. 마감 임박은 강사 평가 목록 경고로만 표시 |
| 추가 | `notifications`의 `work_id`·`comment_id`·`inquiry_thread_id`·`section_id`와 종류별 대상 CHECK | HTML 패치 H1 |
| 추가 | `course_sections.name`, UNIQUE (`course_id`, `name`) | HTML 패치 H4 |
| 추가 | `v_section_stats_screen` | HTML 패치 H2 |
| 추가 | `work_moderation_logs` | 관리자 숨김·복구 이력(#20) |
| 추가 | `users.withdrawn_at`, 상태 `WITHDRAWN`, `ck_users_withdrawn` | 회원 탈퇴 |
| 추가 | `withdrawn_email_holds`, `works.anonymous_like_count`, `work_comments.author_id` NULL 허용, `v_public_works` 재정의 | 탈퇴 개인정보 정리 |
| 추가 | `quest_claims`, `user_avatars`(의상·머리 장식 포함), `user_achievements`, `user_equipped_badges` | RPG 성장 요소 |
| 추가 | `categories.image_key` | 관리자 대분류 대표 사진 |
| 유지 | `work_versions` | 제출·재제출 버전 저장. 승인 과제 수정·삭제 기능은 미정이라 구현하지 않음 |

기존 설치용 추가 SQL은 `migration_game.sql`, `migration_game_crown.sql`, `migration_hobby_image.sql`, `migration_withdrawal_privacy.sql`이다. 운영·공용·시연 DB에는 DB 담당자 확인 없이 실행하지 않는다.

## 6. 확인 범위

이 문서는 저장소의 DDL과 이를 사용하는 서비스 코드를 읽고 정리했다. 실제 시연 DB의 스키마, 마이그레이션 실행 결과, 대량 데이터 성능은 확인 범위에 포함되지 않는다. 11월 6일 원본 DB 설계서와 당시 검증 기록은 저장소 밖 문서다.

[최신 ERD][erd] · [DB·정책 변경 정리][change] · [최종 정책 명세서][policy-spec] · [현재 스키마][schema]

[erd]: Nolibang_ERD_2026-11-12.md
[change]: Nolibang_DB_Policy_2026-11-12.md
[policy-spec]: Nolibang_Policy_Spec_2026-11-12.md
[schema]: ../../src/main/resources/sql/schema.sql
[seed]: ../../src/main/resources/sql/seed.sql
