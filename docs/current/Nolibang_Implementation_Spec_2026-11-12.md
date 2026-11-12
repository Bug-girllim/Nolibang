# 놀이방 LMS · 기능·API·패키지 명세

2026-11-12.

## 1. 기능 명세

| 기획 ID | 현재 구현 및 경계 | 대표 Controller → Service → Entity·데이터 |
| --- | --- | --- |
| F-01 가입·인증 | 가입·중복 확인·메일 인증·계정 복구. 약관 동의와 연령 확인은 제외. 첫 로그인 임시 비밀번호 변경과 중복 로그인 세션 만료 제공 | `SignupController` → `UserService`; `EmailVerifyController` → `EmailVerificationService` → `User`, `AuthToken` |
| F-02 탐색 | 대분류·세부 취미 탐색, 취미 상세와 강의 진입 | `HobbyController` → `HobbyService` → `Category`, `Hobby` |
| F-03 수강 | 강의 상세에서 신청, 담당 강사가 있는 분반 중 여석 기준 배정. 확정일부터 30일 | `CourseController` → `CourseCatalogService`; `EnrollmentController` → `EnrollmentService` → `CourseSection`, `Enrollment` |
| F-04 영상·진도 | 수강 소유권·강의 일치·기간 확인. 서버 관측 시간을 기준으로 진도를 누적하며 60%에서 완료. 수료자는 재시청 가능 | `LessonController`, `LessonVideoController` → `ProgressService` → `LessonProgress`, `Lesson` |
| F-05 제출·평가 | 최초 제출, 평가 시작 전 수정, 반려 후 재제출. 평가 시작·시작 취소·초안·승인·반려 | `AssignmentController`, `InstructorReviewController` → `AssignmentService`, `ReviewService` → `ReviewRound`, `Work`, `WorkVersion` |
| F-06 수료·배지 | 승인 시 수강 수료, 자리 반환, 결과·수료 알림. 취미별 수준·마스터 배지를 수료 기록에서 산출 | `InstructorReviewController`, `MyLearningController` → `ReviewService`, `EnrollmentService`, `BadgeService` → `ReviewRound`, `Enrollment` |
| F-07 작품·성장 기록 | 게시판·내 작품·회고·공개 전환·성장 타임라인. 작성자 일반 작품 논리 삭제 지원 | `MyWorkController`, `WorkBoardController` → `WorkService`, `GrowthService`, `WorkBoardService` → `Work`, `WorkVersion`, `WorkFile` |
| F-08 댓글·좋아요 | 댓글과 1단계 답글, 자기 댓글 수정·삭제, 좋아요 토글 | `CommentController`, `WorkBoardController` → `CommentService` → `WorkComment`, `WorkLike` |
| F-09 평가·알림 | 공식 평가 피드백과 별도 문의 흐름. 알림 목록·개별 열기·모두 읽음·미읽음 수 제공. 마감 임박 알림 유형은 스키마에서 제외 | `AssignmentController`, `NotificationController` → `ReviewResultService`, `NotificationService` → `ReviewRound`, `Notification` |
| F-10 운영 조치 | 관리자 공개 작품 숨김·복구, 사유와 조치 이력 보존 | `AdminWorkController` → `WorkModerationService` → `Work`, `WorkModerationLog` |
| F-11 콘텐츠 운영 | D-59 시드 중심 설계 이후 관리자 대분류·세부 취미 편집 및 일부 대분류 삭제, 강의 편집, 관리자·강사 영상 업로드·차시 연결 추가 | `AdminHobbyController`, `AdminVideoController`, `InstructorVideoController` → `HobbyService`, `CourseService`, `LessonVideoService` → `Category`, `Hobby`, `Course`, `Lesson` |
| F-12 강사·분반 | 임시 비밀번호 발급·재발급, 활성 상태·취미 권한, 분반 생성·정원·담당 변경 | `AdminInstructorController`, `AdminSectionController` → `InstructorAccountService`, `SectionService` → `User`, `InstructorHobbyGrant`, `CourseSection` |
| F-13 탈퇴 | 활성 학습자 탈퇴, 진행 중 수강 검사, 데이터 정리, 이메일 확인값 30일 보관 | `AccountController` → `WithdrawalService`, `WithdrawnEmailHoldService` → `User`, `WithdrawnEmailHold` 및 연관 엔티티 |
| F-14 1:1 문의 | 학습자 문의 개설·후속 메시지와 담당 강사 답변 | `InquiryController`, `InstructorInquiryController` → `InquiryService` → `InquiryThread`, `InquiryMessage` |
| F-15 평가 기한 | 평가 목록에 기한 필터 제공. P2 경고 누적·담당 자격 정지 기능은 현재 범위 밖 | `InstructorReviewController` → `ReviewService` → 평가 조회·`ReviewRound` |
| F-16 강사 통계 | 강사 홈·수강생·분반 통계. 관리자는 별도 통계 화면 | `InstructorHomeController`, `InstructorStudentController`, `InstructorStatController`, `AdminStatController` → `InstructorDashboardService`, `InstructorStatService`, `AdminDashboardService` → 통계 조회 |
| F-17 대기열 | 단일 FIFO, 자리 발생 시 자동 확정. 관리자 대기 항목 취소 추가 | `EnrollmentController`, `AdminWaitlistController` → `WaitlistService`, `SeatService` → `WaitlistEntry`, `Enrollment` |
| F-18 재신청 | 동일 강의 수강 기록이 있으면 재신청을 제한. 대기 중 중복 신청도 제한 | `EnrollmentController` → `EnrollmentService` → 신청 자격 검사 및 수강·대기 고유 제약 |
| 기획 이후 RPG | 캐릭터·퀘스트·보스·길드·장착. XP·순위는 활동에서 계산하고 수령·장착·업적 상태를 저장 | `GameController` → `GameService`, `GameStatQuery` → `quest_claims`, `user_avatars`, `user_achievements`, `user_equipped_badges` |

