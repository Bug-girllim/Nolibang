# 08. 요구사항 ↔ DB 추적표 (MVP 17 테이블, 2026-11-02 · 2026-11-05 단순화 반영)

> **[2026-11-05 MVP 단순화 결정 D-51~D-59 · 이 문서의 모든 앞선 기준보다 우선]** ① 강사 계정은 관리자가 **임시 비밀번호로 즉시 생성**(메일 설정 링크 없음, 첫 로그인 시 비밀번호 변경, D-51·FR-012) ② 공통 강의 분반은 여러 개·정원 1~30 유지, 배정은 **남은 자리가 가장 큰 분반** 한 줄 규칙, **강사 합산 30건 검사 삭제**(D-52·FR-052) ③ **단일 대기열(FIFO)** 유지, 자리가 나면 **1순위를 즉시 수강 확정 + 사이트 알림**, 초대·수락·48시간 단계 없음(D-53·FR-053) ④ 수강 취소 → 자리 반환 → **대기 1순위 자동 확정**(D-54·E6) ⑤ 같은 강의는 **수료(D-35)뿐 아니라 취소·만료한 강의도 이번에는 재신청 불가**(D-55·FR-055 P1) — 재수강 기록 삭제(D-36)·이전 시도 보관(D-44·FR-056)은 대상이 없어 보류 ⑥ 평가는 **시작(잠금) → 초안 → 승인/반려**, 시작 취소, 마감 임박·기한 초과 경고 유지(D-56·FR-023·030·031) ⑦ 알림은 **사이트 알림만**: 평가 결과(승인/반려)·대기→자동 확정·수료, **이메일 알림 없음**(D-57·FR-035) ⑧ 강사 통계는 **현재 숫자만**: 분반별 수강 중·수료·반려·평가 대기 수(D-58·FR-051) ⑨ 관리자 MVP는 **강사 계정·분반(생성·담당·정원)·대기열 조회·관리자 홈**, 카테고리·강의는 **시드 데이터**(D-59). 결정 상세는 `05-policy.md` §14. 아래 본문에 남은 48시간 초대·합산 30건·재수강 회차·월별 통계 문구는 **결정 이력**으로만 본다.

- **기준 문서:** `03-requirements.md`(2026-11-05 갱신, 현행 FR 42개·NFR 9개), `05-policy.md` §13·**§14(D-51~D-59)**, `06-data.md`
- **기준 스키마:** `database/schema/mvp17_create_schema.sql` (17 테이블 · FK 30 · CHECK 18)
- **이번 확정:** **반려 사유 최소 50자**(O-04 종결, FR-031) — 승인 피드백과 같은 기준으로 DB CHECK 적용(공백 포함 글자 수)
- **2026-11-05 스키마 단순화(D-51~D-59):** `auth_tokens(SET_INSTRUCTOR_PASSWORD)` 삭제 → `users.must_change_password` 추가 / `waitlist_entries`의 INVITED·EXPIRED 상태·`section_id`·`invite_expires_at` 삭제(상태 = WAITING·CONFIRMED·CANCELLED) / `enrollments.seat_held` 중 초대 예약 의미 삭제 / `notifications.email_status·email_attempts` 삭제 / `instructor_stat_events` 삭제(통계는 현재 상태 계산) / `enrollment_transfers` 삭제(담당 변경은 `course_sections.instructor_id` 갱신) / `categories·hobbies.deleted_at`은 시드 데이터로 미사용(유지 가능). SQL 파일은 이 표에 맞춰 개정 필요.

**반영 방식 표기**
- **DB** = 제약(CHECK·UNIQUE·FK)으로 DB가 직접 막음
- **구조** = 저장할 칼럼·테이블은 있고, 판단은 서비스 코드
- **계산** = 별도 저장 없이 조회로 계산
- **서비스** = 여러 행·테이블에 걸친 규칙이라 서비스 트랜잭션에서 처리(DB 단독 불가)
- **미포함** = 이번 17 테이블에 없음(P1/P2/확장) → 구현할 때 원본 37 테이블 설계에서 추가

## 1. P0 (19개) — 모두 반영

