# 놀이방 LMS 공통 규칙 (CONVENTIONS)

> 이슈 #01 킥오프 합의 결과 문서. 코드를 쓰기 전에 공통 규칙·상태값·도메인 간 계약을 한 번에 정해 두고,
> 이후 이슈가 서로를 기다리지 않고 병렬로 진행되게 하려는 문서입니다.
>
> - 작성: 2026-11-09 초안 (진행·기록: 팀원 1)
> - **표시:** ✅ = 확정(근거 문서·DB·코드가 있음) / 💡 = 이 초안의 **제안값**(회의에서 확인하고, 다르면 고쳐 쓴다)
> - **변경 규칙:** 확정 이후 enum 값·계약 시그니처·ErrorCode·URL을 바꾸려면 **이슈 #01 댓글로 전원 합의** 후 이 문서와 코드를 함께 고친다. 무단 변경하면 다른 팀원 코드가 깨진다.
> - 문서끼리 다를 때 우선순위: **DB(`schema.sql`) > 이슈 본문 > API 명세서 > 패키지 설계 > 기획 원문**

---

## 1. 패키지·네이밍 규칙 ✅

- 기본 패키지: `com.wanted.lmspractice`
- 도메인 구조: `domain/{도메인}/{controller, service, entity, repository, dto, policy}`, 공통은 `global/{config, exception, file, controller}`
- 도메인: `admin, auth, course, enrollment, hobby, inquiry, instructor, learning, notification, review, user, work`
- 이름: Entity는 **단수형**(`Course`), 화면·서비스용 데이터는 `~DTO`, 폼 입력은 `~FormDTO`, 규칙 클래스는 `~Policy`
- 계층 역할
  | 계층 | 한다 | 하지 않는다 |
  |---|---|---|
  | controller | URL 매핑, 값 받기, `@Valid`, 화면 이름·redirect 결정 | 비즈니스 판단, 트랜잭션, Entity 다루기, try-catch |
  | service | 흐름 조립, `@Transactional` | 화면 이름·Model 다루기, Entity를 Controller로 반환 |
  | entity | 칼럼 매핑 + 자기 상태로 판단하는 규칙 | public setter, Repository 주입 |
  | policy | 다른 행을 조회해야 판단되는 규칙 | 저장·상태 변경 |
- **다른 도메인의 Repository는 직접 주입하지 않는다.** 다른 도메인 데이터는 그 도메인 Service의 계약 메서드(§3)로만 얻는다. Entity는 서비스 안에서만 주고받고, Controller·템플릿에는 DTO만 전달한다.
- Entity의 다른 표 참조는 `Long xxxId`로 둔다(다른 도메인 Entity 직접 참조 금지).
- 파일 소유자 1명 원칙: `SecurityConfig`·`ErrorCode`·`build.gradle`·`application.yaml`·`common/layout.html`은 **팀원 1**이 소유한다. 다른 팀원이 고쳐야 하면 직접 고치지 말고 요청한다. Entity는 merge 후 해당 도메인 담당자가 소유한다.

## 2. 상태값 enum ✅