기획 이후 추가된 관리 기능과 RPG의 공식 정책 채택 여부는 팀 결정 기록에서 확인해야 한다. 공개 프로필의 `ProfileController`는 클래스 접두사만 선언하며 요청을 처리할 핸들러는 없다.

## 2. HTTP 계약

Thymeleaf 화면 조회와 폼 전송이 기본이다. Controller에 따라 같은 화면에 입력 오류를 표시하거나 리다이렉트와 flash 메시지를 사용한다. JSON·파일 응답은 아래 계약과 전체 목록에서 확인한다.

| 경로 | 인증·권한 |
| --- | --- |
| 홈·개인정보 처리방침·취미·강의·가입·일반 인증 | 공개. 강사 비밀번호 변경 경로는 강사 전용 |
| `/my/account/**` | 로그인 공통. 탈퇴는 Controller와 Service에서 학습자로 추가 제한 |
| `/my/**`, `/enrollments/**`, `/lessons/**`, `/videos/**`, `/assignments/**`, `/inquiries/**` | 학습자. 소유권·기간 등 추가 검사는 서비스에서 수행 |
| `/works/**`, `/comments/**`, `/files/works/**`, `/notifications/**` | 로그인 필요. 작품 가시성·작성자·담당자 조건은 요청별로 적용 |
| `/instructor/**`, `/admin/**` | 각각 강사, 관리자 전용 |

Spring Security는 세션 인증, POST 폼의 CSRF 검사, `POST /auth/login` 및 `POST /auth/logout`을 처리한다. 로그인 입력은 `loginId`, `password`다. 비로그인 보호 화면은 `/error/401`로 이동하며, 접근 거부는 `/error/403`으로 처리한다.

| 메서드·경로 | 요청 및 응답 |
| --- | --- |
| `GET /signup/check-id`, `GET /signup/check-nickname` | 각각 `loginId`, `nickname` 쿼리. 사용 가능 여부를 JSON boolean으로 반환 |
| `POST /signup` | 폼 `loginId`, `password`, `passwordConfirm`, `name`, `email`, `nickname`. 검증 오류는 가입 화면, 성공은 이메일 인증 안내 |
| `POST /enrollments` | 폼 `courseId`. 즉시 수강 확정 또는 대기 결과를 화면에서 안내 |
| `GET /lessons/{lessonId}` | `enrollmentId` 쿼리 필수. 차시 화면 |
| `POST /lessons/{lessonId}/progress` | 폼 `enrollmentId`, `streamKey`, `seq`, `playing`, `positionSec`. JSON `LessonProgressDTO`에 비율·완료 여부·완료 시각 등 반환 |
| `GET /videos/{lessonId}` | `enrollmentId` 필수, `Range` 선택. 권한 검사 후 로컬 MP4 200/206, 잘못된 범위 416 또는 지원 저장소의 서명 URL로 302 |
| `POST /assignments`, `POST /assignments/resubmit` | 멀티파트 `enrollmentId`, `title`, `description`, `images`. 최초 제출 또는 반려 후 재제출 |
| `POST /instructor/reviews/{reviewRoundId}/approve`, `…/reject` | 폼 `content`. 담당 및 상태 검증 후 평가 처리·화면 이동 |
| `POST /works/{workId}/like` | 작품 ID와 CSRF. JSON `liked`, `likeCount` |
| `GET /notifications/unread-count` | 로그인 사용자의 미읽음 수를 JSON 숫자로 반환 |
| `POST /my/account/withdraw` | 폼 `currentPassword`, `agree`. 성공 시 로그아웃하고 `/auth/withdrawn`으로 이동 |
| `POST /my/character/equip`, `…/badges/equip`, `…/badges/unequip` | 일반 폼은 리다이렉트. `X-Requested-With: XMLHttpRequest` 요청은 JSON `success`, `message` |