| FR | 요구사항 요약 | 테이블·칼럼 | 반영 |
| --- | --- | --- | --- |
| FR-001 | 아이디·비밀번호·실명·이메일 필수, 이메일 인증, 이메일 고유 | `users.login_id`(UQ), `email`(UQ), `real_name`, `email_verified_at`, `auth_tokens(VERIFY_EMAIL)` | **DB** 필수값·중복 CHECK/UQ, **서비스** 미인증 로그인 차단. 탈퇴 후 30일 재가입 제한은 FR-040(P2)와 함께 → 미포함 |
| FR-002 | 닉네임 선택·중복 불가, 미입력 시 자동 생성 | `users.nickname`(UQ) | **DB** 학습자는 닉네임 필수(자동 생성값 저장), UQ로 중복 차단 → 충돌 시 서비스가 재생성 |
| FR-003 | 별도 로그인, 첫 로그인은 탐색·이후 홈 | `users.first_login_at` | **구조** (이번에 추가) |
| FR-010 | 대분류/취미 운영·준비 중·비공개 노출 | `categories.state`, `hobbies.state` | **구조** + 서비스 권한 검사 |
| FR-012 | 관리자만 강사 생성(**임시 비밀번호로 즉시 활성**, 첫 로그인 변경 강제), 취미 권한·분반 담당 지정 | `users(password_hash, must_change_password)`, `instructor_hobby_grants`, `course_sections.instructor_id` | **DB** 강사 이메일 필수 CHECK, **서비스** 첫 로그인 변경 강제·담당 외 열람 차단 |
| FR-013 | 취미 3 × 수준 3 = 강의 9, 강의당 필수 3차시, 준비물 | `courses`(UQ 취미+수준), `lessons`(1~3 CHECK, UQ 강의+차시), `courses.supplies_text` | **DB** · 차시당 영상 1개 = `lessons.video_key/duration_sec` (총 27개 확정) |
| FR-015 | 동일 강의 학습 중/대기 중 중복 금지, **수료·취소·만료 강의 재신청·대기 불가** | `enrollments` UQ(학습자, 강의), `waitlist_entries` UQ(학습자, 진행 중 대기) | **DB** `enrollments` UQ(학습자, 강의)가 곧 재신청 불가(D-55). 수강 이력이 있는 강의의 대기 신청 차단은 두 표에 걸쳐 있어 **서비스** |
| FR-020 | 차시별 실제 재생 60% 이상이면 완료, 회차별 독립 기록 | `lesson_progresses(played_sec, completed_at)`, `lessons.duration_sec` | **구조** 완료 판정 `played_sec >= duration_sec × 0.6` |
| FR-021 | 3차시 모두 완료 후 최종 과제 제출 | `lesson_progresses.completed_at` 3건 | **서비스** |
| FR-022 | 이미지 1~5장·장당 50MB, 모두 성공해야 제출 | `work_files`(순서 1~5, 크기 ≤ 50MB CHECK, 형식 ENUM) | **DB** 장수·크기·형식, **서비스** 전부 성공 시에만 트랜잭션 커밋 |
| FR-023 | 3차시→제출→담당 강사 평가→50자 피드백·승인 후 수료, 반려 사유 필수, 중복 승인 방지 | `review_rounds`, `enrollments.status/completed_at` | **DB** 피드백·반려 사유 50자, 회차당 승인 1회, 수료=COMPLETED+수료일시 |
| FR-029 | 강의·회차별 진도, 만료일, 평가 상태, 수료일·피드백·작품을 본인만 열람 | `enrollments`, `lesson_progresses`, `review_rounds`, `works` | **계산** + 서비스 권한 |
| FR-030 | 담당 강사의 평가 시작(잠금)→초안→승인·반려, 시작 취소, 제출부터 7×24시간 고정, 마감 임박·초과 경고, 담당 변경 시 초안·잠금·기한 승계 | `review_rounds(status, started_at, due_at, draft_body)`, `course_sections.instructor_id` | **DB** 대기 상태엔 시작 시각 없음, 마감 > 제출. 초안·기한이 회차에 있어 **분반 담당 변경** 시 그대로 승계. 경고는 `due_at` **계산** |
| FR-031 | 승인 피드백 50자 이상, **반려 사유 50자 이상**, 반려 사유도 참고 피드백 대상 | `review_rounds.feedback_body`, `works.reference_review_id` | **DB** `status IN (APPROVED, REJECTED) → CHAR_LENGTH ≥ 50` |
| FR-035 | **사이트 알림만**: 평가 결과·대기→자동 확정·수료 (이메일 없음) | `notifications(kind, target_kind, target_id, read_at)` | **구조**, 발송은 상태 전이 트랜잭션에서 함께 기록 |
| FR-051 | 강사 통계 **현재 숫자만**: 분반별 수강 중·수료·반려·평가 대기 수 | `enrollments(status, section_id)`·`review_rounds(status)` | **계산** (GROUP BY 분반). 과거 이벤트 테이블 불필요 |
| FR-052 | 분반 정원 1~30, **남은 자리가 가장 큰 분반** 배정, 대기자 우선, 담당 없는 분반 제외 (강사 합산 30건 없음) | `course_sections(capacity, instructor_id)`, `enrollments(section_id, status)` | **DB** 분반 정원 1~30. 남은 자리 계산·동시 신청은 **서비스**(행 잠금) |
| FR-053 | 강의별 단일 대기열(FIFO), 자리 반환 시 **1순위 즉시 수강 확정**, 대기 취소 후 끝순번, 중복 대기 불가, 관리자 조회만 | `waitlist_entries(status WAITING/CONFIRMED/CANCELLED)` (FIFO = id 순, 진행 중 1건 UQ) | **DB** 중복 대기, **서비스** 반환 트랜잭션 안에서 1순위 확정·알림 기록 |
| FR-054 | 수강 확정+30일, 상태별 정원 점유/반환(**취소 포함**) → 반환 즉시 대기 1순위 자동 확정, 만료 후에도 평가, 영상 접근은 30일 이내 또는 수료자 | `enrollments(confirmed_at, expires_at, status)` | **DB** 만료 > 확정, **서비스** 점유/반환 전이와 자동 확정 |