DB `CHECK` 값과 **글자까지 같아야** 한다. 바꾸려면 DB(`schema.sql`)·Entity를 같은 PR에서 함께 고치고 전원에게 공유한다. 집계 쿼리(#07)가 이 값에 의존한다.

| enum | 값 |
|---|---|
| `Role` | USER / INSTRUCTOR / ADMIN |
| `UserStatus` | PENDING_EMAIL / ACTIVE / INACTIVE |
| `TokenPurpose` | VERIFY_EMAIL / RESET_PASSWORD / RECOVER_LOGIN_ID |
| `ExposureState` | OPERATING / PREPARING / PRIVATE |
| `CourseLevel` | BASIC / INTERMEDIATE / ADVANCED (DB에는 1·2·3 숫자 — `CourseLevelConverter`) |
| `CourseStatus` | OPEN / CLOSED |
| `EnrollmentStatus` | ACTIVE(정원 점유, 만료 후 평가 중 포함) / COMPLETED / CANCELLED / EXPIRED |
| `WaitlistStatus` | WAITING / CONFIRMED / CANCELLED |
| `ReviewStatus` | WAITING / IN_REVIEW / APPROVED / REJECTED |
| `WorkKind` | FINAL / GENERAL |
| `Visibility` | PUBLIC / PRIVATE |
| `CommentStatus` | NORMAL / DELETED |
| `InquiryStatus` | OPEN / ANSWERED / CLOSED |
| `NotificationKind` (13종) | WAITLIST_CONFIRMED / REVIEW_APPROVED / REVIEW_REJECTED / COURSE_COMPLETED / WORK_HIDDEN / WORK_RESTORED / COMMENT_ON_MY_WORK / REPLY_TO_MY_COMMENT / INQUIRY_ANSWERED / INQUIRY_RECEIVED / PUBLIC_COMMENT / REVIEW_SUBMITTED / SECTION_ASSIGNED |
| `NotificationTargetType` | ENROLLMENT / REVIEW_ROUND / WORK / COMMENT / INQUIRY_THREAD / SECTION |

- 피드백 마감 임박 알림(REVIEW_DUE_SOON)은 11-06 결정으로 **제외**(화면 경고로만 표시).

## 3. 도메인 간 계약 메서드 ✅(시그니처는 💡 제안 — 확정 후 변경 금지)

| # | 계약 | 제공(소유) | 사용 | 시그니처 · 규칙 |
|---|---|---|---|---|
| C1 | 로그인 사용자 | 팀원 1 `AuthDetails` | 전원 | `@AuthenticationPrincipal AuthDetails` → `Long getUserId()`, `Role getRole()` |
| C2 | 예외 | 팀원 1 `ErrorCode`, `BusinessException` | 전원 | `throw new BusinessException(ErrorCode.X)`. 새 코드는 팀원 1에게 요청 |
| C3 | 취미 권한 확인 | 팀원 1 `InstructorAccountService` | 팀원 2(#10), 팀원 4(#18) | `boolean hasHobbyGrant(Long instructorId, Long hobbyId)` |
| C4 | 자리 채우기 | 팀원 2 `SeatService` | 팀원 2 내부 | `int fillVacancies(Long courseId)` — **호출자 트랜잭션 안에서 실행** |
| C5 | 수료·종료 처리 | 팀원 2 `EnrollmentService` | 팀원 3(#14) | `void complete(Long enrollmentId)`, `void closeAfterRejection(Long enrollmentId)` — 내부에서 C4 호출 |
| C6 | 수강 조회 | 팀원 2 `EnrollmentService`·`SectionService` | 팀원 3·4·1 | `Enrollment getOwnedEnrollment(Long userId, Long enrollmentId)`(본인 확인 포함), `Long getCurrentInstructorId(Long enrollmentId)`, `List<SectionDTO> getSectionsByInstructor(Long instructorId)` / `Enrollment.isWithinPeriod(LocalDateTime now)` |
| C7 | 알림 | 팀원 4 `NotificationService` | 팀원 2·3·4 | `void notify(Long receiverId, NotificationKind kind, Long targetId, String title, String body)` + `dedupeKey` 오버로드 — 호출자 트랜잭션 참여, 중복 키면 저장 생략 / `long countUnread(Long userId)` |
| C8 | 최종 과제 저장 | 팀원 4 `WorkService` | 팀원 3(#13) | `Long saveFinalWorkVersion(Long userId, Long enrollmentId, Long hobbyId, CourseLevel level, String title, String description, List<MultipartFile> images)`, `void updateFinalWorkVersion(Long userId, Long workVersionId, String title, String description, List<MultipartFile> images)` — 실패 시 파일 정리 후 예외 |
| C9 | 작품 열람 권한 | 팀원 4 `WorkAccessPolicy` | 팀원 3 | 이미지 URL `/files/works/{fileId}`만 사용 |
| C10 | 진도 | 팀원 3 `ProgressService` | 팀원 2(#11), 팀원 1(#07) | `boolean isAllCompleted(Long enrollmentId)`, `List<LessonProgressDTO> getLessonStatuses(Long enrollmentId)` |
| C11 | 평가 상태 | 팀원 3 `ReviewQueryService` | 팀원 2·1·4 | `boolean hasPendingReview(Long enrollmentId)`, `ReviewStatusDTO getLatestStatus(Long enrollmentId)`, `Optional<FeedbackRefDTO> findLatestFeedbackForReference(Long userId, Long hobbyId)` |
| C12 | 배지 | 팀원 3 `BadgeService` | 팀원 4(#18·#19) | `List<BadgeDTO> getBadges(Long userId)`, `CourseLevel getHighestLevel(Long userId, Long hobbyId)`, `boolean hasBadge(Long userId, Long hobbyId, CourseLevel level)` |
| C13 | 상태값 enum | #03 | 전원 | §2 (DB CHECK 값과 일치) |
| C14 | 화면 공통 | 팀원 1 `common/layout.html` | 전원 | `th:replace="~{common/layout :: gnb(menu='…')}"`, 사이드 `teacherSide(active=…)`·`adminSide(active=…)`, 바닥글 `footer`. 모든 화면 공통 모델 `loginUser`·`unreadCount`·`headerNotifications`는 `GlobalModelAdvice`가 넣는다 |
| C15 | URL·템플릿 경로 | #01 | 전원 | §5·§6 |

호출자 트랜잭션에 참여해야 하는 메서드(C4·C5·C7)는 **별도 트랜잭션으로 열지 않는다.**

## 4. ErrorCode 전체 목록 ✅

`global/exception/ErrorCode.java`에 정의됨. **이후 추가는 팀원 1에게 요청한다.**

- 공통: INVALID_INPUT, ACCESS_DENIED, NOT_FOUND, INTERNAL_ERROR
- 계정: DUPLICATE_LOGIN_ID, DUPLICATE_EMAIL, DUPLICATE_NICKNAME, EMAIL_NOT_VERIFIED, INVALID_TOKEN, TOKEN_EXPIRED, USER_NOT_FOUND, PASSWORD_CHANGE_REQUIRED, INVALID_NEW_PASSWORD
- 탐색: HOBBY_NOT_FOUND, HOBBY_NOT_OPEN, COURSE_NOT_FOUND, LESSON_NOT_FOUND
- 수강: ALREADY_ENROLLED, ALREADY_WAITING, CANNOT_REAPPLY, NO_ASSIGNABLE_SECTION, ENROLLMENT_NOT_FOUND, ENROLLMENT_EXPIRED, CANCEL_NOT_ALLOWED_DURING_REVIEW, SECTION_NOT_FOUND, CAPACITY_OUT_OF_RANGE, CAPACITY_BELOW_ACTIVE, INSTRUCTOR_NOT_GRANTED
- 학습: VIDEO_ACCESS_DENIED
- 과제·평가: LESSONS_NOT_COMPLETED, SUBMISSION_LOCKED, ALREADY_SUBMITTED, INVALID_IMAGE_COUNT, INVALID_IMAGE_FILE, FILE_TOO_LARGE, FILE_SAVE_FAILED, REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT, CANNOT_CANCEL_START, RESUBMIT_NOT_ALLOWED
- 작품: WORK_NOT_FOUND, WORK_ACCESS_DENIED, HOBBY_NOT_OPERATING, LEVEL_TAG_NOT_ALLOWED, COMMENT_NOT_ALLOWED, ALREADY_HIDDEN, NOT_HIDDEN
- 문의: INQUIRY_NOT_ALLOWED

규칙: 예외는 `BusinessException(ErrorCode)`로만 던진다. 다른 사람 데이터 접근은 존재를 숨기려고 `*_NOT_FOUND`(404)로 처리한다.

## 5. URL 접두사 규칙 ✅

| 구분 | URL | 접근 |
|---|---|---|
| 공개 | `/`, `/privacy`, `/hobbies/**`, `/courses/**`, `/auth/**`, `/signup/**`, 정적 리소스 | 누구나 |
| 학습자 | `/my/**`, `/enrollments/**`, `/lessons/**`, `/assignments/**`, `/inquiries/**`, `/videos/**` | USER |
| 로그인 사용자 | `/works/**`, `/comments/**`, `/notifications/**`, `/files/works/{fileId}`, `/my/account/**`(세 역할 공용) | 로그인 |
| 강사 | `/instructor/**` (첫 로그인 비밀번호 변경은 `/auth/instructor/**`) | INSTRUCTOR |
| 관리자 | `/admin/**` | ADMIN |
| 파일 | `/files/works/{fileId}`, `/videos/{lessonId}` | 권한 검사 후 출력 |

- 규칙 구현 위치: `SecurityConfig`(좁은 패턴을 먼저 쓴다). 그 밖의 URL은 로그인 필요.
- 모든 변경 요청은 `POST`로 통일한다(HTML 폼이 GET/POST만 지원).
- 비로그인 → 로그인 화면, 권한 없음 → 403 화면, 없는 URL(로그인 상태) → 404 화면.

## 6. 뷰 규칙 ✅

- 템플릿 폴더 = 도메인명(`templates/hobby/…`, `templates/work/…`). 컨트롤러가 반환하는 뷰 이름은 템플릿 경로와 같다.
- 공통 레이아웃: `common/layout.html`의 `gnb`·`teacherSide`·`adminSide`·`footer` 조각을 쓴다. 화면 쪽에서는 `menu`·`active` 값만 맞추고 머리글·사이드·바닥글을 직접 고치지 않는다.
- 완료·오류 안내는 flash 속성 `message` / `errorMessage`.
- 사용자가 입력한 글은 `th:text`로만 출력한다(`th:utext` 금지). 학습자 실명은 강사·관리자 화면에만 보인다.
- 템플릿의 태그·`class` 구조와 `static/css`는 바꾸지 않는다(값·반복·조건·링크·폼만 연결).

## 7. 로그인 사용자 꺼내는 방법 ✅

- 컨트롤러 파라미터: `@AuthenticationPrincipal AuthDetails details` → `details.getUserId()`, `details.getRole()`
- 비로그인이면 `null`이므로, 로그인이 필요한 URL은 `SecurityConfig`가 먼저 막는다.
- 권한 판단(본인 것인지, 담당 강사인지)은 화면이 아니라 **Service·Policy**에서 한다.

## 8. 브랜치·커밋 규칙 💡

자료에 없던 항목이라 제안값입니다.

- `main` 브랜치 보호(직접 push 금지, PR 필수)
- 작업 브랜치: `feature/#이슈번호-짧은이름` (예: `feature/#04-signup`)
- PR 제목: `[#번호] 이슈 제목`, 병합 방식은 **Squash merge**
- PR 전 체크: `./gradlew build` 성공, 이슈의 완료 조건·테스트 항목 확인, 다른 팀원 소유 파일을 고쳤다면 PR 본문에 표시
- 이슈 #02(기반)를 **가장 먼저 리뷰·merge**하고, 그 이후 이슈가 이어서 진행한다.

## 9. 시간·메일 등 공통 처리 규칙 💡

#04 작업 중 확인된 사항을 모두가 같은 방식으로 쓰기 위한 규칙입니다.

- **현재 시각은 `LocalDateTime.now()`를 쓴다.** `application.yaml`에 `hibernate.jdbc.time_zone: UTC`가 있어서 Hibernate가 `LocalDateTime`을 "JVM 기본 시간대 시각"으로 보고 DB(UTC)에 바꿔 저장한다. `LocalDateTime.now(ZoneOffset.UTC)`를 넣으면 한국 서버에서 9시간 어긋나 `CHECK(expires_at > created_at)` 같은 DB 제약이 깨진다(#04 테스트로 확인). 화면 표시만 KST로 바꾼다.
- **토큰:** 원문은 메일에만 담고 DB에는 SHA-256 해시만 저장한다. 발급·검증은 `EmailVerificationService.issueToken / consumeToken`을 `TokenPurpose`만 바꿔 재사용한다(#06).
- **메일:** SMTP가 없으면(`spring.mail.host` 미설정) 메일을 보내지 않고 인증 링크를 서버 로그(WARN)에 남긴다 — 개발·시연 전용. 운영에서는 반드시 SMTP를 설정한다.
- **민감 정보**(비밀번호, 토큰 원문, 임시 비밀번호, 실명)는 로그에 남기지 않는다. `System.out` 디버그 출력을 남기지 않는다.
- **DTO의 `@AllArgsConstructor`:** 필드가 없는 DTO는 `@NoArgsConstructor`와 충돌하므로 주석 처리해 두었다. **필드를 추가할 때 주석을 풀어 복원한다**(`grep -rl "^// @AllArgsConstructor" src/main/java`로 남은 파일 확인).
- 기획 수치는 이름 있는 상수로 둔다(예: 30일·7일 기한, 반려 사유 50자, 이미지 5장).

## 10. 미확정 사항 담당자·기한 지정 💡

작업분배 §8과 API 명세 §16을 기준으로, **임시로 적용한 값**과 **결정 담당·기한**을 정했습니다. 기한은 일정(마감일)이 없어서 "해당 이슈 구현 시작 전"을 기본으로 제안합니다. 확정되면 상태를 ✅로 바꾸고 해당 이슈 본문·API 명세의 ❓를 지웁니다.

| # | 항목 | 임시 적용값 💡 | 결정 담당 | 기한 | 영향 |
|---|---|---|---|---|---|
| 1 | 인증·재설정 토큰 만료 시간 | **24시간** (`app.token.verify-email-hours`) | 팀원 1 | #04 PR 전 | #04·#06 |
| 2 | 메일 발송 실패 시 가입 롤백 | **롤백**(인증 대기 계정이 아이디·이메일을 점유하지 않게) | 팀원 1 | #04 PR 전 | #04 |
| 3 | SMTP 계정 / 없을 때 확인 방법 | 계정 준비 시 사용, 없으면 **서버 로그 링크**(§9) | 팀원 1 | #04 PR 전 | #04·#06 |
| 4 | 아이디·닉네임 길이, 비밀번호 문자 종류 | 아이디 **4~20자**, 닉네임 **2~20자**, 비밀번호 8~16자 + **영문·숫자·특수문자 모두 필수** | 팀원 1 | #04 PR 전 | #04·#05·#06 |
| 5 | 임시 비밀번호 생성 규칙 | 12자 영문·숫자·특수문자 무작위, 발급 화면에 **1회만 표시**, 평문 미저장 | 팀원 1 | #05 시작 전 | #05 |
| 6 | CSRF 사용 여부 | 개발 중 **`disable`**(수업 방식), 배포 전 재검토. 켜면 fetch(JSON)는 헤더에 토큰 필요 | 팀원 1 | 배포 전 | 모든 POST |
| 7 | multipart `max-request-size` | **300MB**(5×50MB=250MB + 여유, `application.yaml` 반영) — #16에서 업로드 테스트 후 조정 | 팀원 1·4 | #16 PR 전 | #16·#13 |
| 8 | 405 오류 화면 | `error/405.html` 별도 사용(생성 완료) | 팀원 1 | — | #02 |
| 9 | 메인 홈에 보여줄 항목 | 현재 `main/home.html`(시안 샘플) 사용, 취미·이어서 학습은 #08·#11 이후 연결 | 팀원 1 | #11 이후 | CM-01 |
| 10 | 영상 파일(개수·길이·저장 위치), 구간 이동 | 차시당 영상 1개(27개), 구간 이동 지원 여부는 #12에서 결정 | 팀 | #12 시작 전 | #03·#12 |
| 11 | 재생 보고 주기·최대 인정 시간 | **5초**(DEV_REVIEW 후보) | 팀원 3 | #12 시작 전 | #12 |
| 12 | 재생 중 로그인 연장·일시정지 2시간(FR-005) | 이번 범위에서 **제외** | 팀 | — | #06 |
| 13 | 만료 스케줄러 실행 주기 | **매시간 정각**(30일 기한이라 시 단위로 충분) | 팀원 2 | #10 시작 전 | #10 |
| 14 | 페이지 크기(게시판·알림·평가 목록) | **12개**(목록), 알림 **20개** | 팀원 4 | #18 시작 전 | #17·#18·#20 |
| 15 | 숨김·복구 사유 최소 길이, 문의 글자 수 | 숨김 사유 **10자 이상**(상한 1000), 문의 제목 200 / 본문 TEXT(DB 최대값 기준) | 팀원 4 | #20·#21 시작 전 | #20·#21 |
| 16 | 아이디 찾기·비밀번호 재설정 입력값 | **아이디+이메일** 일치 확인(재설정), 이메일만(아이디 찾기) | 팀원 1 | #06 시작 전 | #06 |
| 17 | 계정 설정에서 수정 가능한 항목 | 닉네임·비밀번호 변경 | 팀원 1 | #06 시작 전 | #06 |
| 18 | 승인 과제 수정·삭제(D-63) | **구현하지 않음**(제출·재제출 버전 저장만) | 팀 | — | #13·#19 |
| 19 | 세부 기본값(CHANGELOG 11-05): 동률 시 분반 번호순, 정원은 현재 수강 미만 축소 불가, 첫 로그인 변경 강제, 평가 대기·진행 중 취소 불가 | **이 값으로 설계** | 팀 | 이슈 시작 전 | #05·#09·#10 |
| 20 | 테스트 코드 범위 | P0 핵심 규칙만 `@SpringBootTest`(🟡 허용) | 팀 | — | 전체 |
| 21 | 일정(마감일) | 미정 — 확정되면 위 기한을 날짜로 바꾼다 | 팀 | 즉시 | 전체 |

> 확정 사실(근거 있음): DB 스키마·상태값(11-06), 패키지명, 화면 기술 Thymeleaf, 강의·카테고리는 시드 데이터, 약관·만 14세 확인 제외, 회원가입 검증은 HTML 기준, 반려 사유 50자, 이메일 알림 없음, 강사 통계는 현재 숫자만.

---

## 확인 기록 (이슈 #01 완료 조건)

팀원 4명이 이 문서를 읽고 **이슈 #01 댓글로 "확인"** 을 남기면 아래에 표시합니다.

| 팀원 | 확인 | 날짜 | 비고 |
|---|---|---|---|
| 팀원 1 | ☐ | | |
| 팀원 2 | ☐ | | |
| 팀원 3 | ☐ | | |
| 팀원 4 | ☐ | | |

## 변경 이력

| 날짜 | 내용 | 작성 |
|---|---|---|
| 2026-11-09 | 초안 작성 (#04 진행 중 정해진 값 포함, 제안값은 💡) | 팀원 1 |