### 기존 API 명세에서 달라진 계약

| 기존 ID | 기존 명세 | 현재 코드 |
| --- | --- | --- |
| AU-08·AU-10 | 아이디 찾기·비밀번호 재설정 메일 요청을 서로 다른 POST로 기술 | 두 발송 흐름을 `POST /auth/recovery/send`의 `email`, `tab` 입력으로 처리 |
| AU-12 | `POST /auth/recovery/password/reset` | `POST /auth/recovery/password` |
| US-08 | `GET /profiles/{userId}` 예정 | 매핑 핸들러 없음 |
| LE-02 | JSON 응답을 `ProgressReportDTO`로 기술 | 폼 입력은 `ProgressReportDTO`, JSON 출력은 `LessonProgressDTO` |
| NT-04 | 미읽음 수 조회를 선택 항목으로 기재 | `GET /notifications/unread-count` 구현 |
| 추가 | 탈퇴·작품 직접 삭제·영상 관리·RPG 계약 일부 미기재 | 실제 경로를 아래 도메인별 목록에 수록 |

## 3. 패키지 구조

루트 패키지는 `com.wanted.lmspractice`다. `domain`은 업무별로 Controller·Service·Repository·Entity·DTO·Policy 등을 둔다. `global`은 보안·MVC 설정, 공통 컨트롤러, 예외, 파일 저장을 담당한다. 업무별 조회 전용 Query와 Scheduler는 아래 실제 패키지에 포함한다. 영속 엔티티는 데이터 구조를, 서비스는 트랜잭션과 업무 규칙을, Controller와 DTO는 HTTP 입출력을 담당한다.

| 도메인 | 실제 하위 패키지 | 주요 Service · Entity |
| --- | --- | --- |
| `domain.admin` | `controller`, `dto`, `service` | `AdminDashboardService` |
| `domain.auth` | `controller`, `dto`, `handler`, `service` | `AuthService`, `EmailVerificationService`, `LoginSessionService` |
| `domain.course` | `controller`, `dto`, `entity`, `repository`, `service` | `CourseCatalogService`, `CourseService`, `LessonVideoService`, `Course`, `CourseLevel`, `CourseLevelConverter`, `CourseStatus`, `Lesson` |
| `domain.enrollment` | `controller`, `dto`, `entity`, `policy`, `repository`, `scheduler`, `service` | `EnrollmentExpiryService`, `EnrollmentService`, `SeatService`, `SectionService`, `WaitlistService`, `CourseSection`, `Enrollment`, `EnrollmentStatus`, `WaitlistEntry`, `WaitlistStatus` |
| `domain.game` | `controller`, `dto`, `entity`, `policy`, `repository`, `service` | `AuthorStyleService`, `GameService`, `AvatarItem`, `QuestClaim`, `QuestClaimId`, `UserAchievement`, `UserAchievementId`, `UserAvatar`, `UserEquippedBadge`, `UserEquippedBadgeId` |
| `domain.hobby` | `controller`, `dto`, `entity`, `repository`, `service` | `HobbyCatalogCache`, `HobbyService`, `Category`, `ExposureState`, `Hobby` |
| `domain.inquiry` | `controller`, `dto`, `entity`, `repository`, `service` | `InquiryService`, `InquiryMessage`, `InquiryStatus`, `InquiryThread` |
| `domain.instructor` | `controller`, `dto`, `repository`, `service` | `InstructorDashboardService`, `InstructorHobbyService`, `InstructorStatService` |
| `domain.learning` | `controller`, `dto`, `entity`, `policy`, `repository`, `service` | `MyLearningService`, `ProgressService`, `LessonProgress`, `LessonProgressId` |
| `domain.notification` | `controller`, `dto`, `entity`, `repository`, `service` | `NotificationLinkResolver`, `NotificationService`, `Notification`, `NotificationKind`, `NotificationTargetType` |
| `domain.review` | `controller`, `dto`, `entity`, `policy`, `repository`, `service` | `AssignmentService`, `BadgeService`, `ReviewQueryService`, `ReviewResultService`, `ReviewService`, `ReviewRound`, `ReviewStatus` |
| `domain.user` | `controller`, `dto`, `entity`, `policy`, `repository`, `scheduler`, `service` | `InstructorAccountService`, `UserService`, `WithdrawalService`, `WithdrawnEmailHoldService`, `AuthToken`, `InstructorHobbyGrant`, `InstructorHobbyGrantId`, `Role`, `TokenPurpose`, `User`, `UserStatus`, `WithdrawnEmailHold` |
| `domain.work` | `controller`, `dto`, `entity`, `policy`, `repository`, `service` | `CommentService`, `GrowthService`, `WorkBoardService`, `WorkModerationService`, `WorkService`, `CommentStatus`, `ModerationAction`, `Visibility`, `Work`, `WorkComment`, `WorkFile`, `WorkKind`, `WorkLike`, `WorkLikeId`, `WorkModerationLog`, `WorkVersion` |