## 2. P1 (12개 · FR-011·014는 시드 데이터로 범위 제외)

| FR | 요구사항 요약 | 반영 |
| --- | --- | --- |
| FR-004 | 이메일 인증 기반 아이디 찾기·비밀번호 재설정 | **구조** `auth_tokens(RESET_PASSWORD, RECOVER_LOGIN_ID)` |
| FR-005 | 새 기기 로그인 시 기존 세션 종료, 1시간 유휴·재생 중 유지 | **미포함** — Spring Session(JDBC) 테이블 또는 원본 `login_sessions` 추가 |
| FR-011 | **범위 제외(D-59)** — 카테고리·상태는 시드 데이터. `deleted_at` 칼럼은 후속 CRUD 대비로만 유지 |
| FR-014 | **범위 제외(D-59)** — 강의·차시·과제는 검수 완료 시드 데이터. `courses.status`는 시드 값(OPEN 등)으로만 사용 |
| FR-024 | 수준 배지·마스터 배지, 소급 철회 없음 | **계산** 수료 회차를 취미·수준으로 묶음. 수료 회차는 지워지지 않으므로(수료 강의 재수강 불가) 소급 철회 없음 |
| FR-025 | 차시 완료·제출·재제출·평가 시작·반려/승인 시각 구분, 수료일 = 승인 시각 | **구조** `lesson_progresses.completed_at`, `review_rounds.submitted_at/started_at/finalized_at`, `enrollments.completed_at` |
| FR-026 | 평가 시작 전 수정 가능(기한 불변), 시작 즉시 잠금, 재제출만 새 7일 | **구조** 수정은 `works.updated_at`, 재제출은 새 `review_rounds` 행. 시작 취소 시 WAITING 복귀 **DB** CHECK |
| FR-027 | 일반 작품 여러 개, 제목·이미지 1~5장, 최근 공식 피드백(승인/반려) 참고 연결 | **구조** `works(kind=GENERAL, reference_review_id)` · 운영 중 취미·배지 이하 수준 선택은 **서비스** |
| FR-028 | 성장 목록 날짜순, 한 줄 회고, 참고 피드백 확인 | **구조** `works.created_at`(정상 수정에도 순서 불변), `reflection` |
| FR-033 | 공개 작품 좋아요·댓글·답글 | **미포함** — 원본 `work_comments`, `work_likes` 추가 |
| FR-034 | 게시판 검색·필터·정렬, 최종 과제 기본 제목 수정 가능 | **구조** `works.title`, 인덱스 `idx_works_public` |
| FR-036 | 공개 작품 숨김·복구, 작성자 공개 선택과 독립, 사유 표시 | **구조** `works.admin_hidden`, `hidden_reason`, `visibility` 분리 |
| FR-042 | 학습자 → 담당 강사 1:1 문의 | **미포함** — 원본 `inquiry_threads`, `inquiry_messages` 추가 |
| FR-055 | **같은 강의 재신청 불가**(수료·취소·만료, D-55) | **DB** `enrollments` UQ(학습자, 강의). 이전 회차 삭제·30일 판정·CASCADE 정리 로직은 **불필요**(보류) |

## 3. P2 (8개) · 등급 미정 · 확장