## 4. Controller 요청 목록

요청 핸들러 130개를 소스 어노테이션에서 추출했다. 동일 메서드·URL에 AJAX 전용 매핑이 있는 경우 각각 표시했다. Spring Security가 직접 처리하는 로그인·로그아웃 POST는 위 HTTP 계약을 참고한다. `ANY`는 HTTP 메서드를 지정하지 않은 매핑이다.

### admin

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/admin` | [AdminHomeController.home](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminHomeController.java) | 화면·리다이렉트 |
| GET | `/admin/stats` | [AdminStatController.stats](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminStatController.java) | 화면·리다이렉트 |
| GET | `/admin/videos` | [AdminVideoController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminVideoController.java) | 화면·리다이렉트 |
| POST | `/admin/videos` | [AdminVideoController.upload](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminVideoController.java) | 화면·리다이렉트 |
| GET | `/admin/videos/file` | [AdminVideoController.preview](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminVideoController.java) | 파일 응답 |
| POST | `/admin/videos/link` | [AdminVideoController.link](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminVideoController.java) | 화면·리다이렉트 |
| POST | `/admin/videos/delete` | [AdminVideoController.delete](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/admin/controller/AdminVideoController.java) | 화면·리다이렉트 |

### auth

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/auth/recovery` | [AccountRecoveryController.recoveryForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AccountRecoveryController.java) | 화면·리다이렉트 |
| POST | `/auth/recovery/send` | [AccountRecoveryController.send](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AccountRecoveryController.java) | 화면·리다이렉트 |
| GET | `/auth/recovery/login-id` | [AccountRecoveryController.showLoginId](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AccountRecoveryController.java) | 화면·리다이렉트 |
| GET | `/auth/recovery/password` | [AccountRecoveryController.passwordForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AccountRecoveryController.java) | 화면·리다이렉트 |
| POST | `/auth/recovery/password` | [AccountRecoveryController.resetPassword](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AccountRecoveryController.java) | 화면·리다이렉트 |
| GET | `/auth/login` | [AuthController.loginForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AuthController.java) | 화면·리다이렉트 |
| GET | `/auth/withdrawn` | [AuthController.withdrawn](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/AuthController.java) | 화면·리다이렉트 |
| GET | `/auth/verify/sent` | [EmailVerifyController.sent](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/EmailVerifyController.java) | 화면·리다이렉트 |
| GET | `/auth/verify` | [EmailVerifyController.verify](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/EmailVerifyController.java) | 화면·리다이렉트 |
| POST | `/auth/verify/resend` | [EmailVerifyController.resend](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/EmailVerifyController.java) | 화면·리다이렉트 |
| GET | `/auth/instructor/password` | [InstructorSetupController.passwordForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/InstructorSetupController.java) | 화면·리다이렉트 |
| POST | `/auth/instructor/password` | [InstructorSetupController.changePassword](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller/InstructorSetupController.java) | 화면·리다이렉트 |

### course

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/courses` | [CourseController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/course/controller/CourseController.java) | 화면·리다이렉트 |
| GET | `/courses/{courseId}` | [CourseController.detail](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/course/controller/CourseController.java) | 화면·리다이렉트 |

### enrollment

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/admin/sections` | [AdminSectionController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminSectionController.java) | 화면·리다이렉트 |
| POST | `/admin/sections` | [AdminSectionController.create](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminSectionController.java) | 화면·리다이렉트 |
| POST | `/admin/sections/{sectionId}/capacity` | [AdminSectionController.changeCapacity](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminSectionController.java) | 화면·리다이렉트 |
| POST | `/admin/sections/{sectionId}/instructor` | [AdminSectionController.changeInstructor](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminSectionController.java) | 화면·리다이렉트 |
| GET | `/admin/waitlists` | [AdminWaitlistController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminWaitlistController.java) | 화면·리다이렉트 |
| POST | `/admin/waitlists/{waitlistId}/cancel` | [AdminWaitlistController.cancel](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/AdminWaitlistController.java) | 화면·리다이렉트 |
| POST | `/enrollments` | [EnrollmentController.apply](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/EnrollmentController.java) | 화면·리다이렉트 |
| POST | `/enrollments/waitlist/{waitlistId}/cancel` | [EnrollmentController.cancelWaiting](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/EnrollmentController.java) | 화면·리다이렉트 |
| POST | `/enrollments/{enrollmentId}/cancel` | [EnrollmentController.cancel](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/controller/EnrollmentController.java) | 화면·리다이렉트 |