| FR | 반영 |
| --- | --- |
| FR-032 복습 추천 | **미포함** (원본 `review_recommendations`) |
| FR-040 탈퇴·익명화·30일 재가입 제한 | 일부 구조(`users.withdrawn_at`, 탈퇴 시 식별정보 NULL 허용). 재가입 확인값은 원본 `email_rejoin_blocks` 추가 |
| FR-041 영상 제공 종료 공지·신규 신청 제한 | 일부 구조(`courses.status = ENDED`). 공지일·종료일 칼럼은 구현 시 추가 |
| FR-044 강사 경고·정지 | **미포함** |
| FR-045 · FR-048 수준별 내 상태·공개 프로필 | **계산** |
| FR-049 강사 완전 탈퇴 시 피드백 보존·'활동 종료 강사' | **구조** `review_rounds.reviewer_id` ON DELETE SET NULL → 화면 표시 대체 |
| FR-050 일반 작품 본인 삭제 | **구조** 작품 삭제 시 이미지 CASCADE. 승인된 최종 과제 삭제 후처리는 기획 미정 → 수료는 `enrollments`에 있어 작품을 지워도 수료·배지 유지 |
| FR-056 이전 시도 보관 | **보류(D-55)** — 재신청 불가로 새 회차가 없어 대상 없음 |
| FR-043·046·047 신고 | 확장 → 스키마에 넣지 않음 |

## 4. 비기능 요구사항

| NFR | 반영 |
| --- | --- |
| NFR-01 권한 서버 검사 | 서비스 |
| NFR-02 비밀번호 평문 금지 | `users.password_hash`(임시 비밀번호 포함), `auth_tokens.token_hash`(가입 인증·재설정만, 원문 미저장) |
| NFR-03 중복·동시 요청 일관성 | **DB** 회차당 승인 1회·최종 과제 1개·진행 중 대기 1건·강의당 수강 1건 UQ / 자리 반환↔1순위 확정 경쟁은 서비스 잠금 |
| NFR-04 실명 비공개 | 서비스(조회 권한). 공개 표시는 `nickname` |
| NFR-05 미디어 50MB×5 | **DB** `work_files` CHECK |
| NFR-06 소급 수료 박탈 금지 | 수료가 `enrollments`에만 있고 작품·강사 변경과 무관 |
| NFR-07~09 | 해당 없음(화면·환경) |

## 5. 이번 요구사항 검토로 스키마에 추가·변경한 것

| 변경 | 근거 |
| --- | --- |
| `review_rounds` CHECK: 승인·반려 모두 `feedback_body` 50자 이상 | FR-031, 반려 50자 확정(O-04 종결) |
| `review_rounds` CHECK: WAITING이면 `started_at`, `finalized_at` 없음 | FR-026 평가 시작 취소·잠금 |
| `users.first_login_at` 추가 | FR-003 (P0) |
| `users` CHECK: 학습자 실명·이메일·닉네임 필수 / 강사 이메일 필수 (탈퇴 계정 제외) | FR-001, FR-002, FR-012 |
| `categories.deleted_at`, `hobbies.deleted_at` 추가 | FR-011 삭제/복구 |
| `users.must_change_password` 추가, `auth_tokens(SET_INSTRUCTOR_PASSWORD)` 삭제 | FR-012, D-51 |
| `waitlist_entries`: INVITED·EXPIRED·`section_id`·`invite_expires_at` 삭제 | FR-053, D-53 |
| `notifications.email_status/email_attempts` 삭제 | FR-035, D-57 |
| `instructor_stat_events`, `enrollment_transfers` 삭제 | FR-051·D-58, D-59 |

## 6. 서비스 코드에서 꼭 처리해야 하는 것 (DB가 막지 못함)

1. **분반 정원 + 남은 자리 최대 분반 배정** (FR-052): 신청·자동 확정을 한 트랜잭션에서 처리하고, 해당 강의의 분반 행을 `SELECT … FOR UPDATE`로 잠근다. 동률은 분반 번호순.
2. **대기자 우선 / 담당 없는 분반 제외** (FR-052·053).
3. **자리 반환 → 대기 1순위 자동 확정** (FR-053·054, D-54): 수료 승인·수강 취소·만료 반환·정원 증가 트랜잭션 안에서 WAITING 최소 id를 CONFIRMED로 바꾸고 `enrollments` 행 생성·사이트 알림 기록. 정기 만료 작업은 수강 만료 반환에만 사용.
4. **3차시 완료 후 제출, 기간 내 제출, 평가 대기 중 취소 금지** (FR-021·054).
5. **학습 중이거나 수강 이력이 있는 강의(수료·취소·만료)에 대기 신청 금지** (FR-015·055).
6. **최종 과제의 취미·수준 = 강의의 취미·수준** (정규화 예외 E3).
7. **알림 대상 존재 확인** (`notifications.target_kind/target_id`는 FK 불가).