### game

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/my/character` | [GameController.character](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |
| POST | `/my/character/quests/claim` | [GameController.claimQuest](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |
| POST | `/my/character/equip` | [GameController.equip](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |
| POST | `/my/character/equip` | [GameController.equipAjax](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | JSON (AJAX) |
| POST | `/my/character/badges/equip` | [GameController.equipBadge](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |
| POST | `/my/character/badges/equip` | [GameController.equipBadgeAjax](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | JSON (AJAX) |
| POST | `/my/character/badges/unequip` | [GameController.unequipBadge](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |
| POST | `/my/character/badges/unequip` | [GameController.unequipBadgeAjax](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | JSON (AJAX) |
| GET | `/my/guild` | [GameController.guild](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/game/controller/GameController.java) | 화면·리다이렉트 |

### hobby

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/admin/hobbies` | [AdminHobbyController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/hobbies/categories/{categoryId}/state` | [AdminHobbyController.changeCategoryState](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/hobbies/{hobbyId}/state` | [AdminHobbyController.changeHobbyState](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| GET | `/admin/categories` | [AdminHobbyController.categories](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/categories` | [AdminHobbyController.createCategory](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/categories/{categoryId}` | [AdminHobbyController.updateCategory](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/categories/{categoryId}/delete` | [AdminHobbyController.deleteCategory](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| GET | `/admin/sub-hobbies` | [AdminHobbyController.subHobbies](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/sub-hobbies` | [AdminHobbyController.createHobby](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/sub-hobbies/{hobbyId}` | [AdminHobbyController.updateHobby](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| GET | `/admin/content-review` | [AdminHobbyController.contentReview](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| POST | `/admin/content-review/courses/{courseId}` | [AdminHobbyController.updateCourse](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/AdminHobbyController.java) | 화면·리다이렉트 |
| GET | `/hobbies` | [HobbyController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/HobbyController.java) | 화면·리다이렉트 |
| GET | `/hobbies/{hobbyId}` | [HobbyController.detail](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/HobbyController.java) | 화면·리다이렉트 |
| GET | `/hobbies/image-files/{name}` | [HobbyImageController.image](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/hobby/controller/HobbyImageController.java) | 이미지 파일 응답 |

### inquiry

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/inquiries` | [InquiryController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InquiryController.java) | 화면·리다이렉트 |
| GET | `/inquiries/{threadId}` | [InquiryController.thread](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InquiryController.java) | 화면·리다이렉트 |
| POST | `/inquiries` | [InquiryController.write](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InquiryController.java) | 화면·리다이렉트 |
| POST | `/inquiries/{threadId}/messages` | [InquiryController.addMessage](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InquiryController.java) | 화면·리다이렉트 |
| GET | `/instructor/inquiries` | [InstructorInquiryController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InstructorInquiryController.java) | 화면·리다이렉트 |
| GET | `/instructor/inquiries/{threadId}` | [InstructorInquiryController.thread](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InstructorInquiryController.java) | 화면·리다이렉트 |
| POST | `/instructor/inquiries/{threadId}/answer` | [InstructorInquiryController.answer](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/inquiry/controller/InstructorInquiryController.java) | 화면·리다이렉트 |

### instructor

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/instructor/hobbies` | [InstructorHobbyController.hobbies](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorHobbyController.java) | 화면·리다이렉트 |
| GET | `/instructor` | [InstructorHomeController.home](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorHomeController.java) | 화면·리다이렉트 |
| GET | `/instructor/stats` | [InstructorStatController.stats](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorStatController.java) | 화면·리다이렉트 |
| GET | `/instructor/students` | [InstructorStudentController.students](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorStudentController.java) | 화면·리다이렉트 |
| GET | `/instructor/videos` | [InstructorVideoController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorVideoController.java) | 화면·리다이렉트 |
| POST | `/instructor/videos` | [InstructorVideoController.upload](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorVideoController.java) | 화면·리다이렉트 |
| GET | `/instructor/videos/file` | [InstructorVideoController.preview](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorVideoController.java) | 파일 응답 |
| POST | `/instructor/videos/link` | [InstructorVideoController.link](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorVideoController.java) | 화면·리다이렉트 |
| POST | `/instructor/videos/delete` | [InstructorVideoController.delete](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/instructor/controller/InstructorVideoController.java) | 화면·리다이렉트 |

### learning

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/lessons/{lessonId}` | [LessonController.lesson](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/LessonController.java) | 화면·리다이렉트 |
| POST | `/lessons/{lessonId}/progress` | [LessonController.report](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/LessonController.java) | JSON |
| GET | `/videos/{lessonId}` | [LessonVideoController.video](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/LessonVideoController.java) | 파일 응답 |
| GET | `/my/learning` | [MyLearningController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/MyLearningController.java) | 화면·리다이렉트 |
| GET | `/my/learning/apply` | [MyLearningController.apply](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/MyLearningController.java) | 화면·리다이렉트 |

### notification

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/notifications` | [NotificationController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/notification/controller/NotificationController.java) | 화면·리다이렉트 |
| GET | `/notifications/{notificationId}` | [NotificationController.open](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/notification/controller/NotificationController.java) | 화면·리다이렉트 |
| POST | `/notifications/read-all` | [NotificationController.readAll](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/notification/controller/NotificationController.java) | 화면·리다이렉트 |
| GET | `/notifications/unread-count` | [NotificationController.unreadCount](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/notification/controller/NotificationController.java) | JSON |

### review

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/assignments` | [AssignmentController.view](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/AssignmentController.java) | 화면·리다이렉트 |
| POST | `/assignments` | [AssignmentController.submit](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/AssignmentController.java) | 화면·리다이렉트 |
| POST | `/assignments/{reviewRoundId}/edit` | [AssignmentController.edit](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/AssignmentController.java) | 화면·리다이렉트 |
| POST | `/assignments/resubmit` | [AssignmentController.resubmit](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/AssignmentController.java) | 화면·리다이렉트 |
| GET | `/assignments/{reviewRoundId}/result` | [AssignmentController.result](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/AssignmentController.java) | 화면·리다이렉트 |
| GET | `/instructor/reviews` | [InstructorReviewController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| GET | `/instructor/reviews/{reviewRoundId}` | [InstructorReviewController.detail](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| POST | `/instructor/reviews/{reviewRoundId}/start` | [InstructorReviewController.start](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| POST | `/instructor/reviews/{reviewRoundId}/cancel-start` | [InstructorReviewController.cancelStart](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| POST | `/instructor/reviews/{reviewRoundId}/draft` | [InstructorReviewController.saveDraft](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| POST | `/instructor/reviews/{reviewRoundId}/approve` | [InstructorReviewController.approve](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |
| POST | `/instructor/reviews/{reviewRoundId}/reject` | [InstructorReviewController.reject](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/controller/InstructorReviewController.java) | 화면·리다이렉트 |

### user

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/my/account` | [AccountController.accountForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AccountController.java) | 화면·리다이렉트 |
| POST | `/my/account/withdraw` | [AccountController.withdraw](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AccountController.java) | 화면·리다이렉트 |
| POST | `/my/account/nickname` | [AccountController.updateNickname](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AccountController.java) | 화면·리다이렉트 |
| POST | `/my/account/password` | [AccountController.updatePassword](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AccountController.java) | 화면·리다이렉트 |
| GET | `/admin/instructors` | [AdminInstructorController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| POST | `/admin/instructors` | [AdminInstructorController.create](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| POST | `/admin/instructors/{instructorId}/reissue` | [AdminInstructorController.reissue](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| POST | `/admin/instructors/{instructorId}/status` | [AdminInstructorController.changeStatus](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| POST | `/admin/instructors/{instructorId}/hobbies` | [AdminInstructorController.grantHobby](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| POST | `/admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke` | [AdminInstructorController.revokeHobby](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AdminInstructorController.java) | 화면·리다이렉트 |
| GET | `/signup` | [SignupController.signupForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/SignupController.java) | 화면·리다이렉트 |
| POST | `/signup` | [SignupController.signup](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/SignupController.java) | 화면·리다이렉트 |
| GET | `/signup/check-id` | [SignupController.checkId](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/SignupController.java) | JSON |
| GET | `/signup/check-nickname` | [SignupController.checkNickname](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/SignupController.java) | JSON |

### work

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/admin/works` | [AdminWorkController.list](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/AdminWorkController.java) | 화면·리다이렉트 |
| POST | `/admin/works/{workId}/hide` | [AdminWorkController.hide](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/AdminWorkController.java) | 화면·리다이렉트 |
| POST | `/admin/works/{workId}/restore` | [AdminWorkController.restore](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/AdminWorkController.java) | 화면·리다이렉트 |
| POST | `/comments` | [CommentController.write](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/CommentController.java) | 화면·리다이렉트 |
| POST | `/comments/{commentId}/edit` | [CommentController.edit](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/CommentController.java) | 화면·리다이렉트 |
| POST | `/comments/{commentId}/delete` | [CommentController.delete](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/CommentController.java) | 화면·리다이렉트 |
| GET | `/my/works/new` | [MyWorkController.createForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| POST | `/my/works` | [MyWorkController.create](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| GET | `/my/works/{workId}/edit` | [MyWorkController.editForm](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| POST | `/my/works/{workId}/edit` | [MyWorkController.edit](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| POST | `/my/works/{workId}/delete` | [MyWorkController.delete](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| POST | `/my/works/{workId}/visibility` | [MyWorkController.changeVisibility](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| POST | `/my/works/{workId}/reflection` | [MyWorkController.saveReflection](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| GET | `/my/works/growth` | [MyWorkController.growth](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| GET | `/my/works/{workId}/moderation` | [MyWorkController.moderation](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/MyWorkController.java) | 화면·리다이렉트 |
| GET | `/works` | [WorkBoardController.board](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/WorkBoardController.java) | 화면·리다이렉트 |
| GET | `/works/{workId}` | [WorkBoardController.detail](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/WorkBoardController.java) | 화면·리다이렉트 |
| POST | `/works/{workId}/like` | [WorkBoardController.like](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/WorkBoardController.java) | JSON |
| GET | `/files/works/{fileId}` | [WorkFileController.image](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/controller/WorkFileController.java) | 이미지 파일 응답 |

### global

| 메서드 | 경로 | 핸들러 | 응답 |
| --- | --- | --- | --- |
| GET | `/` | [MainController.index](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |
| GET | `/privacy` | [MainController.privacy](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |
| ANY | `/error/403` | [MainController.forbidden](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |
| GET | `/error/401` | [MainController.loginRequired](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |
| GET | `/error/401-expired` | [MainController.sessionExpired](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |
| GET | `/error/401-duplicate` | [MainController.duplicateLogin](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java) | 화면·리다이렉트 |

## 근거 문서

- [11월 5일 기능 명세](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EA%B8%B0%ED%9A%8D%EC%95%88_11.05/04-features.md)
- [11월 5일 정책](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EA%B8%B0%ED%9A%8D%EC%95%88_11.05/05-policy.md)
- [기존 API 명세](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/API_%EB%AA%85%EC%84%B8%EC%84%9C.md)
- [기존 패키지 구조 설계](../진행_완/설계_기록/패키지_구조_설계.md)
- [보안 설정](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/config/SecurityConfig.java)
- [현재 SQL 스키마](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/resources/sql/schema.sql)
- [DB·정책 변경 정리](Nolibang_DB_Policy_2026-11-12.md)
