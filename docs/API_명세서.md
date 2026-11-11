# 놀이방 LMS — API 명세서

> **프로젝트:** `lms-practice` (Spring Boot 4.1.2 · Thymeleaf · Spring Security · JPA · MySQL)
> **기준 문서:** `패키지_구조_설계.md`, `작업분배_GitHub이슈_PR전략.md`, 기획 `04-features.md`·`05-policy.md`·`07-screens.md`
> **기준 코드:** `com.wanted.lmspractice` 스켈레톤 (컨트롤러 클래스·URL 접두사·ErrorCode는 이미 생성된 코드와 일치)
> **기준 DB:** `lms-practice/src/main/resources/sql/schema.sql` (팀원 스키마 + 11-06 팀 결정 반영) — 상태값·칼럼 길이는 DB 기준
> **작성일:** 2026-11-06 (DB 반영 수정 11-06)

## 범례

| 표시 | 의미 |
|---|---|
| ✅ | 기획 문서·이슈 문서에 근거가 있는 값 |
| 💡 | 자료에 없어 **제안한 값** — #01 킥오프에서 확정 |
| ❓ | 자료에 없고 정하지 않은 값 — 담당자가 결정 후 이 문서 수정 |
| **P0 / P1 / P2** | 기획 우선순위 (P2는 이번 범위 밖, 참고용으로만 표기) |
| 🔓 / 👤 / 🎓 / 🛠 | 비회원 포함 모두 / 로그인 학습자(USER) / 강사(INSTRUCTOR) / 관리자(ADMIN) |

---

## 목차

1. [공통 규칙](#1-공통-규칙)
2. [전체 엔드포인트 목록](#2-전체-엔드포인트-목록)
3. [상세 명세 — 공통·인증](#3-상세-명세--공통인증)
4. [상세 명세 — 회원·강사 계정](#4-상세-명세--회원강사-계정)
5. [상세 명세 — 취미·강의 탐색](#5-상세-명세--취미강의-탐색)
6. [상세 명세 — 수강·분반·대기열](#6-상세-명세--수강분반대기열)
7. [상세 명세 — 학습·진도·영상](#7-상세-명세--학습진도영상)
8. [상세 명세 — 최종 과제·평가](#8-상세-명세--최종-과제평가)
9. [상세 명세 — 작품·댓글·파일](#9-상세-명세--작품댓글파일)
10. [상세 명세 — 알림](#10-상세-명세--알림)
11. [상세 명세 — 1:1 문의](#11-상세-명세--11-문의)
12. [상세 명세 — 강사 LMS·관리자 홈·통계](#12-상세-명세--강사-lms관리자-홈통계)
13. [HTTP가 아닌 작업 (스케줄러)](#13-http가-아닌-작업-스케줄러)
14. [ErrorCode 표](#14-errorcode-표)
15. [상태값(enum) 표](#15-상태값enum-표)
16. [미확정 사항 목록](#16-미확정-사항-목록)

---

## 1. 공통 규칙

### 1-1. 이 프로젝트에서 "API"의 의미

이 프로젝트는 **Thymeleaf 서버 렌더링**이다. 대부분의 요청은 JSON이 아니라 **HTML 화면을 돌려주거나(VIEW) 다른 주소로 이동(REDIRECT)** 한다. 그래서 이 문서는 "URL + 요청 값 + 응답 화면/이동 위치 + 오류 처리"를 명세한다.

| 응답 유형 | 의미 | 컨트롤러 반환 | 배운 기술 |
|---|---|---|---|
| **VIEW** | Thymeleaf 템플릿을 그려서 HTML 응답 | `return "도메인/화면명";` + `Model` | ✅ chap01~02 |
| **REDIRECT** | 처리 후 다른 URL로 이동 (PRG 패턴) | `return "redirect:/경로";` + `RedirectAttributes.addFlashAttribute()` | ✅ ResolverController |
| **JSON** | 화면 일부만 갱신하는 작은 요청 | `@ResponseBody` 메서드 → 객체/boolean | ✅ 미니 프로젝트 `/menu/category` |
| **FILE** | 이미지·영상 바이너리 출력 | `HttpServletResponse` 출력 스트림 | ✅ Servlet 응답 출력 |

### 1-2. HTTP 메서드 규칙 ✅

- **조회 = `GET`, 변경 = `POST`** 두 가지만 쓴다. HTML `<form>`은 GET/POST만 보낼 수 있고, 수업에서 `PUT`·`DELETE` 매핑을 폼과 함께 쓰지 않았다.
- 수정·삭제·상태 변경은 `POST /자원/{id}/동작` 형태로 쓴다. 예: `POST /enrollments/{id}/cancel`
- JSON 요청 본문(`@RequestBody`)은 배우지 않았으므로 **JSON 응답 엔드포인트도 입력은 폼/쿼리 값(`@RequestParam`)** 으로 받는다.

### 1-3. URL 접두사와 접근 권한 ✅ (#01 §5, `SecurityConfig`에 등록)

| URL 패턴 | 권한 | 비고 |
|---|---|---|
| `/`, `/hobbies/**`, `/courses/**` | 🔓 permitAll | 비회원은 홈·취미 탐색·강의 상세까지 (D-08) |
| `/auth/**`, `/signup/**` | 🔓 permitAll | 단, `/auth/instructor/**`는 🎓 |
| `/css/**`, `/js/**`, `/images/**`, `/error/**` | 🔓 permitAll | 정적 리소스·에러 화면 |
| `/my/**`, `/enrollments/**`, `/lessons/**`, `/videos/**`, `/assignments/**` | 👤 USER | |
| `/works/**`, `/comments/**`, `/files/works/**` | 로그인 사용자 전체 | 공개 작품은 로그인 회원 열람(F-08). 세부 권한은 Policy에서 판단 |
| `/notifications/**` | 로그인 사용자 전체 | 역할별로 같은 화면 사용 💡 |
| `/inquiries/**` | 👤 USER | P1 |
| `/instructor/**`, `/auth/instructor/**` | 🎓 INSTRUCTOR | |
| `/admin/**` | 🛠 ADMIN | |
| `/profiles/**` | 🔓 | **P2** — 이번 범위 밖 |

> ❓ 비회원이 `/works/**`(공개 작품)를 볼 수 있는지: 기획 F-08은 "로그인 회원 열람"이므로 로그인 필수로 둔다.

### 1-4. 인증 방식 ✅

- **세션 + Spring Security `formLogin`** (JWT 아님).
- 로그인 사용자 정보는 컨트롤러 파라미터 `@AuthenticationPrincipal AuthDetails`로 꺼낸다 → `getUserId()`, `getRole()` (#01 §7).
- 세션 유휴 만료 **60분**(`server.servlet.session.timeout: 60m`), **한 계정 한 기기**(`maximumSessions(1)`, #06).
- 로그인하지 않은 상태로 보호 URL 접근 → `/auth/login`으로 이동. 권한이 없는 역할 → **403 화면**.
- 강사가 임시 비밀번호 상태(`mustChangePassword = true`)면 `PasswordChangeInterceptor`가 모든 요청을 `/auth/instructor/password`로 보낸다(로그아웃·정적 리소스 제외, #05).

### 1-5. CSRF ❓

- 수업에서는 `csrf.disable()`을 썼다. 세션 방식이라 운영 환경에서는 켜는 것이 권장된다(배운기술_정리 §Security).
- **켜는 경우:** Thymeleaf `th:action`을 쓴 폼은 토큰이 자동으로 들어간다. JSON 요청(`fetch`)은 헤더에 토큰을 직접 넣어야 한다.
- **결정 담당:** 팀원 1 (#02). 결정 전까지는 수업 방식(`disable`)으로 개발.

### 1-6. 화면 응답 공통 규칙 💡

| 상황 | 처리 |
|---|---|
| 처리 성공 후 이동 | `redirect:` + flash 속성 **`message`** (완료 안내, ✅ #01 §6) |
| 입력 형식 오류 (`@Valid` 실패) | **같은 폼 화면을 다시 그림** (`BindingResult`로 필드별 오류 표시, 입력값 유지) |
| 사용자가 고칠 수 있는 업무 오류 (**400·409** 계열 ErrorCode) | 원래 화면으로 `redirect:` + flash 속성 **`errorMessage`** (= `ErrorCode.getMessage()`) |
| 권한·없는 자원·서버 오류 (**403·404·500** 계열) | `BusinessException`을 던짐 → `GlobalExceptionHandler` → **`error/{status}` 화면**, 모델 `status`, `message` (✅ #02) |
| 본인 것이 아닌 자원 접근 | 존재를 숨기기 위해 **404**(`*_NOT_FOUND`)로 응답 💡 |

> 💡 위 규칙은 각 엔드포인트의 "실패" 칸에서 다시 쓰지 않는다. 실패 칸에는 **어떤 ErrorCode가 나는지만** 적는다.

### 1-7. JSON 응답 공통 규칙 💡

- 성공: 각 엔드포인트에 적은 객체(또는 `boolean`)를 그대로 반환, HTTP 200.
- 실패: `BusinessException` → HTTP 상태는 `ErrorCode.getStatus()`, 본문은 아래 형식.

```json
{ "code": "VIDEO_ACCESS_DENIED", "message": "수강 기간이 끝났거나 취소한 강의라 영상을 볼 수 없어요. 수료한 강의는 기간과 관계없이 다시 볼 수 있어요." }
```

- 구현 방법: `GlobalExceptionHandler`가 요청이 `@ResponseBody` 메서드인지(또는 `Accept: application/json`인지) 구분해 JSON으로 응답 — **팀원 1(#02)이 방식 결정** ❓

### 1-8. 공통 모델 값 💡

모든 화면 템플릿(`common/layout.html` 헤더)에서 쓰는 값. `@ControllerAdvice` + `@ModelAttribute`로 한 번에 넣는 방법을 제안한다 (방법은 팀원 1·팀원 4 합의, #17).

| 모델 이름 | 타입 | 설명 |
|---|---|---|
| `loginUser` | `LoginUserDTO` 또는 `null` | 닉네임·역할 (헤더 메뉴 분기) |
| `unreadCount` | `long` | 안 읽은 알림 수 (`NotificationService.countUnread`) |
| `message` / `errorMessage` | `String` (flash) | 완료·오류 안내 |

### 1-9. 페이징 ❓

- 목록이 길어질 수 있는 화면: 작품 게시판, 관리자 작품 목록, 알림함, 강사 평가 목록.
- 쿼리 파라미터 `page`(0부터) 💡, 페이지 크기 ❓(미정, #18).
- 다른 목록(강의 9개, 분반 등)은 페이징 없이 전체 조회.

### 1-10. 파일 업로드 공통 ✅

| 항목 | 값 | 근거 |
|---|---|---|
| 요청 형식 | `multipart/form-data`, 파라미터 이름 `images` (여러 개) | 💡 이름 |
| 개수 | 1~5장 | D-18·D-22 |
| 형식 | JPG / PNG / WebP | D-22 |
| 크기 | 장당 50MB 이하 | D-22, `max-file-size: 50MB` |
| 요청 전체 크기 | ❓ (`max-request-size`, 5×50MB 이상 필요) | #02 미정 |
| 실패 처리 | 한 장이라도 실패하면 전체 미저장, 이미 저장된 파일 삭제 | F-05, #16 |
| 저장 위치 | `file.upload-dir`, 파일명 UUID | ✅ |

---

## 2. 전체 엔드포인트 목록

> 컨트롤러는 모두 생성된 스켈레톤에 있다. "담당"은 팀원 번호, "#"은 이슈 번호.

### 2-1. 공통·인증 (팀원 1)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| CM-01 | GET | `/` | 🔓 | VIEW `main/home` | C-01 | #02 | P0 |
| AU-01 | GET | `/auth/login` | 🔓 | VIEW `auth/login` | C-02 | #02 | P0 |
| AU-02 | POST | `/auth/login` | 🔓 | REDIRECT (Security 처리) | C-02 | #02·#04 | P0 |
| AU-03 | POST | `/auth/logout` | 로그인 | REDIRECT `/` | 공통 헤더 | #02 | P0 |
| AU-04 | GET | `/auth/verify/sent` | 🔓 | VIEW `auth/verify-sent` | C-04 | #04 | P0 |
| AU-05 | GET | `/auth/verify` | 🔓 | VIEW `auth/verify-result` | C-04 | #04 | P0 |
| AU-06 | POST | `/auth/verify/resend` | 🔓 | REDIRECT | C-04 | #04 | P0 |
| AU-07 | GET | `/auth/recovery` | 🔓 | VIEW `auth/recovery` | C-05 | #06 | P1 |
| AU-08 | POST | `/auth/recovery/login-id` | 🔓 | REDIRECT | C-05 | #06 | P1 |
| AU-09 | GET | `/auth/recovery/login-id` | 🔓 | VIEW `auth/recovery-login-id` | C-05 | #06 | P1 |
| AU-10 | POST | `/auth/recovery/password` | 🔓 | REDIRECT | C-05 | #06 | P1 |
| AU-11 | GET | `/auth/recovery/password` | 🔓 | VIEW `auth/reset-password` | C-05 | #06 | P1 |
| AU-12 | POST | `/auth/recovery/password/reset` | 🔓 | REDIRECT `/auth/login` | C-05 | #06 | P1 |
| AU-13 | GET | `/auth/instructor/password` | 🎓 | VIEW `auth/instructor-password` | C-09 | #05 | P0 |
| AU-14 | POST | `/auth/instructor/password` | 🎓 | REDIRECT `/instructor` | C-09 | #05 | P0 |

### 2-2. 회원·강사 계정 (팀원 1)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| US-01 | GET | `/signup` | 🔓 | VIEW `user/signup` | C-03 | #04 | P0 |
| US-02 | POST | `/signup` | 🔓 | REDIRECT `/auth/verify/sent` | C-03 | #04 | P0 |
| US-03 | GET | `/signup/check-id` | 🔓 | JSON `boolean` | C-03 | #04 | P0 |
| US-04 | GET | `/signup/check-nickname` | 🔓 | JSON `boolean` | C-03, U-S17 | #04 | P0 |
| US-05 | GET | `/my/account` | 👤🎓🛠 | VIEW `user/account` | U-S17 | #06 | P1 |
| US-06 | POST | `/my/account/nickname` | 👤 | REDIRECT `/my/account` | U-S17 | #06 | P1 |
| US-07 | POST | `/my/account/password` | 👤🎓🛠 | REDIRECT `/my/account` | U-S17 | #06 | P1 |
| US-08 | GET | `/profiles/{userId}` | 🔓 | VIEW `user/profile` | U-S13 | — | **P2** |
| AI-01 | GET | `/admin/instructors` | 🛠 | VIEW `admin/instructors` | A-S04 | #05 | P0 |
| AI-02 | POST | `/admin/instructors` | 🛠 | REDIRECT `/admin/instructors` | A-S04 | #05 | P0 |
| AI-03 | POST | `/admin/instructors/{instructorId}/reissue` | 🛠 | REDIRECT | A-S04 | #05 | P0 |
| AI-04 | POST | `/admin/instructors/{instructorId}/status` | 🛠 | REDIRECT | A-S04 | #05 | P0 |
| AI-05 | POST | `/admin/instructors/{instructorId}/hobbies` | 🛠 | REDIRECT | A-S04 | #05 | P0 |
| AI-06 | POST | `/admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke` | 🛠 | REDIRECT | A-S04 | #05 | P0 |

### 2-3. 취미·강의 탐색 (팀원 2)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| HB-01 | GET | `/hobbies` | 🔓 | VIEW `hobby/list` | U-S01 | #08 | P0 |
| HB-02 | GET | `/hobbies/{hobbyId}` | 🔓 | VIEW `hobby/detail` | U-S02 | #08 | P0 |
| HB-03 | GET | `/admin/hobbies` | 🛠 | VIEW `admin/hobbies` | A-S15(HTML) | #08 | P0 |
| CO-01 | GET | `/courses` | 🔓 | VIEW `course/list` | U-S03 | #08 | P0 |
| CO-02 | GET | `/courses/{courseId}` | 🔓 | VIEW `course/detail` | U-S04 | #08·#09 | P0 |

### 2-4. 수강·분반·대기열 (팀원 2)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| EN-01 | POST | `/enrollments` | 👤 | REDIRECT `/courses/{courseId}` | U-S05 | #09 | P0 |
| EN-02 | POST | `/enrollments/waitlist/{waitlistId}/cancel` | 👤 | REDIRECT | U-S05 | #09 | P0 |
| EN-03 | POST | `/enrollments/{enrollmentId}/cancel` | 👤 | REDIRECT `/my/learning` | U-S05 | #09 | P0 |
| SE-01 | GET | `/admin/sections` | 🛠 | VIEW `admin/sections` | A-S05 | #10 | P0 |
| SE-02 | POST | `/admin/sections` | 🛠 | REDIRECT | A-S05 | #10 | P0 |
| SE-03 | POST | `/admin/sections/{sectionId}/capacity` | 🛠 | REDIRECT | A-S05 | #10 | P0 |
| SE-04 | POST | `/admin/sections/{sectionId}/instructor` | 🛠 | REDIRECT | A-S05 | #10 | P0 |
| WL-01 | GET | `/admin/waitlists` | 🛠 | VIEW `admin/waitlists` | A-S12 | #10 | P0 |

### 2-5. 학습·진도·영상 (팀원 2: ML / 팀원 3: LE·VD)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| ML-01 | GET | `/my/learning` | 👤 | VIEW `learning/my-learning` | U-S08 | #11 | P0 |
| LE-01 | GET | `/lessons/{lessonId}` | 👤 | VIEW `learning/lesson` | U-S06 | #12 | P0 |
| LE-02 | POST | `/lessons/{lessonId}/progress` | 👤 | JSON `ProgressReportDTO` | U-S06 | #12 | P0 |
| VD-01 | GET | `/videos/{lessonId}` | 👤 | FILE (영상) | U-S06 | #12 | P0 |

### 2-6. 최종 과제·평가 (팀원 3)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| AS-01 | GET | `/assignments` | 👤 | VIEW `review/assignment` | U-S07 | #13 | P0 |
| AS-02 | POST | `/assignments` | 👤 | REDIRECT | U-S07 | #13 | P0 |
| AS-03 | POST | `/assignments/{reviewRoundId}/edit` | 👤 | REDIRECT | U-S07 | #13 | P0 |
| AS-04 | POST | `/assignments/resubmit` | 👤 | REDIRECT | U-S07 | #13 | P0 |
| AS-05 | GET | `/assignments/{reviewRoundId}/result` | 👤 | VIEW `review/result` | U-S14 | #15 | P0 |
| RV-01 | GET | `/instructor/reviews` | 🎓 | VIEW `instructor/reviews` | T-S03 | #14 | P0 |
| RV-02 | GET | `/instructor/reviews/{reviewRoundId}` | 🎓 | VIEW `instructor/review-detail` | T-S04 | #14 | P0 |
| RV-03 | POST | `/instructor/reviews/{reviewRoundId}/start` | 🎓 | REDIRECT | T-S04 | #14 | P0 |
| RV-04 | POST | `/instructor/reviews/{reviewRoundId}/cancel-start` | 🎓 | REDIRECT | T-S04 | #14 | P0 |
| RV-05 | POST | `/instructor/reviews/{reviewRoundId}/draft` | 🎓 | REDIRECT | T-S04 | #14 | P0 |
| RV-06 | POST | `/instructor/reviews/{reviewRoundId}/approve` | 🎓 | REDIRECT | T-S04 | #14 | P0 |
| RV-07 | POST | `/instructor/reviews/{reviewRoundId}/reject` | 🎓 | REDIRECT | T-S04 | #14 | P0 |

### 2-7. 작품·댓글·파일 (팀원 4)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| FL-01 | GET | `/files/works/{fileId}` | 로그인 | FILE (이미지) | 모든 작품 이미지 | #16 | P0 |
| WB-01 | GET | `/works` | 로그인 | VIEW `work/board` | U-S09, T-S10 | #18 | P1 |
| WB-02 | GET | `/works/{workId}` | 로그인 | VIEW `work/detail` | U-S10 | #18 | P1 |
| WB-03 | POST | `/works/{workId}/like` | 로그인 | JSON `LikeResult` | U-S10 | #18 | P1 |
| CM-C1 | POST | `/comments` | 로그인 | REDIRECT `/works/{workId}` | U-S10 | #18 | P1 |
| CM-C2 | POST | `/comments/{commentId}/edit` | 로그인 | REDIRECT | U-S10 | #18 | P1 |
| CM-C3 | POST | `/comments/{commentId}/delete` | 로그인 | REDIRECT | U-S10 | #18 | P1 |
| MW-01 | GET | `/my/works/new` | 👤 | VIEW `work/form` | U-S11 | #19 | P1 |
| MW-02 | POST | `/my/works` | 👤 | REDIRECT `/my/works/growth` | U-S11 | #19 | P1 |
| MW-03 | GET | `/my/works/{workId}/edit` | 👤 | VIEW `work/form` | U-S11 | #19 | P1 |
| MW-04 | POST | `/my/works/{workId}/edit` | 👤 | REDIRECT | U-S11 | #19 | P1 |
| MW-05 | POST | `/my/works/{workId}/visibility` | 👤 | REDIRECT | U-S11·U-S12 | #19 | P1 |
| MW-06 | POST | `/my/works/{workId}/reflection` | 👤 | REDIRECT | U-S12 | #19 | P1 |
| MW-07 | GET | `/my/works/growth` | 👤 | VIEW `work/growth` | U-S12 | #19 | P1 |
| MW-08 | GET | `/my/works/{workId}/moderation` | 👤 | VIEW `work/moderation` | U-S16 | #20 | P1 |
| MW-09 | POST | `/my/works/{workId}/delete` | 👤 | REDIRECT | U-S11 | — | **P2** |
| AW-01 | GET | `/admin/works` | 🛠 | VIEW `admin/works` | A-S08, A-S14(HTML) | #20 | P1 |
| AW-02 | POST | `/admin/works/{workId}/hide` | 🛠 | REDIRECT | A-S08 | #20 | P1 |
| AW-03 | POST | `/admin/works/{workId}/restore` | 🛠 | REDIRECT | A-S08 | #20 | P1 |

### 2-8. 알림 (팀원 4)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| NT-01 | GET | `/notifications` | 로그인 | VIEW `notification/list` | U-S15, T-S09(HTML) | #17 | P0 |
| NT-02 | GET | `/notifications/{notificationId}` | 로그인 | REDIRECT (대상 화면) | U-S15 | #17 | P0 |
| NT-03 | POST | `/notifications/read-all` | 로그인 | REDIRECT `/notifications` | U-S15 | #17 | P0 |
| NT-04 | GET | `/notifications/unread-count` | 로그인 | JSON `long` | 공통 헤더 | #17 | 💡 선택 |

### 2-9. 1:1 문의 (팀원 4)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| IQ-01 | GET | `/inquiries` | 👤 | VIEW `inquiry/list` | U-S18 | #21 | P1 |
| IQ-02 | POST | `/inquiries` | 👤 | REDIRECT `/inquiries/{threadId}` | U-S18 | #21 | P1 |
| IQ-03 | GET | `/inquiries/{threadId}` | 👤 | VIEW `inquiry/thread` | U-S18 | #21 | P1 |
| IQ-04 | POST | `/inquiries/{threadId}/messages` | 👤 | REDIRECT | U-S18 | #21 | P1 |
| II-01 | GET | `/instructor/inquiries` | 🎓 | VIEW `instructor/inquiries` | T-S06 | #21 | P1 |
| II-02 | GET | `/instructor/inquiries/{threadId}` | 🎓 | VIEW `instructor/inquiry-thread` | T-S06 | #21 | P1 |
| II-03 | POST | `/instructor/inquiries/{threadId}/answer` | 🎓 | REDIRECT | T-S06 | #21 | P1 |

### 2-10. 강사 LMS·관리자 홈·통계 (팀원 1)

| ID | 메서드 | URL | 권한 | 응답 | 화면 | # | 등급 |
|---|---|---|---|---|---|---|---|
| IN-01 | GET | `/instructor` | 🎓 | VIEW `instructor/home` | T-S01 | #07 | P0 |
| IN-02 | GET | `/instructor/students` | 🎓 | VIEW `instructor/students` | T-S02 | #07 | P0 |
| IN-03 | GET | `/instructor/students/{enrollmentId}` | 🎓 | VIEW `instructor/student-detail` | T-S02 | #07 | P0 |
| IN-04 | GET | `/instructor/stats` | 🎓 | VIEW `instructor/stats` | T-S05 | #07 | P0 |
| AD-01 | GET | `/admin` | 🛠 | VIEW `admin/home` | A-S01 | #07 | P0 |
| AD-02 | GET | `/admin/stats` | 🛠 | VIEW `admin/stats` | A-S13 | #07 | P0 |

> **합계:** 94개 — 이번 범위(P0·P1) **92개**, P2 참고 2개(US-08, MW-09). 이 밖에 스케줄러 1개(§13 SC-01).

---

## 상세 명세 읽는 법

각 엔드포인트는 아래 순서로 적는다.

- **요약 줄:** 메서드·URL · 권한 · 컨트롤러 · 담당/이슈
- **요청:** 위치(Path / Query / Form / File)·이름·타입·필수·규칙
- **처리:** 호출하는 Service 메서드 (다른 팀원 기능은 §7 계약 메서드로만 호출)
- **성공:** VIEW면 모델 값, REDIRECT면 이동 위치·flash, JSON이면 본문
- **실패:** 발생 가능한 ErrorCode (처리 방식은 §1-6·§1-7 규칙)

---

## 3. 상세 명세 — 공통·인증

### CM-01. 메인 홈
`GET /` · 🔓 · `MainController` · 팀원 1 / #02 · C-01

| 요청 | 없음 |
|---|---|

- **처리:** 로그인 여부 확인. 로그인 학습자면 이어서 학습할 수강 목록 조회 ❓(어떤 항목을 홈에 보여줄지 HTML C-01 기준으로 #02에서 결정)
- **성공:** VIEW `main/home`

| 모델 | 타입 | 설명 |
|---|---|---|
| `hobbies` | `List<HobbyDTO>` | 운영 중 취미 바로가기 ❓ |
| `continueCourses` | `List<MyCourseDTO>` | 로그인 학습자의 학습 중 강의 (비회원은 빈 목록) ❓ |

- **실패:** 없음

---

### AU-01. 로그인 화면
`GET /auth/login` · 🔓 · `AuthController` · 팀원 1 / #02 · C-02

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `error` | 존재 여부 | N | 로그인 실패 후 돌아온 경우 |
| Query | `expired` | 존재 여부 | N | 💡 세션 만료·다른 기기 로그인으로 끊긴 경우 (C-06 안내) |
| Query | `logout` | 존재 여부 | N | 로그아웃 완료 |

- **성공:** VIEW `auth/login`, 모델 `errorMessage`(실패 원인 메시지 — `AuthFailHandler`가 넣음)
- **실패:** 없음

### AU-02. 로그인 처리
`POST /auth/login` · 🔓 · **Spring Security가 처리** (컨트롤러 메서드 없음) · 팀원 1 / #02·#04

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Form | `loginId` 💡 | String | Y | `usernameParameter("loginId")` — 템플릿과 이름 일치 필수 |
| Form | `password` | String | Y | `passwordParameter("password")` |

- **처리:** `AuthService.loadUserByUsername(loginId)` → BCrypt 비교 → `AuthDetails.isEnabled()`(이메일 인증 완료 && 활성 계정)
- **성공 (`AuthSuccessHandler`) ✅**

| 조건 | 이동 |
|---|---|
| 강사 + 임시 비밀번호 상태 | `redirect:/auth/instructor/password` (C-09) |
| 강사 | `redirect:/instructor` (T-S01) |
| 관리자 | `redirect:/admin` (A-S01) |
| 학습자 첫 로그인 (`first_login_at` 없음 → 기록) | `redirect:/hobbies` (FR-003) |
| 학습자 이후 로그인 | `redirect:/` |

- **실패 (`AuthFailHandler`):** `redirect:/auth/login?error` + 원인별 메시지

| 예외 | 메시지 |
|---|---|
| `BadCredentialsException` | "아이디 또는 비밀번호가 올바르지 않습니다." 💡 (계정 존재 여부를 드러내지 않음) |
| `DisabledException` (이메일 미인증) | `EMAIL_NOT_VERIFIED` 메시지 + 인증 메일 재발송 링크 |
| `DisabledException` (비활성 강사) | "비활성화된 계정입니다." 💡 |

### AU-03. 로그아웃
`POST /auth/logout` · 로그인 · Spring Security · 팀원 1 / #02

- **처리:** 세션 무효화, `JSESSIONID` 쿠키 삭제 ✅
- **성공:** `redirect:/` 💡 (또는 `/auth/login?logout`)

### AU-04. 인증 메일 발송 안내
`GET /auth/verify/sent` · 🔓 · `EmailVerifyController` · 팀원 1 / #04 · C-04

- **성공:** VIEW `auth/verify-sent`, 모델 `email`(flash로 받은 가입 이메일, 일부 가림 💡)

### AU-05. 이메일 인증 처리
`GET /auth/verify` · 🔓 · `EmailVerifyController` · 팀원 1 / #04 · C-04

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `token` | String | Y | 메일 링크에 담긴 토큰 원문 (DB에는 해시만 저장, NFR-02) |

- **처리:** `EmailVerificationService.verify(token)` — 목적 `VERIFY_EMAIL` 토큰만 허용, 1회 사용
- **성공:** VIEW `auth/verify-result`, 모델 `success=true` → "로그인하기" 버튼 (가입 직후 자동 로그인 없음, F-01)
- **실패:** `INVALID_TOKEN`(없음·이미 사용·목적 다름), `TOKEN_EXPIRED` → **같은 화면**에 `success=false`, `errorMessage`, 재발송 폼 표시
- ❓ 토큰 만료 시간 (§16-1)

### AU-06. 인증 메일 재발송
`POST /auth/verify/resend` · 🔓 · `EmailVerifyController` · 팀원 1 / #04

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `email` | String | Y | `@Email` |

- **처리:** 미인증 계정이면 기존 토큰 무효화 후 새 토큰 발송
- **성공:** `redirect:/auth/verify/sent` + flash `email`
- **실패:** 이메일이 없거나 이미 인증된 경우에도 **같은 안내**를 보여 계정 존재 여부를 숨김 💡

### AU-07. 아이디 찾기·비밀번호 재설정 화면
`GET /auth/recovery` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06 · C-05

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `tab` | String | N | `login-id`(기본) / `password` 💡 |

- **성공:** VIEW `auth/recovery`

### AU-08. 아이디 찾기 메일 요청
`POST /auth/recovery/login-id` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `email` | String | Y | `@Email` |

- **처리:** `EmailVerificationService.sendRecovery(email, RECOVER_LOGIN_ID)`
- **성공:** `redirect:/auth/recovery?tab=login-id` + flash `message`("가입된 이메일이라면 인증 메일을 보냈습니다." 💡 — 존재 여부 숨김)

### AU-09. 아이디 확인 (메일 링크)
`GET /auth/recovery/login-id` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `token` | String | Y | 목적 `RECOVER_LOGIN_ID` |

- **성공:** VIEW `auth/recovery-login-id`, 모델 `loginId`
- **실패:** `INVALID_TOKEN`, `TOKEN_EXPIRED` → 같은 화면에 오류 표시

### AU-10. 비밀번호 재설정 메일 요청
`POST /auth/recovery/password` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `loginId` | String | Y | ❓ 아이디+이메일 둘 다 받을지, 이메일만 받을지 (HTML C-05 확인) |
| Form | `email` | String | Y | `@Email` |

- **처리:** `EmailVerificationService.sendRecovery(email, RESET_PASSWORD)`
- **성공:** `redirect:/auth/recovery?tab=password` + flash `message` (AU-08과 같은 문구)

### AU-11. 새 비밀번호 입력 화면 (메일 링크)
`GET /auth/recovery/password` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `token` | String | Y | 목적 `RESET_PASSWORD` (가입 인증 토큰으로는 불가, DEV_REVIEW §2-12) |

- **처리:** 토큰 유효성만 확인 (아직 사용 처리하지 않음)
- **성공:** VIEW `auth/reset-password`, 모델 `token`(hidden 필드)
- **실패:** `INVALID_TOKEN`, `TOKEN_EXPIRED` → 같은 화면에 오류 + 다시 요청 링크

### AU-12. 비밀번호 재설정 처리
`POST /auth/recovery/password/reset` · 🔓 · `AccountRecoveryController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `token` | String | Y | |
| Form | `newPassword` | String | Y | 8~16자, 영문·숫자·특수문자 (US-02와 같은 규칙) |
| Form | `newPasswordConfirm` | String | Y | `newPassword`와 같아야 함 |

- **처리:** 토큰 검증·사용 처리 → BCrypt 저장
- **성공:** `redirect:/auth/login` + flash `message`("비밀번호가 변경되었습니다.")
- **실패:** `INVALID_TOKEN`, `TOKEN_EXPIRED`, `INVALID_NEW_PASSWORD`, 형식 오류 → AU-11 화면 재표시

### AU-13. 강사 첫 로그인 비밀번호 변경 화면
`GET /auth/instructor/password` · 🎓 · `InstructorSetupController` · 팀원 1 / #05 · C-09

- **성공:** VIEW `auth/instructor-password`
- 이 화면·AU-14·로그아웃·정적 리소스는 `PasswordChangeInterceptor` 제외 경로 ✅ (빠뜨리면 무한 redirect)
- C-08(강사 약관 확인)은 **만들지 않음** (11-06 결정)

### AU-14. 강사 첫 로그인 비밀번호 변경 처리
`POST /auth/instructor/password` · 🎓 · `InstructorSetupController` · 팀원 1 / #05

`PasswordChangeDTO`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `newPassword` | String | Y | 8~16자, 영문·숫자·특수문자 / **아이디·임시 비밀번호와 달라야 함** ✅ |
| Form | `newPasswordConfirm` | String | Y | 일치 |

- **처리:** `UserService`(또는 `InstructorAccountService`) — 새 비밀번호 저장, `mustChangePassword = false`
- **성공:** `redirect:/instructor` + flash `message`
- **실패:** `INVALID_NEW_PASSWORD`(아이디·임시 비밀번호와 같음), 형식 오류 → AU-13 화면 재표시
- ❓ 임시 비밀번호 재발급 시 "이전 변경 무효"(07 C-09) — 재발급하면 다시 `mustChangePassword = true`

---

## 4. 상세 명세 — 회원·강사 계정

### US-01. 회원가입 화면
`GET /signup` · 🔓 · `SignupController` · 팀원 1 / #04 · C-03

- **성공:** VIEW `user/signup`, 모델 `signupDTO`(빈 객체)
- 약관 동의·만 14세 확인 항목 **없음** (11-06 결정)

### US-02. 회원가입 처리
`POST /signup` · 🔓 · `SignupController` · 팀원 1 / #04

`SignupDTO` (`@Valid @ModelAttribute`) — 검증값은 HTML C-03 기준 (11-06 확정)

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `loginId` | String | Y | 영문 또는 영문+숫자 (`@Pattern`) ✅ / 길이 ❓ |
| Form | `password` | String | Y | `@Size(min=8, max=16)` + 영문·숫자·특수문자 (`@Pattern`) ✅ / ❓ 세 종류 모두 필수인지 |
| Form | `passwordConfirm` | String | Y | `password`와 일치 |
| Form | `name` | String | Y | 실명, 한글 2~5자 (`@Pattern`) ✅ |
| Form | `email` | String | Y | `@Email`, 계정당 고유 ✅ |
| Form | `nickname` | String | N | 입력 시 중복 불가. 미입력 시 `형용사+동물명사+1~999` 자동 생성 (`NicknamePolicy`) ✅ / 길이 ❓ |

- **처리:** `UserService.signup(dto)` → 중복 검사(아이디·이메일·닉네임) → 닉네임 생성 → BCrypt → `User.createLearner()`(미인증) → `EmailVerificationService.sendVerification(user)`
- **성공:** `redirect:/auth/verify/sent` + flash `email`
- **실패:** `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL`, `DUPLICATE_NICKNAME`, 형식 오류 → **US-01 화면 재표시**(입력값 유지, 비밀번호 칸은 비움 💡)
- ❓ 메일 발송 실패 시 가입 롤백 여부 (§16-1), 탈퇴 후 30일 재가입 제한 검사는 탈퇴 기능(P2)과 함께

### US-03. 아이디 중복 확인
`GET /signup/check-id` · 🔓 · `SignupController` · 팀원 1 / #04 · `@ResponseBody`

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Query | `loginId` | String | Y |

- **성공:** JSON `true` = **사용 가능**, `false` = 이미 사용 중 💡(의미 고정)
- 최종 판단은 US-02에서 다시 검사 (중복 확인 후 가입 사이에 다른 사람이 가입할 수 있음)

### US-04. 닉네임 중복 확인
`GET /signup/check-nickname` · 🔓 · `SignupController` · 팀원 1 / #04 · `@ResponseBody`

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Query | `nickname` | String | Y |

- **성공:** JSON `true` = 사용 가능 / `false` = 사용 중. 계정 설정(US-06)에서도 재사용

### US-05. 계정 설정 화면
`GET /my/account` · 로그인 · `AccountController` · 팀원 1 / #06 · U-S17

- **성공:** VIEW `user/account`

| 모델 | 설명 |
|---|---|
| `account` | 아이디·실명·이메일(조회 전용 ❓), 닉네임 |

- 탈퇴 탭은 **P2(FR-040) — 만들지 않음**

### US-06. 닉네임 변경
`POST /my/account/nickname` · 👤 · `AccountController` · 팀원 1 / #06

`AccountUpdateDTO`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `nickname` | String | Y | 중복 불가 / 길이 ❓ |

- **처리:** `UserService.changeNickname(userId, nickname)`
- **성공:** `redirect:/my/account` + flash `message`
- **실패:** `DUPLICATE_NICKNAME`

### US-07. 비밀번호 변경
`POST /my/account/password` · 로그인 · `AccountController` · 팀원 1 / #06

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `currentPassword` | String | Y | 현재 비밀번호와 일치 |
| Form | `newPassword` | String | Y | US-02 비밀번호 규칙 |
| Form | `newPasswordConfirm` | String | Y | 일치 |

- **처리:** `UserService.changePassword(userId, current, next)`
- **성공:** `redirect:/my/account` + flash `message`
- **실패:** 현재 비밀번호 불일치 → 💡 ErrorCode `PASSWORD_MISMATCH`(400) **추가 제안** (현재 목록에 없음, 없으면 `INVALID_INPUT` 사용), `INVALID_NEW_PASSWORD`

### US-08. 공개 프로필 — **P2 (이번 범위 밖)**
`GET /profiles/{userId}` · 🔓 · `ProfileController` · FR-048

- 배지 공개, 공개 작품만, 학습 진도 비공개. 구현하지 않으며 컨트롤러는 빈 상태로 둔다.

---

### AI-01. 강사 계정 목록
`GET /admin/instructors` · 🛠 · `AdminInstructorController` · 팀원 1 / #05 · A-S04

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `keyword` | String | N | 아이디·실명 검색 💡 |

- **성공:** VIEW `admin/instructors`

| 모델 | 타입 | 설명 |
|---|---|---|
| `instructors` | `List<InstructorDTO>` | 아이디, 실명, 이메일, 상태(첫 로그인 전/활성/비활성), 취미 권한 목록 |
| `hobbies` | `List<HobbyDTO>` | 권한 부여용 세부 취미 목록 |
| `instructorCreateDTO` | `InstructorCreateDTO` | 생성 폼 |
| `tempPassword` | String (flash) | **생성·재발급 직후 1회만** 표시 (새로고침 시 사라짐, 로그·DB에 평문 저장 안 함) ✅ |

### AI-02. 강사 계정 생성
`POST /admin/instructors` · 🛠 · `AdminInstructorController` · 팀원 1 / #05

`InstructorCreateDTO`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `loginId` | String | Y | US-02 아이디 규칙, 중복 불가 |
| Form | `name` | String | Y | 실명 |
| Form | `email` | String | Y | `@Email`, 중복 불가 |
| Form | `hobbyIds` | List\<Long\> | N | 💡 생성과 동시에 취미 권한 부여 (없으면 AI-05로 따로) |

- **처리:** `InstructorAccountService.create(dto)` → 임시 비밀번호 생성(규칙 ❓) → BCrypt 저장 + `mustChangePassword = true` → 반환값 `String tempPassword`
- **성공:** `redirect:/admin/instructors` + flash `tempPassword`, `message`
- **실패:** `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL`, 형식 오류
- 메일 설정 링크 없음 (D-51)

### AI-03. 임시 비밀번호 재발급
`POST /admin/instructors/{instructorId}/reissue` · 🛠 · 팀원 1 / #05

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `instructorId` | Long | Y |

- **처리:** `InstructorAccountService.reissue(instructorId)` → 새 임시 비밀번호, `mustChangePassword = true`
- **성공:** `redirect:/admin/instructors` + flash `tempPassword`
- **실패:** `USER_NOT_FOUND`

### AI-04. 강사 활성/비활성 변경
`POST /admin/instructors/{instructorId}/status` · 🛠 · 팀원 1 / #05

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Path | `instructorId` | Long | Y | |
| Form | `active` | boolean | Y | `true` 활성 / `false` 비활성 |
| Form | `emergency` | boolean | N | `true` 긴급 비활성화(갑작스러운 이탈·정지) — 기본 `false` |
| Form | `from` | String | N | `stats` 면 성공 후 `/admin/stats`로 |

- **처리:** 활성화는 `InstructorAccountService.changeStatus`. 비활성화는 `SectionService.deactivateInstructor(instructorId, emergency)`
  - 비활성 강사는 로그인 불가, 로그인 중인 세션도 끊는다 → 그 브라우저는 다음 요청에서 `/auth/login?error=disabled`("비활성화된 계정입니다", `SessionExpiredHandler`)
  - 담당 분반이 있으면 평소에는 거부(`INSTRUCTOR_HAS_SECTIONS`) → A-S04에 이유 + [분반 관리로 가기]·[긴급 비활성화] 표시. 관리자가 SE-04로 담당을 먼저 바꾼다(D-59)
  - `emergency=true`면 바로 비활성화하고 그 강사의 분반을 모두 **담당 공백**(`instructor_id = NULL`)으로 바꾼다 → 신규 배정 제외(D-52), A-S01 담당 공백 분반에 표시, 새 담당 지정 시 평가 대기 과제 승계
- **성공:** `redirect:/admin/instructors` (또는 `/admin/stats`) + flash `message`(담당 공백이 된 분반 수 포함)
- **실패:** `USER_NOT_FOUND`(에러 화면), `INSTRUCTOR_HAS_SECTIONS`(A-S04로 redirect + flash `errorMessage`·`blockedInstructorId`)

### AI-05. 취미 권한 부여
`POST /admin/instructors/{instructorId}/hobbies` · 🛠 · 팀원 1 / #05

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `instructorId` | Long | Y |
| Form | `hobbyId` | Long | Y |

- **처리:** `InstructorAccountService.grantHobby(instructorId, hobbyId)` (이미 있으면 무시 💡)
- **실패:** `USER_NOT_FOUND`, `HOBBY_NOT_FOUND`

### AI-06. 취미 권한 회수
`POST /admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke` · 🛠 · 팀원 1 / #05

- **처리:** `SectionService.revokeHobbyGrant(instructorId, hobbyId)` — 그 취미의 분반을 담당 중이면 거부, 아니면 `InstructorAccountService.revokeHobby`
- **실패:** `USER_NOT_FOUND`, `HOBBY_NOT_FOUND`, `GRANT_IN_USE`(A-S04 취미 권한 탭으로 redirect + flash `errorMessage`)

---

## 5. 상세 명세 — 취미·강의 탐색

### HB-01. 취미 탐색
`GET /hobbies` · 🔓 · `HobbyController` · 팀원 2 / #08 · U-S01

- **처리:** `HobbyService` — 노출 규칙 ✅(05 §3)
  - **운영 중**(미술·드로잉, 요리·베이킹): 세부 취미 목록, 신청 가능
  - **준비 중**(공예): 소개·썸네일만, 신청 불가
  - **비공개**(사진·영상): USER·강사에게 보이지 않음 (관리자는 HB-03)
- **성공:** VIEW `hobby/list`

| 모델 | 타입 | 설명 |
|---|---|---|
| `categories` | `List<CategoryDTO>` | 대분류(이름, 상태) + 하위 `List<HobbyDTO>`(이름, 썸네일, 상태) |

### HB-02. 세부 취미 상세
`GET /hobbies/{hobbyId}` · 🔓 · `HobbyController` · 팀원 2 / #08 · U-S02

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `hobbyId` | Long | Y |

- **성공:** VIEW `hobby/detail`

| 모델 | 타입 | 설명 |
|---|---|---|
| `hobby` | `HobbyDetailDTO` | 소개·특징·썸네일·상태, 수준 3개(입문/중급/고급)별 강의 링크 |
| `levelStatuses` | Map\<CourseLevel, String\> | 수준별 내 상태(미수강/수강 중/수료·배지) — **P2**, 비회원은 없음 |

- **실패:** `HOBBY_NOT_FOUND`(없음 또는 비공개 취미에 USER 접근 — 존재 숨김)
- 준비 중 취미는 **강사·관리자에게만** 소개를 보여 주고(강의 신청 링크 비활성), **학습자·비회원에게는 보이지 않는다**(목록·사이드 메뉴에서 빠지고 직접 URL은 `HOBBY_NOT_FOUND`) — 11-09 노출 정책 변경. 이미 수강 중인 학습자의 학습·기록 접근은 유지(D-20, 나의 학습·차시 화면은 이 규칙을 거치지 않음). 비공개는 관리자만. `HOBBY_NOT_OPEN`은 신청 시점 EN-01에서 사용(준비 중으로 바뀐 취미의 강의는 학습자에게 숨겨져 `COURSE_NOT_FOUND`가 먼저 나올 수 있음)

### HB-03. 관리자 취미 조회
`GET /admin/hobbies` · 🛠 · `AdminHobbyController` · 팀원 2 / #08 · A-S15(HTML)

- **성공:** VIEW `admin/hobbies`, 모델 `categories`(비공개 포함 전체)
- **조회 전용.** 대분류·세부 취미 등록·수정은 시드 데이터로 대체 (D-59, 기존 기획안 유지 결정)

### CO-01. 수준별 강의 목록
`GET /courses` · 🔓 · `CourseController` · 팀원 2 / #08 · U-S03

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `hobbyId` | Long | Y | |
| Query | `level` | `CourseLevel` | N | `BASIC`/`INTERMEDIATE`/`ADVANCED`, 없으면 전체 |

- **성공:** VIEW `course/list`

| 모델 | 타입 | 설명 |
|---|---|---|
| `hobby` | `HobbyDTO` | |
| `courses` | `List<CourseDTO>` | 강의명, 수준, 정원 현황(남은 자리 합계), 대기 인원, **내 상태**(로그인 시) |

- 내 상태 표시 ✅: 신청 가능 / 대기 중 n번 / 학습 중 / **수료 완료·다시 보기** / **다시 신청할 수 없음**(취소·만료, D-55)
- **실패:** `HOBBY_NOT_FOUND`, `MissingServletRequestParameterException`(hobbyId 없음 → 400)

### CO-02. 강의 상세
`GET /courses/{courseId}` · 🔓 · `CourseController` · 팀원 2 / #08 (신청 영역은 #09)· U-S04

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `courseId` | Long | Y |

- **처리:** `CourseService.getCourseDetail(courseId, userIdOrNull)` + `EnrollmentService.getStatusForUser(userId, courseId)`
- **성공:** VIEW `course/detail` (신청 영역은 `th:fragment`로 분리 ✅)

| 모델 | 타입 | 설명 |
|---|---|---|
| `course` | `CourseDetailDTO` | 목표·준비물·**필수 3차시**(`List<LessonDTO>`)·최종 과제 안내·수강 기간 30일·잔여 정원·대기 인원 ✅ |
| `enrollStatus` | `EnrollmentStatusDTO` | 신청 영역 상태 (아래) — 비회원이면 "로그인 후 신청" |

`EnrollmentStatusDTO.state` 💡

| 값 | 버튼/안내 |
|---|---|
| `CAN_APPLY` | [수강 신청] — 빈자리 있음 |
| `CAN_WAIT` | [대기 신청] — 만석, "대기 중 자리가 나면 자동으로 수강 확정됩니다" ✅ |
| `WAITING` | "대기 n번" + [대기 취소] |
| `LEARNING` | [이어서 학습] (`/lessons/...`) |
| `COMPLETED` | "수료 완료" + [다시 보기] |
| `CANNOT_REAPPLY` | "취소·만료한 강의는 다시 신청할 수 없습니다" ✅ D-55 |
| `UNAVAILABLE` | 담당 강사 있는 분반 없음·준비 중 취미 → 신청 불가 |

- **실패:** `COURSE_NOT_FOUND`

---

## 6. 상세 명세 — 수강·분반·대기열

### EN-01. 수강 신청 (자동 배정 또는 대기)
`POST /enrollments` · 👤 · `EnrollmentController` · 팀원 2 / #09 · U-S05

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `courseId` | Long | Y |

- **처리:** `EnrollmentService.apply(userId, courseId)` → `ApplyResultDTO` ✅
  1. `EnrollEligibilityPolicy`: 같은 강의 수강 중·대기 중, 수료·취소·만료 이력(D-55), 운영 중 취미 아님, 담당 강사 있는 분반 없음 → 거부
  2. 강의의 분반 행 `SELECT … FOR UPDATE` 잠금
  3. 대기자가 있으면 → **대기열 끝** (빈자리 직행 금지)
  4. 대기자 없고 자리 있으면 → `SectionAssignPolicy`: 담당 강사 있는 분반 중 **남은 자리 최대**(동률은 분반 번호순, D-52) → `Enrollment.create(확정 시각, 확정+30일)`
  5. 자리 없으면 → 대기 등록
- **성공:** `redirect:/courses/{courseId}` + flash `message`

| `ApplyResultDTO.result` | 메시지 예 |
|---|---|
| `CONFIRMED` | "수강이 확정되었습니다. ({분반명}, {만료일}까지)" |
| `WAITING` | "대기 {n}번으로 등록되었습니다. 자리가 나면 자동으로 수강이 확정됩니다." |

- **실패:** `COURSE_NOT_FOUND`, `HOBBY_NOT_OPEN`, `ALREADY_ENROLLED`, `ALREADY_WAITING`, `CANNOT_REAPPLY`, `NO_ASSIGNABLE_SECTION` → `redirect:/courses/{courseId}` + flash `errorMessage`
- 중복 신청 이중 방어: DB UNIQUE(학습자, 강의) ✅ → `DataIntegrityViolationException`은 `ALREADY_ENROLLED`로 변환 💡

### EN-02. 대기 취소
`POST /enrollments/waitlist/{waitlistId}/cancel` · 👤 · `EnrollmentController` · 팀원 2 / #09

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Path | `waitlistId` | Long | Y | |
| Form | `returnUrl` | String | N | 💡 돌아갈 화면 (강의 상세 또는 나의 학습). 내부 경로(`/`로 시작)만 허용 |

- **처리:** `WaitlistService.cancel(userId, waitlistId)` → `WAITING` → `CANCELLED`. 다시 대기하면 끝순번 ✅
- **성공:** `redirect:{returnUrl 또는 /my/learning}` + flash `message`
- **실패:** `NOT_FOUND`(본인 대기 아님·이미 확정/취소) 💡 → 전용 코드가 필요하면 `WAITLIST_NOT_FOUND` 추가 요청

### EN-03. 수강 취소
`POST /enrollments/{enrollmentId}/cancel` · 👤 · `EnrollmentController` · 팀원 2 / #09 · U-S05

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `enrollmentId` | Long | Y |

- 화면에서 **"취소한 강의는 다시 신청할 수 없습니다" 확인** 후 전송 ✅ (D-54·D-55)
- **처리:** `EnrollmentService.cancel(userId, enrollmentId)` → 평가 대기·진행 중이면 거부(`ReviewQueryService.hasPendingReview`) → `Enrollment.cancel()` → `SeatService.fillVacancies(courseId)` → 대기 1순위 자동 확정 + 알림 `WAITLIST_CONFIRMED`
- **성공:** `redirect:/my/learning` + flash `message`
- **실패:** `ENROLLMENT_NOT_FOUND`(본인 것 아님 포함), `CANCEL_NOT_ALLOWED_DURING_REVIEW`, `INVALID_REVIEW_STATE`(이미 수료·취소·만료 💡)

---

### SE-01. 분반 관리 화면
`GET /admin/sections` · 🛠 · `AdminSectionController` · 팀원 2 / #10 · A-S05

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `courseId` | Long | N | 없으면 강의 선택 목록만 표시 |

- **성공:** VIEW `admin/sections`

| 모델 | 타입 | 설명 |
|---|---|---|
| `courses` | `List<CourseDTO>` | 강의 선택용 (9개) |
| `sections` | `List<SectionDTO>` | 분반 번호·이름, 담당 강사(없으면 "공백"), 정원, 활성 수강 수, 남은 자리 (DB VIEW `v_section_seats`) |
| `candidates` | `List<InstructorDTO>` | 이 강의 취미 권한이 있는 활성 강사 (담당 지정용) |
| `sectionFormDTO` | `SectionFormDTO` | 생성 폼 |

- **강사 합산 30건 표시·검사 없음** (D-52)
- **실패:** `COURSE_NOT_FOUND`

### SE-02. 분반 생성
`POST /admin/sections` · 🛠 · `AdminSectionController` · 팀원 2 / #10

`SectionFormDTO`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `courseId` | Long | Y | |
| Form | `name` | String | N | 분반 이름(예: "A분반"), 최대 30자, 강의 안에서 중복 불가 — 비우면 번호로 표시 (DB `course_sections.name`) |
| Form | `capacity` | int | Y | `@Min(1) @Max(30)` ✅ |
| Form | `instructorId` | Long | Y 💡 | 해당 취미 권한 보유 강사 (`hasHobbyGrant`) |

- **처리:** `SectionService.create(courseId, dto)` → 분반 번호 자동 증가 💡 → 담당 강사에게 `notify(SECTION_ASSIGNED)` → 생성 후 `fillVacancies(courseId)` (대기자 있으면 새 자리만큼 자동 확정)
- **성공:** `redirect:/admin/sections?courseId={courseId}` + flash `message`
- **실패:** `COURSE_NOT_FOUND`, `CAPACITY_OUT_OF_RANGE`, `INSTRUCTOR_NOT_GRANTED`

### SE-03. 정원 변경
`POST /admin/sections/{sectionId}/capacity` · 🛠 · 팀원 2 / #10

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Path | `sectionId` | Long | Y | |
| Form | `capacity` | int | Y | 1~30, **현재 활성 수강 수 이상** ✅ |

- **처리:** `SectionService.changeCapacity(sectionId, capacity)` → 늘어나면 `fillVacancies()`로 대기 1순위부터 자동 확정 ✅
- **성공:** `redirect:/admin/sections?courseId={courseId}` + flash `message`(자동 확정 인원 포함 💡)
- **실패:** `SECTION_NOT_FOUND`, `CAPACITY_OUT_OF_RANGE`, `CAPACITY_BELOW_ACTIVE`

### SE-04. 담당 강사 지정·변경
`POST /admin/sections/{sectionId}/instructor` · 🛠 · 팀원 2 / #10

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `sectionId` | Long | Y |
| Form | `instructorId` | Long | Y |

- **처리:** `SectionService.changeInstructor(sectionId, instructorId)` ✅
  - 수강생 분반 이동 없음 (D-59)
  - 평가 잠금·작성 중 피드백 초안·7일 마감 **그대로 승계** (평가 권한은 "현재 분반 담당자"로 판단하므로 별도 이관 없음)
  - 새 담당 강사에게 `notify(SECTION_ASSIGNED)`
  - 담당 공백이 해소되면 `fillVacancies()`
- **성공:** `redirect:/admin/sections?courseId={courseId}` + flash `message`
- **실패:** `SECTION_NOT_FOUND`, `USER_NOT_FOUND`, `INSTRUCTOR_NOT_GRANTED`

### WL-01. 강의별 대기열 조회
`GET /admin/waitlists` · 🛠 · `AdminWaitlistController` · 팀원 2 / #10 · A-S12

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Query | `courseId` | Long | N |

- **성공:** VIEW `admin/waitlists`

| 모델 | 타입 | 설명 |
|---|---|---|
| `courses` | `List<CourseDTO>` | 강의별 대기 인원 요약 |
| `waitlist` | `List<WaitlistDTO>` | 순번, 학습자(실명·아이디 💡 관리자 열람), 대기 신청 시각 |
| `sections` | `List<SectionDTO>` | 분반별 남은 자리 |
| `recentConfirmed` | `List<WaitlistDTO>` | 최근 자동 확정 내역 (개수 ❓) |

- **조회만.** 순번 변경·수동 확정 버튼 없음 (D-53·D-59)

---

## 7. 상세 명세 — 학습·진도·영상

### ML-01. 나의 학습 현황
`GET /my/learning` · 👤 · `MyLearningController` · 팀원 2 / #11 · U-S08

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `status` | String | N | 탭 필터 💡: `ALL`(기본) / `WAITING`(대기) / `LEARNING`(수강 중) / `COMPLETED` / `ENDED`(취소·만료) |

- **처리:** `MyLearningService.getMyCourses(userId, status)` — 진도는 `ProgressService.getLessonStatuses()`, 평가 상태는 `ReviewQueryService.getLatestStatus()` (팀원 3 계약)
- **성공:** VIEW `learning/my-learning`

`MyCourseDTO` (항목별)

| 필드 | 설명 |
|---|---|
| 강의명·수준·취미 | |
| 상태 | 대기(n번) / 학습 중 / 평가 대기 / 평가 중 / 반려 / 수료 / 만료 / 취소 |
| 분반명 | 대기 중이면 없음 |
| 남은 기간 | 확정+30일 기준 D-day ✅ |
| 차시별 진도 | 3차시 각각 비율·완료 여부 |
| 과제 평가 상태 | `ReviewStatus` 또는 미제출 |
| 23일 경과 안내 | 남은 기간 7일 미만이면 "반려 시 재제출 기회가 없을 수 있습니다" ✅ D-41 |
| 버튼 | 이어서 학습 / 최종 과제 / 수강 취소 / 대기 취소 / 다시 보기(수료) |

- 준비 중으로 바뀐 취미의 기존 강의도 그대로 진입 가능 ✅ D-20
- 다른 사람의 학습 현황은 볼 수 없음 (본인만)

### LE-01. 차시 학습 화면
`GET /lessons/{lessonId}` · 👤 · `LessonController` · 팀원 3 / #12 · U-S06

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `lessonId` | Long | Y |
| Query | `enrollmentId` | Long | Y |

- **처리:** 본인 수강인지 확인 → `VideoAccessPolicy`(수강 30일 이내 **또는** 수료, D-42) → `LessonViewDTO` 조립
- **성공:** VIEW `learning/lesson`

| 모델 | 타입 | 설명 |
|---|---|---|
| `lesson` | `LessonViewDTO` | 강의명, 차시 번호·제목·학습 목표, 영상 URL(`/videos/{lessonId}?enrollmentId=`) |
| `lessons` | `List<LessonProgressDTO>` | 3차시 목록 + 각 진도 비율·완료 시각 (차시 자유 선택) |
| `canSubmit` | boolean | 3차시 모두 완료 시 [최종 과제로] 버튼 활성 — **자동 이동 없음** ✅ |

- **실패:** `ENROLLMENT_NOT_FOUND`, `LESSON_NOT_FOUND`(다른 강의의 차시 포함), `VIDEO_ACCESS_DENIED`

### LE-02. 재생 시간 보고
`POST /lessons/{lessonId}/progress` · 👤 · `LessonController` · 팀원 3 / #12 · `@ResponseBody`

`lesson-progress.js`가 재생 중에 주기적으로 호출 🟡(미학습 JS)

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Path | `lessonId` | Long | Y | |
| Form | `enrollmentId` | Long | Y | |
| Form | `streamKey` | String | Y | 재생을 시작할 때마다 새로 만드는 값(최대 64자) — DB `lesson_progresses.playback_stream_key` |
| Form | `seq` | long | Y | 같은 `streamKey` 안의 보고 순번(1, 2, 3…) — DB `last_ping_seq` |
| Form | `playing` | boolean | Y | 재생 중 여부 (일시정지 중 보고는 적립 안 함) |

- **처리:** `ProgressService.report(userId, enrollmentId, lessonId, streamKey, seq, playing)` ✅
  - 같은 `streamKey`에서 `seq`가 직전 값 이하이면(순서 뒤바뀜·중복) **적립하지 않음** — DB 확정(11-06)
  - 브라우저가 보낸 시간을 믿지 않고 **서버가 관측한 경과 시간(직전 보고 이후)을 상한**으로 누적 (DEV_REVIEW §2-7)
  - 배속 재생해도 실제 경과 시간만 인정
  - `played_sec ≥ duration_sec × 0.6` 이면 `LessonProgress.complete(now)` (1회만)
- **성공:** JSON `ProgressReportDTO` 💡

```json
{ "playedSec": 412, "durationSec": 600, "ratio": 0.68, "completed": true }
```

- **실패 (JSON):** `ENROLLMENT_NOT_FOUND`, `LESSON_NOT_FOUND`, `VIDEO_ACCESS_DENIED`, `ENROLLMENT_EXPIRED`
- ❓ 보고 주기(DEV_REVIEW "약 5초" 후보), 한 번에 인정하는 최대 시간

### VD-01. 영상 출력
`GET /videos/{lessonId}` · 👤 · `LessonVideoController` · 팀원 3 / #12

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `lessonId` | Long | Y |
| Query | `enrollmentId` | Long | Y |

- **처리:** `VideoAccessPolicy` 통과 시 `HttpServletResponse`로 파일 전송
- **성공:** FILE, `Content-Type: video/mp4` ❓(영상 형식 미정), 영상 경로는 시드 데이터
- **실패:** 403 `VIDEO_ACCESS_DENIED`(만료 미수료자 포함), 404 `LESSON_NOT_FOUND`
- ❓ `Range` 요청(구간 이동) 지원 여부 — 미지원이면 시크바 이동이 제한될 수 있음 (#01 결정)

---

## 8. 상세 명세 — 최종 과제·평가

### AS-01. 최종 과제 화면 (작성·상태)
`GET /assignments` · 👤 · `AssignmentController` · 팀원 3 / #13 · U-S07

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Query | `enrollmentId` | Long | Y |

- **성공:** VIEW `review/assignment`

| 모델 | 타입 | 설명 |
|---|---|---|
| `state` | String 💡 | `LOCKED_LESSONS`(3차시 미완료) / `NOT_SUBMITTED` / `WAITING`(평가 대기, 수정 가능) / `IN_REVIEW`(평가 중, 수정 잠금) / `REJECTED`(재제출 가능) / `APPROVED`(수료) / `EXPIRED` |
| `rounds` | `List<ReviewStatusDTO>` | 제출 회차 이력 (제출 시각, 마감, 상태, 반려 사유) |
| `work` | `WorkDTO` | 현재 제출물 (이미지·제목·설명) |
| `form` | `AssignmentSubmitDTO` | 제출·수정 폼 (제목 기본값 = 강의명 ✅) |
| `lateWarning` | boolean | 남은 기간 7일 미만 → "반려 시 재제출 기회가 없을 수 있습니다" ✅ D-41 |

- **실패:** `ENROLLMENT_NOT_FOUND`

### AS-02. 최초 제출
`POST /assignments` · 👤 · `AssignmentController` · 팀원 3 / #13 · `multipart/form-data`

`AssignmentSubmitDTO` + 이미지

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `enrollmentId` | Long | Y | |
| Form | `title` | String | Y | 기본값 = 강의의 과제 제목(예: "색연필화 기초 — 최종 과제"), 수정 가능 ✅ / 최대 200자(DB) |
| Form | `description` | String | N | 설명 (DB TEXT) |
| Form | `visibility` | `Visibility` | N | 기본 `PRIVATE` ✅ |
| File | `images` | MultipartFile[] | Y | 1~5장, JPG/PNG/WebP, 장당 50MB (§1-10) |

- **처리:** `SubmissionPolicy`(3차시 완료·30일 이내·진행 중 평가 0건) → `WorkService.saveFinalWorkVersion(...)`(팀원 4 계약 — 작품·**버전**·이미지 저장, 버전 id 반환) → `ReviewRound.create(enrollmentId, workVersionId, roundNo = 1, submittedAt, dueAt = +7×24h)` → 상태 `WAITING` → 담당 강사에게 `notify(REVIEW_SUBMITTED)`
- **성공:** `redirect:/assignments?enrollmentId={id}` + flash `message`("제출되었습니다. 담당 강사의 평가를 기다려 주세요.") — **제출 즉시 수료가 아님** ✅
- **실패:** `LESSONS_NOT_COMPLETED`, `ENROLLMENT_EXPIRED`, `ALREADY_SUBMITTED`, `INVALID_IMAGE_COUNT`, `INVALID_IMAGE_FILE`, `FILE_TOO_LARGE`, `FILE_SAVE_FAILED`(500)
- 한 장이라도 실패하면 평가 대기도 만들지 않음 ✅

### AS-03. 평가 시작 전 수정
`POST /assignments/{reviewRoundId}/edit` · 👤 · 팀원 3 / #13 · `multipart/form-data`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Path | `reviewRoundId` | Long | Y | |
| Form | `title`, `description`, `visibility` | | | AS-02와 같음 |
| File | `images` | MultipartFile[] | N ❓ | 보내면 이미지 전체 교체, 안 보내면 유지 💡 |

- **처리:** `AssignmentService.edit()` → `WorkService.updateFinalWorkVersion()` — `WAITING`일 때만, **같은 버전**을 고침(새 버전 없음), **마감(7일) 변경 없음** ✅
- **성공:** `redirect:/assignments?enrollmentId={id}` + flash `message`
- **실패:** `REVIEW_NOT_FOUND`, `SUBMISSION_LOCKED`(평가 시작됨), `ENROLLMENT_EXPIRED`, 이미지 관련 코드

### AS-04. 반려 후 정식 재제출
`POST /assignments/resubmit` · 👤 · 팀원 3 / #13 · `multipart/form-data`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `enrollmentId` | Long | Y | |
| Form | `title`, `description`, `visibility` | | | AS-02와 같음 |
| File | `images` | MultipartFile[] | ❓ | 기존 이미지 유지 허용 여부 미정 |

- **처리:** `AssignmentService.resubmit()` — 직전 회차 `REJECTED` + 수강 30일 이내만 → `saveFinalWorkVersion()`으로 **새 버전** → **새 `ReviewRound`**(round_no + 1, 새 7일 마감) → `notify(REVIEW_SUBMITTED)` ✅ / 횟수 제한 없음
- **성공:** `redirect:/assignments?enrollmentId={id}` + flash `message`
- **실패:** `RESUBMIT_NOT_ALLOWED`, `ENROLLMENT_EXPIRED`, `ALREADY_SUBMITTED`, 이미지 관련 코드
- ✅ 재제출 = 새 버전 + 새 평가 회차 (DB 확정 11-06). 승인된 과제의 수정·삭제(D-63)는 **미정 → 만들지 않음**

### AS-05. 평가 결과·공식 피드백
`GET /assignments/{reviewRoundId}/result` · 👤 · `AssignmentController` · 팀원 3 / #15 · U-S14

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `reviewRoundId` | Long | Y |

- **성공:** VIEW `review/result`

| 모델 | 타입 | 설명 |
|---|---|---|
| `review` | `ReviewDetailDTO` | 결과(승인/반려), 공식 피드백 전문 또는 반려 사유, 작성 강사, 결정 시각 |
| `badges` | `List<BadgeDTO>` | 승인 시 획득 배지 (수준 배지·마스터 배지) |
| `canResubmit` | boolean | 반려 + 기간 내 → 재제출 링크 |

- 알림 `REVIEW_APPROVED`/`REVIEW_REJECTED` 클릭 시 이 화면으로 이동
- **실패:** `REVIEW_NOT_FOUND`(본인 것 아님 포함), 평가 확정 전이면 `INVALID_REVIEW_STATE` 💡
- 복습 추천(FR-032)·탈퇴 강사 표시는 **P2 — 제외**

---

### RV-01. 강사 평가 목록
`GET /instructor/reviews` · 🎓 · `InstructorReviewController` · 팀원 3 / #14 · T-S03

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `tab` | String | N | `WAITING`(기본) / `IN_REVIEW` / `DONE` ✅ |
| Query | `sectionId` | Long | N | 분반 필터 |
| Query | `page` | int | N | 💡 |

- **처리:** **현재 본인이 담당하는 분반**의 평가만 조회
- **성공:** VIEW `instructor/reviews`

`ReviewListDTO` (항목별)

| 필드 | 설명 |
|---|---|
| 학습자 실명 | 담당 강사에게만 표시 (NFR-04) |
| 강의·분반 | |
| 제출 시각·마감 시각 | 마감 = 제출(또는 정식 재제출)+7×24h |
| `dueSoon` | 마감까지 48시간 이하 → "마감 임박" 경고 ✅ |
| `overdue` | 마감 지남 → "기한 초과" 경고 (평가 의무는 계속) ✅ |

### RV-02. 평가 상세
`GET /instructor/reviews/{reviewRoundId}` · 🎓 · 팀원 3 / #14 · T-S04

- **처리:** 현재 분반 담당자인지 확인. **단순 열람은 잠금이 아님** ✅
- **성공:** VIEW `instructor/review-detail`

| 모델 | 타입 | 설명 |
|---|---|---|
| `review` | `ReviewDetailDTO` | 제출물(이미지·설명), 학습자 진도, 이전 회차 이력, 상태, 마감, 작성 중 초안 |
| `decisionForm` | `ReviewDecisionDTO` | 승인/반려 폼 |

- 이전 담당자가 확정한 피드백은 **읽기 전용**으로 보존 표시 ✅
- **실패:** `REVIEW_NOT_FOUND`, `NOT_ASSIGNED_INSTRUCTOR`

### RV-03. 평가 시작 (수정 잠금)
`POST /instructor/reviews/{reviewRoundId}/start` · 🎓 · 팀원 3 / #14

- **처리:** `ReviewService.start(instructorId, reviewRoundId)` → `WAITING` → `IN_REVIEW`, 학습자 수정 잠금
- **성공:** `redirect:/instructor/reviews/{reviewRoundId}` + flash `message`
- **실패:** `NOT_ASSIGNED_INSTRUCTOR`, `INVALID_REVIEW_STATE`(WAITING 아님)

### RV-04. 평가 시작 취소
`POST /instructor/reviews/{reviewRoundId}/cancel-start` · 🎓 · 팀원 3 / #14

- **처리:** `ReviewService.cancelStart(...)` — **초안이 비어 있을 때만**, `IN_REVIEW` → `WAITING`, **마감 유지** ✅ (D-56). 수강 기간 내일 때만 학습자 수정 재허용
- **성공:** `redirect:/instructor/reviews/{reviewRoundId}`
- **실패:** `NOT_ASSIGNED_INSTRUCTOR`, `INVALID_REVIEW_STATE`, `CANNOT_CANCEL_START`(초안 작성됨)

### RV-05. 피드백 초안 저장
`POST /instructor/reviews/{reviewRoundId}/draft` · 🎓 · 팀원 3 / #14

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `draft` | String | Y | 길이 상한 ❓ |

- **처리:** `ReviewService.saveDraft(...)` — `IN_REVIEW`일 때만. 담당 변경 시 초안은 새 담당에게 승계 ✅
- **성공:** `redirect:/instructor/reviews/{reviewRoundId}` + flash `message`("임시 저장되었습니다.")
- **실패:** `NOT_ASSIGNED_INSTRUCTOR`, `INVALID_REVIEW_STATE`

### RV-06. 수료 승인
`POST /instructor/reviews/{reviewRoundId}/approve` · 🎓 · 팀원 3 / #14

`ReviewDecisionDTO`

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `content` | String | Y | **공식 피드백 50자 이상** `@Size(min=50)` + 엔티티 재검증 ✅ |

> ✅ DB에는 피드백 칸이 **하나**(`review_rounds.feedback_body`, 50자 이상 CHECK)다. HTML의 "잘한 점 / 개선할 점 / 다음 연습"은 작성 가이드로만 보여 주고 `content` 하나로 받는다(F-09: 각 칸 필수 아님).

- **처리:** 한 트랜잭션 안에서 ✅
  1. `ReviewRound.approve(content, now)` → `APPROVED`
  2. `EnrollmentService.complete(enrollmentId)` → 수료·정원 반환 → `fillVacancies`
  3. `NotificationService.notify(학습자, REVIEW_APPROVED …)`, `notify(학습자, COURSE_COMPLETED …)`
- **성공:** `redirect:/instructor/reviews?tab=DONE` + flash `message`
- **실패:** `NOT_ASSIGNED_INSTRUCTOR`, `INVALID_REVIEW_STATE`(IN_REVIEW 아님), `FEEDBACK_TOO_SHORT` → 상세 화면으로 돌아가 입력값 유지 💡
- 관리자는 평가하지 않음. 확정 결과 정정 버튼 없음 (확장 D-37)

### RV-07. 반려
`POST /instructor/reviews/{reviewRoundId}/reject` · 🎓 · 팀원 3 / #14

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `content` | String | Y | **반려 사유 50자 이상** ✅ (DB 추적표 O-04 종결값) |

- **처리:** `ReviewRound.reject(content, now)` → `REJECTED` → `notify(REVIEW_REJECTED)` → **수강 기간이 이미 만료**됐으면 `EnrollmentService.closeAfterRejection()`(정원 반환)
- **성공:** `redirect:/instructor/reviews?tab=DONE` + flash `message`
- **실패:** RV-06과 같음

---

## 9. 상세 명세 — 작품·댓글·파일

### FL-01. 작품 이미지 출력
`GET /files/works/{fileId}` · 로그인 · `WorkFileController` · 팀원 4 / #16

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `fileId` | Long | Y |

- **처리:** `WorkAccessPolicy` — 열람 가능 조건 ✅
  - 공개 + 관리자 숨김 아님 → 로그인 회원 누구나
  - 비공개 또는 관리자 숨김 → **작성자 본인**, (최종 과제면) **현재 담당 강사**, 관리자
- **성공:** FILE, `Content-Type`은 저장된 MIME (`image/jpeg`·`image/png`·`image/webp`)
- **실패:** 403 `WORK_ACCESS_DENIED`, 404 `NOT_FOUND`
- 모든 작품 `<img src>`는 이 URL을 사용 (업로드 폴더를 정적 경로로 직접 노출하지 않음 ✅)

### WB-01. 작품 게시판
`GET /works` · 로그인 · `WorkBoardController` · 팀원 4 / #18 · U-S09, T-S10

`WorkSearchDTO` (`@ModelAttribute`, 모두 선택)

| 위치 | 이름 | 타입 | 설명 | 등급 |
|---|---|---|---|---|
| Query | `categoryId` | Long | 대분류 (취미별 분리 게시판) | P1 |
| Query | `hobbyId` | Long | 세부 취미 필터 | P2 |
| Query | `level` | `CourseLevel` | 수준 태그 필터 | P2 |
| Query | `kind` | `WorkKind` | `FINAL`/`GENERAL` | P2 |
| Query | `keyword` | String | 제목·작가 닉네임 검색 | P2 |
| Query | `sort` | String | `latest`(기본, 최신 공개순) / `likes` ✅ D-09 | P2 |
| Query | `page` | int | 💡 | |

- **처리:** `WorkBoardService.search(cond)` — **공개 + 관리자 숨김 아님**만
- **성공:** VIEW `work/board`, 모델 `works`(`List<WorkDTO>`: 썸네일, 제목, 작가 **닉네임**, 수준·유형 태그, 좋아요 수), `cond`
- 실명·아이디는 표시하지 않음 ✅ / 신고 버튼 없음 (확장 D-33)

### WB-02. 작품 상세
`GET /works/{workId}` · 로그인 · `WorkBoardController` · 팀원 4 / #18 · U-S10

- **성공:** VIEW `work/detail`

| 모델 | 타입 | 설명 |
|---|---|---|
| `work` | `WorkDTO` | 이미지들(`/files/works/{fileId}`), 제목, 설명, 작가 닉네임, 취미·수준·유형, 좋아요 수, 내가 좋아요 했는지 |
| `comments` | `List<CommentDTO>` | 댓글 + 1단계 답글. 삭제 댓글은 "삭제된 댓글입니다", 탈퇴 작성자는 "--탈퇴한 사용자 입니다--" ✅ |
| `canComment` | boolean | `CommentPolicy` 결과 (배지 수준·강사 취미 권한) |
| `isOwner` | boolean | 작성자면 [수정]·[공개 설정]·(숨김 시) [조치 사유] 버튼 |

- **실패:** `WORK_NOT_FOUND`, `WORK_ACCESS_DENIED`(비공개·숨김 작품을 권한 없는 사람이 열람)

### WB-03. 좋아요 토글
`POST /works/{workId}/like` · 로그인 · `WorkBoardController` → `CommentService.toggleLike` · 팀원 4 / #18 · `@ResponseBody`

- **처리:** 작품당 1회, 다시 누르면 취소, **본인 작품도 가능** ✅
- **성공:** JSON 💡

```json
{ "liked": true, "likeCount": 12 }
```

- **실패 (JSON):** `WORK_NOT_FOUND`, `WORK_ACCESS_DENIED`

### CM-C1. 댓글·답글 작성
`POST /comments` · 로그인 · `CommentController` · 팀원 4 / #18

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `workId` | Long | Y | |
| Form | `parentId` | Long | N | 있으면 답글 (**1단계만** — 답글의 답글 불가) ✅ |
| Form | `content` | String | Y | 최대 1000자(DB) |

- **처리:** `CommentPolicy` ✅ — 작품의 취미·수준 기준 현재 배지(입문 배지→입문 / 중급→입문·중급 / 고급→전체), 해당 취미 권한 강사 가능, **작품 작성자는 배지 무관 답글** → 저장 → `notify(작품 작성자, COMMENT_ON_MY_WORK)` 또는 `notify(원 댓글 작성자, REPLY_TO_MY_COMMENT)`, 해당 취미 담당 강사에게 `notify(PUBLIC_COMMENT)`
- **성공:** `redirect:/works/{workId}#comment-{commentId}`
- **실패:** `WORK_NOT_FOUND`, `COMMENT_NOT_ALLOWED`, `INVALID_INPUT`(답글에 답글)

### CM-C2. 댓글 수정
`POST /comments/{commentId}/edit` · 로그인 · 팀원 4 / #18

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Path | `commentId` | Long | Y |
| Form | `content` | String | Y |

- **처리:** 본인 댓글만 ✅
- **성공:** `redirect:/works/{workId}#comment-{commentId}`
- **실패:** `NOT_FOUND`, `ACCESS_DENIED`

### CM-C3. 댓글 삭제
`POST /comments/{commentId}/delete` · 로그인 · 팀원 4 / #18

- **처리:** 본인 댓글만. 본문을 "삭제된 댓글입니다"로 바꾸고 **답글은 유지** ✅ (소프트 삭제)
- **성공:** `redirect:/works/{workId}`
- **실패:** `NOT_FOUND`, `ACCESS_DENIED`

### MW-01. 일반 작품 등록 화면
`GET /my/works/new` · 👤 · `MyWorkController` · 팀원 4 / #19 · U-S11

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `hobbyId` | Long | N | 취미를 고르면 화면을 다시 불러 참고 피드백·수준 선택지를 갱신 💡 (JS 없이 동작) |

- **성공:** VIEW `work/form`

| 모델 | 타입 | 설명 |
|---|---|---|
| `hobbies` | `List<HobbyDTO>` | **운영 중** 세부 취미만 ✅ D-29 |
| `allowedLevels` | `List<CourseLevel>` | 배지 없으면 `BASIC`만, 있으면 최고 배지 이하 (`BadgeService.getHighestLevel`) ✅ |
| `reference` | `FeedbackRefDTO` 또는 null | 선택 취미의 **본인 최근 공식 피드백 1개 전문** (승인 피드백 또는 반려 사유). 없으면 "참고 가능한 최근 공식 피드백이 없습니다" + 체크박스 비활성 ✅ D-17 |
| `workFormDTO` | `WorkFormDTO` | 기본값 수준 `BASIC`, 공개 `PRIVATE` |

### MW-02. 일반 작품 등록
`POST /my/works` · 👤 · `MyWorkController` · 팀원 4 / #19 · `multipart/form-data`

`WorkFormDTO` + 이미지

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `title` | String | Y | 직접 입력 ✅ D-18 / 길이 ❓ |
| Form | `hobbyId` | Long | Y | 운영 중 취미만 |
| Form | `level` | `CourseLevel` | Y | 기본 `BASIC`, 보유 배지 이하 |
| Form | `description` | String | N | |
| Form | `visibility` | `Visibility` | N | 기본 `PRIVATE` |
| Form | `referenceReviewId` | Long | N | "이번 작품에 참고한 피드백" 체크 시 |
| Form | `reflection` | String | N | 한 줄 회고 (안내문 D-14) / 길이 ❓ |
| File | `images` | MultipartFile[] | Y | 1~5장 (§1-10) |

- **처리:** `WorkService.saveGeneralWork(userId, dto, images)` → `WorkLevelPolicy` 검사 → 저장
- **성공:** `redirect:/my/works/growth` + flash `message`
- **실패:** `HOBBY_NOT_FOUND`, `HOBBY_NOT_OPERATING`, `LEVEL_TAG_NOT_ALLOWED`, `INVALID_IMAGE_COUNT`, `INVALID_IMAGE_FILE`, `FILE_TOO_LARGE`, `FILE_SAVE_FAILED` / 참고 피드백이 본인 것이 아니면 `ACCESS_DENIED`
- 업로드 일부 실패 시 등록 미완료 (수료와 무관) ✅

### MW-03. 일반 작품 수정 화면
`GET /my/works/{workId}/edit` · 👤 · 팀원 4 / #19

- **성공:** VIEW `work/form` (MW-01 모델 + 기존 값)
- **실패:** `WORK_NOT_FOUND`(본인 일반 작품 아님 — 최종 과제는 AS-03에서 수정)

### MW-04. 일반 작품 수정
`POST /my/works/{workId}/edit` · 👤 · 팀원 4 / #19 · `multipart/form-data`

- **요청:** MW-02와 같음. `images`는 선택 ❓ (보내면 교체 💡)
- **처리:** 수준 태그는 **수정 시점의 배지**로 다시 검사 ✅. 관리자 숨김은 작성자가 수정해도 해제되지 않음 ✅. 수정해도 타임라인 순서 불변 ✅
- **성공:** `redirect:/works/{workId}` + flash `message`
- **실패:** MW-02와 같음 + `WORK_NOT_FOUND`

### MW-05. 공개 설정 변경
`POST /my/works/{workId}/visibility` · 👤 · 팀원 4 / #19 💡

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `visibility` | `Visibility` | Y |

- **처리:** 일반 작품·최종 과제 모두 본인만. 관리자 숨김 중이면 공개로 바꿔도 노출되지 않음 ✅
- **성공:** `redirect:` 이전 화면 (`returnUrl` 💡) + flash `message`
- **실패:** `WORK_NOT_FOUND`

### MW-06. 회고 저장
`POST /my/works/{workId}/reflection` · 👤 · 팀원 4 / #19

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `reflection` | String | N (빈 값이면 삭제) |

- 선택 입력, 수료와 무관 ✅
- **성공:** `redirect:/my/works/growth`
- **실패:** `WORK_NOT_FOUND`

### MW-07. 개인 성장 타임라인
`GET /my/works/growth` · 👤 · `MyWorkController` · 팀원 4 / #19 · U-S12

> 작업분배 문서에서는 `/my/growth`로 적었으나, 생성된 `MyWorkController`의 접두사(`/my/works`)에 맞춰 `/my/works/growth`로 정리했다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `hobbyId` | Long | N | 없으면 전체 보기 |

- **처리:** `GrowthService.getTimeline(userId, hobbyIdOrNull)`
- **성공:** VIEW `work/growth`, 모델 `timeline`(`TimelineDTO`)

| `TimelineDTO` 필드 | 설명 |
|---|---|
| `items` | 최종 과제 + 일반 작품, **등록일(유효 제출일) 순** — 수정해도 순서 불변 ✅ |
| `first` / `latest` | 취미 선택 시 "나의 첫 시작"·"가장 최근 실습" 썸네일 2개 (작품 1개면 1개, 전체 보기에서는 없음) ✅ D-13 |
| 각 항목 | 회고, 연결된 참고 피드백 ✅ D-17 |

### MW-08. 관리자 조치 사유 확인
`GET /my/works/{workId}/moderation` · 👤 · `MyWorkController` · 팀원 4 / #20 · U-S16

- **성공:** VIEW `work/moderation`, 모델: 작품 정보, 숨김·복구 이력(일시, 사유)
- 작성자 본인만. 숨김 중에도 본인·담당 강사는 원문 열람 가능 ✅ Q-02
- **실패:** `WORK_NOT_FOUND`

### MW-09. 일반 작품 삭제 — **P2 (이번 범위 밖)**
`POST /my/works/{workId}/delete` · FR-050 / D-32 — 만들지 않음

### AW-01. 관리자 작품 관리 목록
`GET /admin/works` · 🛠 · `AdminWorkController` · 팀원 4 / #20 · A-S08, A-S14(HTML)

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `hidden` | Boolean | N | `true` 숨김만 / `false` 공개 중만 / 없으면 전체 |
| Query | `categoryId`, `keyword`, `page` | | N | WB-01과 같은 검색 재사용 💡 |

- **성공:** VIEW `admin/works`, 모델 `works`(작품 + 숨김 여부 + 최근 조치)

### AW-02. 공개 작품 숨김
`POST /admin/works/{workId}/hide` · 🛠 · 팀원 4 / #20

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `reason` | String | Y | 사유 필수 / 최소 길이 ❓ |

- **처리:** `WorkModerationService.hide(adminId, workId, reason)` — **공개 작품만** → 조치 이력 저장 → `notify(작성자, WORK_HIDDEN)` ✅
- 숨김은 일반 노출만 차단. 수료일·배지·평가 의무·기존 피드백 **변경 없음** ✅
- **성공:** `redirect:/admin/works` + flash `message`
- **실패:** `WORK_NOT_FOUND`, `ALREADY_HIDDEN`, `INVALID_INPUT`(비공개 작품 💡)

### AW-03. 숨김 복구
`POST /admin/works/{workId}/restore` · 🛠 · 팀원 4 / #20

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `reason` | String | ❓ (복구 사유도 작성자 화면에 표시 — F-10 ⑤) |

- **처리:** `WorkModerationService.restore(...)` → `notify(작성자, WORK_RESTORED)`. 작성자가 그 사이 비공개로 바꿨다면 복구 후에도 비공개 ✅
- **성공:** `redirect:/admin/works` + flash `message`
- **실패:** `WORK_NOT_FOUND`, `NOT_HIDDEN`

---

## 10. 상세 명세 — 알림

### NT-01. 알림함
`GET /notifications` · 로그인 · `NotificationController` · 팀원 4 / #17 · U-S15, T-S09(HTML)

> 작업분배 문서에서는 강사용 `/instructor/notifications`를 따로 적었으나, 💡 **역할과 관계없이 `/notifications` 하나**를 쓰고 템플릿에서 역할별로 표시하는 것을 제안한다 (중복 코드 제거). 분리하려면 #17에서 결정.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `kind` | String | N | 유형 탭 필터 💡 |
| Query | `page` | int | N | |

- **성공:** VIEW `notification/list`, 모델 `notifications`(`List<NotificationDTO>`: 유형, 메시지, 생성 시각, 읽음 여부)
- 이메일 알림 없음, **사이트 알림만** ✅ D-57

| 알림 유형 (`NotificationKind`) | 받는 사람 | 발생 위치 | 이동 화면 | 등급 |
|---|---|---|---|---|
| `WAITLIST_CONFIRMED` | 학습자 | `SeatService.fillVacancies` | CO-02 강의 상세 또는 ML-01 | P0 |
| `REVIEW_APPROVED` | 학습자 | RV-06 | AS-05 | P0 |
| `REVIEW_REJECTED` | 학습자 | RV-07 | AS-05 | P0 |
| `COURSE_COMPLETED` | 학습자 | RV-06 | ML-01 | P0 |
| `COMMENT_ON_MY_WORK` / `REPLY_TO_MY_COMMENT` | 작품 작성자 / 원 댓글 작성자 | CM-C1 | WB-02 | P1 |
| `INQUIRY_ANSWERED` | 학습자 | II-03 | IQ-03 | P1 |
| `WORK_HIDDEN` / `WORK_RESTORED` | 작품 작성자 | AW-02 / AW-03 | MW-08 | P1 |
| `INQUIRY_RECEIVED` | 담당 강사 | IQ-02·IQ-04 | II-02 | P1 |
| `PUBLIC_COMMENT` | 해당 취미 담당 강사 | CM-C1 | WB-02 | P1 |
| `REVIEW_SUBMITTED` | 담당 강사 | AS-02·AS-04 | RV-02 | P0 💡 |
| `SECTION_ASSIGNED` | 새 담당 강사 | SE-02·SE-04 | IN-01 | P0 💡 |

> 알림 종류 13종은 DB CHECK 값과 같다(11-06). **피드백 마감 임박 알림은 만들지 않는다**(11-06 팀 결정) — 마감 임박·초과는 RV-01 목록의 경고로만 보여 준다.
> 저장 규칙: 대상 칼럼은 종류마다 하나(수강·평가 회차·작품·댓글·문의·분반), 같은 사건은 `dedupe_key`(기본 `종류:대상id`)로 한 번만 저장된다. 계약: `notify(receiverId, kind, targetId, title, body)`.

### NT-02. 알림 열기 (읽음 처리 + 이동)
`GET /notifications/{notificationId}` · 로그인 · 팀원 4 / #17

- **처리:** 본인 알림인지 확인 → 읽음 처리 → `targetType`·`targetId`로 이동 URL 결정
- **성공:** `redirect:{대상 화면 URL}` (위 표)
- **대상을 열 수 없을 때:** `redirect:/notifications` + flash `errorMessage`("알림이 가리키는 내용이 삭제되었거나 지금은 볼 수 없어요.") — 403·404 오류 화면으로 보내지 않는다
  - 대상 삭제(수강·평가 회차·작품·댓글·문의 스레드·분반)
  - 댓글 알림: 작품이 숨김·비공개가 됐고 받는 사람이 작성자가 아님 (`NotificationLinkResolver.workIdOfComment`)
  - 강사 알림 `INQUIRY_RECEIVED`·`REVIEW_SUBMITTED`: 분반 담당이 바뀌었거나 담당 공백이 돼 받는 강사가 지금 담당이 아님 (D-59, `isCurrentInstructorOf`)
- **실패:** `NOT_FOUND`(본인 알림 아님 포함)
- 💡 링크 클릭(GET)으로 상태를 바꾸는 방식이다. 엄격히 하려면 POST 폼 버튼으로 바꿀 수 있음 (#17 결정)

### NT-03. 모두 읽음
`POST /notifications/read-all` · 로그인 · 팀원 4 / #17

- **성공:** `redirect:/notifications` + flash `message`("N개의 알림을 읽음으로 바꿨어요.", 안 읽은 알림이 없었으면 "새로 읽음 처리할 알림이 없어요. 모두 읽은 상태예요.")

### NT-04. 안 읽은 알림 수 — 💡 선택
`GET /notifications/unread-count` · 로그인 · `@ResponseBody`

- **성공:** JSON `long` (예: `3`)
- 헤더 표시는 §1-8 공통 모델 `unreadCount`로 충분하면 **만들지 않아도 됨**

---

## 11. 상세 명세 — 1:1 문의 (P1)

> 텍스트만(첨부 없음), 학습자 간 메시지 없음, 강사의 일방 발송 없음 ✅ F-14

### IQ-01. 내 문의 목록
`GET /inquiries` · 👤 · `InquiryController` · 팀원 4 / #21 · U-S18

- **성공:** VIEW `inquiry/list`, 모델 `threads`(`List<InquiryDTO>`: 강의명, 현재 담당 강사, 마지막 메시지 시각, 답변 여부), `enrollments`(문의 가능한 수강 — 수강 중·수료한 강의)

### IQ-02. 문의 작성 (스레드 생성 또는 이어쓰기)
`POST /inquiries` · 👤 · `InquiryController` · 팀원 4 / #21

| 위치 | 이름 | 타입 | 필수 | 규칙 |
|---|---|---|---|---|
| Form | `enrollmentId` | Long | Y | 수강 중 또는 수료한 본인 수강 |
| Form | `title` | String | 새 스레드일 때 Y | 스레드 제목, 최대 200자 (DB `inquiry_threads.title` 필수) |
| Form | `content` | String | Y | 메시지 본문 (DB TEXT) |

- **처리:** `InquiryService.writeMessage(userId, enrollmentId, content)` — 강의별 스레드가 없으면 생성 💡, 있으면 이어쓰기 → `notify(담당 강사, INQUIRY_RECEIVED)` (같은 스레드의 새 메시지마다 알리려면 `dedupeKey` 지정)
- **성공:** `redirect:/inquiries/{threadId}`
- **실패:** `ENROLLMENT_NOT_FOUND`, `INQUIRY_NOT_ALLOWED`(담당 강사 공백 D-31, 대기·취소 수강)

### IQ-03. 문의 스레드 보기
`GET /inquiries/{threadId}` · 👤 · 팀원 4 / #21

- **성공:** VIEW `inquiry/thread`, 모델 `thread`(`InquiryDTO`), `messages`(`List<InquiryMessageDTO>`: 작성자 구분, 내용, 시각)
- 담당 변경 시 새 담당 강사가 스레드를 이어받음 ✅ D-08 (담당자를 분반에서 조회하므로 자동)
- **실패:** `NOT_FOUND`(본인 스레드 아님)

### IQ-04. 스레드에 메시지 추가
`POST /inquiries/{threadId}/messages` · 👤 · 팀원 4 / #21

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `content` | String | Y |

- **성공:** `redirect:/inquiries/{threadId}`
- **실패:** `NOT_FOUND`, `INQUIRY_NOT_ALLOWED`

### II-01. 강사 문의 목록
`GET /instructor/inquiries` · 🎓 · `InstructorInquiryController` · 팀원 4 / #21 · T-S06

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `answered` | Boolean | N | `false` = 미답변만 💡 |

- **처리:** **현재 담당 분반 학습자**의 스레드만 ✅
- **성공:** VIEW `instructor/inquiries`
- 답변은 1주일 내 권장 안내만, 경고 대상 아님 ✅

### II-02. 강사 문의 스레드
`GET /instructor/inquiries/{threadId}` · 🎓 · 팀원 4 / #21

- **성공:** VIEW `instructor/inquiry-thread`
- **실패:** `NOT_FOUND`, `NOT_ASSIGNED_INSTRUCTOR`

### II-03. 답변 작성
`POST /instructor/inquiries/{threadId}/answer` · 🎓 · 팀원 4 / #21

| 위치 | 이름 | 타입 | 필수 |
|---|---|---|---|
| Form | `content` | String | Y |

- **처리:** `InquiryService.answer(instructorId, threadId, content)` → 스레드 상태 `ANSWERED` → `notify(학습자, INQUIRY_ANSWERED)`
- **성공:** `redirect:/instructor/inquiries/{threadId}`
- **실패:** `NOT_FOUND`, `NOT_ASSIGNED_INSTRUCTOR`

---

## 12. 상세 명세 — 강사 LMS·관리자 홈·통계

> 집계 쿼리는 `InstructorStatQuery`(`EntityManager` native 쿼리)에서 처리해 다른 팀원의 Repository를 수정하지 않는다 ✅. 학습자 실명은 담당 강사·관리자에게만 표시 ✅ NFR-04.

### IN-01. 강사 홈 (담당 강의·분반)
`GET /instructor` · 🎓 · `InstructorHomeController` · 팀원 1 / #07 · T-S01

- **성공:** VIEW `instructor/home`, 모델 `sections`(`List<SectionSummaryDTO>`: 강의명, 분반 번호, 정원, 활성 수강 수, 평가 대기 수, 마감 임박 수 💡)
- 강사 로그인 직후 첫 화면 ✅ F-16

### IN-02. 담당 학습자 목록
`GET /instructor/students` · 🎓 · `InstructorStudentController` · 팀원 1 / #07 · T-S02

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `sectionId` | Long | N | 없으면 담당 분반 전체 |
| Query | `status` | String | N | 상태 필터 💡 (`LEARNING`/`WAITING_REVIEW`/`REJECTED`/`COMPLETED`/`EXPIRED`) |

- **처리:** `InstructorDashboardService.getStudents(instructorId, sectionId, status)`
- **성공:** VIEW `instructor/students`, 모델 `students`(`List<StudentProgressDTO>`: 실명, 강의, 분반, 진도율, 30일 잔여 기간, 과제 상태)
- **실패:** `SECTION_NOT_FOUND`, `NOT_ASSIGNED_INSTRUCTOR`(다른 강사 분반 — **타 분반 차단** ✅)

### IN-03. 담당 학습자 상세 💡
`GET /instructor/students/{enrollmentId}` · 🎓 · 팀원 1 / #07 · T-S02

- T-S02의 "차시별 완료·제출/피드백 이력"을 보여주는 화면. 목록에 펼쳐 보여줄 수 있으면 **만들지 않아도 됨** (HTML 확인 후 결정)
- **성공:** VIEW `instructor/student-detail` — 차시별 완료 시각(`ProgressService.getLessonStatuses`), 제출·평가 이력(`ReviewQueryService`)
- **실패:** `ENROLLMENT_NOT_FOUND`, `NOT_ASSIGNED_INSTRUCTOR`

### IN-04. 강사 통계
`GET /instructor/stats` · 🎓 · `InstructorStatController` · 팀원 1 / #07 · T-S05 **P0**

- **처리:** `InstructorStatService.getSectionStats(instructorId)`
- **성공:** VIEW `instructor/stats`, 모델 `stats`(`List<SectionStatDTO>`)

| `SectionStatDTO` | 설명 |
|---|---|
| 분반명 | |
| 수강 중 | |
| 수료 | |
| 반려 | |
| 평가 대기 | |

- 숫자는 DB VIEW `v_section_stats_screen`을 그대로 읽는다(11-06): 수강 중 = 평가 대기·진행 제외(`learning_count`), 평가 대기 = 대기+진행(`pending_review_count`), 수료 = 누적(`completed_count`), 반려 = 반려 회차 누적(`rejected_total`)
- **현재 숫자만** ✅ D-58. 월별 추이·수료율·평균 진도율은 후속. 본인·관리자만 열람

### AD-01. 관리자 홈
`GET /admin` · 🛠 · `AdminHomeController` · 팀원 1 / #07 · A-S01

- **성공:** VIEW `admin/home`, 모델 `summary`(`AdminSummaryDTO`: 강사 수(활성/첫 로그인 전), 분반 수, 담당 공백 분반 수 💡, 대기 인원 합계, 평가 대기 합계)
- 바로가기: 강사 계정(AI-01) · 분반(SE-01) · 대기열(WL-01) · 강사 통계(AD-02) · 작품 관리(AW-01) · 취미 조회(HB-03)
- 카테고리·강의 등록 메뉴 **없음** (시드 데이터, D-59)

### AD-02. 관리자 강사 통계
`GET /admin/stats` · 🛠 · `AdminStatController` · 팀원 1 / #07 · A-S13 **P0**

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `instructorId` | Long | N | 없으면 전체 강사 |

- **성공:** VIEW `admin/stats`, 모델 `stats` — 강사·분반별 수강 중·수료·반려·평가 대기 수 (IN-04와 같은 쿼리 재사용 ✅)

---

## 13. HTTP가 아닌 작업 (스케줄러)

### SC-01. 30일 수강 기간 만료 처리
`EnrollmentExpiryScheduler.expire()` · `@Scheduled` 🟡(미학습) · 팀원 2 / #10

| 항목 | 내용 |
|---|---|
| 실행 주기 | ❓ (예: 매 10분 💡) — `@EnableScheduling` 필요 |
| 대상 | 만료 시각(확정+30일)이 지났고 **평가할 과제가 없는** 수강 건 ✅ |
| 처리 | `EXPIRED`로 변경 → `SeatService.fillVacancies(courseId)` → 대기 1순위 자동 확정 + 알림 |
| 제외 | 기간 내 제출한 과제가 평가 대기·진행 중이면 **평가 끝날 때까지 정원 점유** ✅ (반려되면 RV-07에서 반환) |
| 동시성 | 사용자 요청과 같은 강의를 동시에 처리할 수 있으므로 `fillVacancies` 안의 `FOR UPDATE` 잠금을 반드시 거침 ✅ |
| 결과 | 처리 건수 로그 |

---

## 14. ErrorCode 표

> 생성된 `global/exception/ErrorCode.java`와 같은 목록이다. 새 코드가 필요하면 **팀원 1에게 요청**(파일 소유자 1명 원칙). "처리"는 §1-6 규칙 (400·409 → 원래 화면 flash / 403·404·500 → 에러 화면, JSON 요청은 §1-7 형식).

| ErrorCode | HTTP | 메시지 | 처리 | 사용하는 API |
|---|---|---|---|---|
| `INVALID_INPUT` | 400 | 입력값을 확인해 주세요. | flash | US-07, CM-C1, AW-02 |
| `ACCESS_DENIED` | 403 | 접근 권한이 없습니다. | 에러 화면 | CM-C2, CM-C3, MW-02 |
| `NOT_FOUND` | 404 | 요청한 페이지를 찾을 수 없습니다. | 에러 화면 | EN-02, FL-01, CM-C2, CM-C3, NT-02, IQ-03, IQ-04, II-02, II-03 |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했습니다. | 에러 화면 | 공통 (GlobalExceptionHandler) |
| `DUPLICATE_LOGIN_ID` | 409 | 이미 사용 중인 아이디입니다. | flash | US-02, AI-02 |
| `DUPLICATE_EMAIL` | 409 | 이미 가입된 이메일입니다. | flash | US-02, AI-02 |
| `DUPLICATE_NICKNAME` | 409 | 이미 사용 중인 닉네임입니다. | flash | US-02, US-06 |
| `EMAIL_NOT_VERIFIED` | 403 | 이메일 인증이 필요합니다. | 에러 화면 | AU-02 |
| `INVALID_TOKEN` | 400 | 인증 정보를 찾을 수 없습니다. | flash | AU-05, AU-09, AU-11, AU-12 |
| `TOKEN_EXPIRED` | 400 | 인증 기간이 만료되었습니다. | flash | AU-05, AU-09, AU-11, AU-12 |
| `USER_NOT_FOUND` | 404 | 회원을 찾을 수 없습니다. | 에러 화면 | AI-03, AI-04, AI-05, AI-06, SE-04 |
| `PASSWORD_CHANGE_REQUIRED` | 403 | 비밀번호 변경이 필요합니다. | 화면 요청은 C-09로 redirect, JSON 요청은 403 💡 | `PasswordChangeInterceptor` (AU-13·AU-14) |
| `INVALID_NEW_PASSWORD` | 400 | 사용할 수 없는 비밀번호입니다. | flash | AU-12, AU-14, US-07 |
| `HOBBY_NOT_FOUND` | 404 | 취미를 찾을 수 없어요. 지금은 공개되지 않았거나 없는 취미예요. | 에러 화면 | AI-05, AI-06, HB-02, CO-01, MW-02 |
| `HOBBY_NOT_OPEN` | 403 | 운영 중인 취미가 아닙니다. | 에러 화면 | HB-02, EN-01 |
| `COURSE_NOT_FOUND` | 404 | 강의를 찾을 수 없어요. 비공개로 바뀌었거나 없는 강의예요. | 에러 화면 | CO-02, EN-01, SE-01, SE-02 |
| `LESSON_NOT_FOUND` | 404 | 차시를 찾을 수 없습니다. | 에러 화면 | LE-01, LE-02, VD-01 |
| `ALREADY_ENROLLED` | 409 | 이미 수강 중인 강의입니다. | flash | EN-01 |
| `ALREADY_WAITING` | 409 | 이미 대기 중인 강의입니다. | flash | EN-01 |
| `CANNOT_REAPPLY` | 409 | 다시 신청할 수 없는 강의입니다. | flash | CO-02, EN-01 |
| `NO_ASSIGNABLE_SECTION` | 409 | 신청 가능한 분반이 없습니다. | flash | EN-01 |
| `ENROLLMENT_NOT_FOUND` | 404 | 수강 정보를 찾을 수 없어요. 나의 학습에서 강의를 다시 선택해 주세요. | 에러 화면 | EN-03, LE-01, LE-02, AS-01, IQ-02, IN-03 |
| `ENROLLMENT_EXPIRED` | 403 | 수강 기간이 만료되었습니다. | 에러 화면 | LE-02, AS-02, AS-03, AS-04 |
| `CANCEL_NOT_ALLOWED_DURING_REVIEW` | 409 | 평가 대기·진행 중에는 수강을 취소할 수 없습니다. | flash | EN-03 |
| `SECTION_NOT_FOUND` | 404 | 분반을 찾을 수 없습니다. | 에러 화면 | SE-03, SE-04, IN-02 |
| `CAPACITY_OUT_OF_RANGE` | 400 | 정원은 1~30명입니다. | flash | SE-02, SE-03 |
| `CAPACITY_BELOW_ACTIVE` | 409 | 현재 수강 인원보다 정원을 줄일 수 없습니다. | flash | SE-03 |
| `INSTRUCTOR_NOT_GRANTED` | 403 | 해당 취미 권한이 없는 강사입니다. | 에러 화면 | SE-02, SE-04 |
| `INSTRUCTOR_HAS_SECTIONS` | 409 | 담당 분반이 있는 강사는 비활성화할 수 없어요. 분반 관리에서 담당 강사를 먼저 바꿔 주세요. … | A-S04 flash | AI-04 |
| `GRANT_IN_USE` | 409 | 이 취미의 분반을 담당 중이라 권한을 회수할 수 없어요. 분반 관리에서 담당 강사를 먼저 바꿔 주세요. | A-S04 flash | AI-06 |
| `VIDEO_ACCESS_DENIED` | 403 | 수강 기간이 끝났거나 취소한 강의라 영상을 볼 수 없어요. 수료한 강의는 기간과 관계없이 다시 볼 수 있어요. | 에러 화면 | LE-01, LE-02, VD-01 |
| `LESSONS_NOT_COMPLETED` | 409 | 3차시를 모두 완료해야 제출할 수 있습니다. | flash | AS-02 |
| `SUBMISSION_LOCKED` | 409 | 평가가 시작되어 수정할 수 없습니다. | flash | AS-03 |
| `ALREADY_SUBMITTED` | 409 | 이미 평가 대기 중인 과제가 있습니다. | flash | AS-02, AS-04 |
| `INVALID_IMAGE_COUNT` | 400 | 이미지는 1~5장이어야 합니다. | flash | AS-02, MW-02 |
| `INVALID_IMAGE_FILE` | 400 | 지원하지 않는 이미지 형식입니다. | flash | AS-02, MW-02 |
| `FILE_TOO_LARGE` | 400 | 이미지 한 장은 50MB 이하여야 합니다. | flash | AS-02, MW-02 |
| `FILE_SAVE_FAILED` | 500 | 파일 저장에 실패했습니다. | 에러 화면 | AS-02, MW-02 |
| `REVIEW_NOT_FOUND` | 404 | 평가 정보를 찾을 수 없습니다. | 에러 화면 | AS-03, AS-05, RV-02 |
| `NOT_ASSIGNED_INSTRUCTOR` | 403 | 담당 강사만 평가할 수 있습니다. | 에러 화면 | RV-02, RV-03, RV-04, RV-05, RV-06, II-02, II-03, IN-02, IN-03 |
| `INVALID_REVIEW_STATE` | 409 | 지금 상태에서는 할 수 없는 작업이에요. 이미 처리되었거나 아직 평가가 끝나지 않았을 수 있어요. 화면을 새로 고쳐 확인해 주세요. | flash | EN-03, AS-05, RV-03, RV-04, RV-05, RV-06 |
| `FEEDBACK_TOO_SHORT` | 400 | 피드백·반려 사유는 50자 이상이어야 합니다. | flash | RV-06 |
| `CANNOT_CANCEL_START` | 409 | 평가 내용을 작성한 뒤에는 시작을 취소할 수 없습니다. | flash | RV-04 |
| `RESUBMIT_NOT_ALLOWED` | 409 | 재제출할 수 없습니다. | flash | AS-04 |
| `WORK_NOT_FOUND` | 404 | 작품을 찾을 수 없습니다. | 에러 화면 | WB-02, WB-03, CM-C1, MW-03, MW-04, MW-05, MW-06, MW-08, AW-02, AW-03 |
| `WORK_ACCESS_DENIED` | 403 | 열람 권한이 없는 작품입니다. | 에러 화면 | FL-01, WB-02, WB-03 |
| `HOBBY_NOT_OPERATING` | 400 | 운영 중인 취미만 선택할 수 있습니다. | flash | MW-02 |
| `LEVEL_TAG_NOT_ALLOWED` | 400 | 보유한 배지 수준 이하만 선택할 수 있습니다. | flash | MW-02 |
| `COMMENT_NOT_ALLOWED` | 403 | 댓글 작성 권한이 없습니다. | 에러 화면 | CM-C1 |
| `ALREADY_HIDDEN` | 409 | 이미 숨김 처리된 작품입니다. | flash | AW-02 |
| `NOT_HIDDEN` | 409 | 숨김 상태가 아닌 작품입니다. | flash | AW-03 |
| `INQUIRY_NOT_ALLOWED` | 403 | 현재 문의를 작성할 수 없습니다. | 에러 화면 | IQ-02, IQ-04 |

**💡 추가 제안 (현재 목록에 없음 — #01에서 결정)**

| ErrorCode | HTTP | 메시지 | 필요한 API |
|---|---|---|---|
| `PASSWORD_MISMATCH` | 400 | 현재 비밀번호가 일치하지 않습니다. | US-07 |
| `WAITLIST_NOT_FOUND` | 404 | 대기 정보를 찾을 수 없습니다. | EN-02 (없으면 `NOT_FOUND` 사용) |
| `COMMENT_NOT_FOUND` | 404 | 댓글을 찾을 수 없습니다. | CM-C2, CM-C3 (없으면 `NOT_FOUND`) |
| `INQUIRY_NOT_FOUND` | 404 | 문의를 찾을 수 없습니다. | IQ-03, IQ-04, II-02, II-03 (없으면 `NOT_FOUND`) |

**Spring 기본 예외 처리 (`GlobalExceptionHandler`, 배운기술 §전역 예외 처리 고도화)**

| 예외 | 원인 | 처리 |
|---|---|---|
| `BindException` | `@Valid @ModelAttribute` 검증 실패 | 폼 화면은 컨트롤러에서 `BindingResult`로 직접 처리, 그 외 400 |
| `MissingServletRequestParameterException` | 필수 쿼리 값 없음 (예: `enrollmentId`) | 400 `INVALID_INPUT` |
| `MethodArgumentTypeMismatchException` | 숫자 자리에 문자 등 | 400 `INVALID_INPUT` |
| `MaxUploadSizeExceededException` | 업로드 크기 초과 | 400 `FILE_TOO_LARGE` 💡 |
| `DataIntegrityViolationException` | UNIQUE 위반 등 | 409 (상황별 코드로 변환 💡) |
| `NoResourceFoundException` | 없는 URL | 404 `NOT_FOUND` |
| `AccessDeniedException` | 역할 권한 없음 | 403 화면 (Security `accessDeniedPage` 또는 핸들러) |
| `Exception` | 그 외 | 500 `INTERNAL_ERROR` |

---

## 15. 상태값(enum) 표

> 생성된 스켈레톤의 enum과 같고, **DB CHECK 값과 글자까지 같다**(11-06 확정). 바꾸려면 DB와 함께 바꾸고 전원 합의.

| enum | 값 | 사용하는 API |
|---|---|---|
| `Role` | `USER` / `INSTRUCTOR` / `ADMIN` | 전체 (권한) |
| `TokenPurpose` | `VERIFY_EMAIL` / `RESET_PASSWORD` / `RECOVER_LOGIN_ID` | AU-05~AU-12 |
| `ExposureState` | `OPERATING`(운영 중) / `PREPARING`(준비 중) / `PRIVATE`(비공개) | HB-01~03, EN-01, MW-01·02 |
| `CourseLevel` | `BASIC`(입문) / `INTERMEDIATE`(중급) / `ADVANCED`(고급) — DB에는 1·2·3 | CO-01, MW-01·02, WB-01, CM-C1 |
| `UserStatus` | `PENDING_EMAIL`(인증 전) / `ACTIVE` / `INACTIVE` | AU-02, AI-04 |
| `CourseStatus` | `OPEN` / `CLOSED` (MVP는 모두 OPEN) | CO-01·02 |
| `EnrollmentStatus` | `ACTIVE`(정원 점유) / `COMPLETED` / `CANCELLED` / `EXPIRED` | EN-01·03, ML-01, IN-02, SC-01 |
| `WaitlistStatus` | `WAITING` / `CONFIRMED` / `CANCELLED` | EN-01·02, WL-01 |
| `ReviewStatus` | `WAITING` / `IN_REVIEW` / `APPROVED` / `REJECTED` | AS-01~05, RV-01~07 |
| `WorkKind` | `FINAL`(최종 과제) / `GENERAL`(일반 작품) | WB-01, MW-* |
| `Visibility` | `PUBLIC` / `PRIVATE` | AS-02~04, MW-02·05 |
| `CommentStatus` | `NORMAL` / `DELETED` | CM-C3 |
| `InquiryStatus` | `OPEN` / `ANSWERED` / `CLOSED` | IQ-*, II-* |
| `NotificationKind` (13종) | `WAITLIST_CONFIRMED` / `REVIEW_APPROVED` / `REVIEW_REJECTED` / `COURSE_COMPLETED` / `WORK_HIDDEN` / `WORK_RESTORED` / `COMMENT_ON_MY_WORK` / `REPLY_TO_MY_COMMENT` / `INQUIRY_ANSWERED` / `INQUIRY_RECEIVED` / `PUBLIC_COMMENT` / `REVIEW_SUBMITTED` / `SECTION_ASSIGNED` | NT-01 (§10 표) |

**화면 표시용 상태 (enum 아님, DTO 안의 문자열) 💡**

| DTO | 값 | 설명 |
|---|---|---|
| `EnrollmentStatusDTO.state` | `CAN_APPLY` / `CAN_WAIT` / `WAITING` / `LEARNING` / `COMPLETED` / `CANNOT_REAPPLY` / `UNAVAILABLE` | CO-02 신청 영역 |
| `ApplyResultDTO.result` | `CONFIRMED` / `WAITING` | EN-01 결과 |
| AS-01 `state` | `LOCKED_LESSONS` / `NOT_SUBMITTED` / `WAITING` / `IN_REVIEW` / `REJECTED` / `APPROVED` / `EXPIRED` | 과제 화면 상태 |

> ✅ "활성 수강"(정원 점유) = `EnrollmentStatus.ACTIVE` 하나 — 학습 중, 평가 대기·진행(기간 만료 후 포함), 기간 내 반려 (DB 확정 11-06). 화면 표시용 `LEARNING`은 ACTIVE 중 평가 대기·진행이 아닌 것.

---

## 16. 미확정 사항 목록

> 이 표의 값이 정해지면 해당 API 칸의 ❓를 지우고 이 문서를 수정한다.

### 16-1. 결정이 필요한 값

| # | 항목 | 영향 API | 결정 담당 | 비고 |
|---|---|---|---|---|
| 1 | 이메일 인증·재설정 토큰 만료 시간 | AU-05·09·11·12 | 팀원 1 (#04) | 작업분배 §8 #11 |
| 2 | 메일 발송 실패 시 가입 롤백 여부 | US-02 | 팀원 1 (#04) | 작업분배 §8 #11 |
| 3 | 임시 비밀번호 생성 규칙 | AI-02·03 | 팀원 1 (#05) | 작업분배 §8 #11 |
| 4 | 아이디·닉네임 길이, 비밀번호 세 종류 문자 필수 여부 | US-02·06, AI-02 | 팀원 1 (#04) | HTML C-03 다시 확인 |
| 5 | 아이디 찾기·비밀번호 재설정 입력값 (이메일만 / 아이디+이메일) | AU-08·10 | 팀원 1 (#06) | HTML C-05 확인 |
| 6 | 계정 설정에서 수정 가능한 항목 범위 | US-05 | 팀원 1 (#06) | 자료 없음 |
| 7 | CSRF 사용 여부 | 모든 POST, LE-02·WB-03 | 팀원 1 (#02) | §1-5 |
| 8 | JSON 요청의 오류 응답 방식 | LE-02, WB-03, US-03·04 | 팀원 1 (#02) | §1-7 |
| 9 | `max-request-size` 값 | 업로드 API 전체 | 팀원 1 (#02) | 5×50MB 이상 |
| 10 | 페이지 크기 | WB-01, AW-01, NT-01, RV-01 | 팀원 4 (#18) | §1-9 |
| 11 | 재생 시간 보고 주기·최대 인정 시간 (형식은 DB 확정: streamKey·seq·playing) | LE-02 | 팀원 3 (#12) | DEV_REVIEW "약 5초" 후보 |
| 12 | 영상 형식, `Range` 요청 지원 여부 | VD-01 | 팀원 3 (#12) | 시크바 이동 영향 |
| 13 | 과제 수정·재제출 시 이미지 필수 여부 (교체/유지) | AS-03·04, MW-04 | 팀원 3·4 | |
| ~~14~~ | ~~공식 피드백 칸 구성~~ | RV-06 | — | ✅ DB 확정: 칸 하나 |
| ~~15~~ | ~~재제출을 새 행으로 저장하는지~~ | AS-04 | — | ✅ DB 확정: 새 버전 + 새 회차 |
| 16 | 숨김·복구 사유 필수 여부·최소 길이 | AW-02·03 | 팀원 4 (#20) | |
| 17 | 화면에서 쓸 글자 수 상한 | CM-C1·C2, IQ-02·04, II-03, AS-02, MW-02 | 각 담당 | DB 최대값: 작품·문의 제목 200, 회고 500, 댓글 1000, 숨김 사유 1000, 닉네임·아이디 50 (설명·문의 본문은 TEXT) |
| 18 | 만료 스케줄러 실행 주기 | SC-01 | 팀원 2 (#10) | |
| 19 | 메인 홈에 보여줄 항목 | CM-01 | 팀원 1 (#02) | HTML C-01 |
| 20 | 강사 알림함 분리 여부 (`/instructor/notifications`) | NT-01 | 팀원 4 (#17) | §10 제안 |
| 21 | 대기 취소 후 돌아갈 화면 | EN-02 | 팀원 2 (#09) | |
| 22 | 비활성 강사·취미 권한 회수 시 담당 분반 처리 | AI-04·06 | 팀원 1·2 | |

### 16-2. 이 명세서에서 정리·변경한 부분

| 내용 | 이유 |
|---|---|
| 성장 타임라인 URL `/my/growth` → `/my/works/growth` | 생성된 `MyWorkController` 접두사(`/my/works`)와 맞춤 |
| 강사 알림함을 `/notifications` 하나로 합치는 제안 | 같은 기능의 컨트롤러·템플릿 중복 방지 (확정 아님) |
| 좋아요를 `/works/{workId}/like`에 둠 | 좋아요는 작품에 대한 동작. 처리 로직은 작업분배대로 `CommentService.toggleLike` |
| 상태값·알림 종류·재생 보고 입력·분반 이름·문의 제목을 DB에 맞춤 (11-06) | 확정된 DB(`schema.sql`)와 일치시키기 위함 |
| 모든 변경 요청을 `POST`로 통일 | HTML 폼은 GET/POST만 지원, 수업에서 PUT·DELETE를 폼과 함께 쓰지 않음 |
| 본인 것이 아닌 자원은 404로 응답 | 다른 사람 데이터의 존재 여부를 드러내지 않기 위함 |

### 16-3. 이번 범위에서 만들지 않는 API

| 기능 | 이유 |
|---|---|
| 공개 프로필 (US-08) | P2 (FR-048) |
| 일반 작품 삭제 (MW-09) | P2 (FR-050) |
| 회원 탈퇴 | P2 (FR-040) |
| 대분류·세부 취미·강의·차시 등록·수정, 콘텐츠 검수 | 시드 데이터로 대체 (D-59, 기존 기획안 유지 결정) |
| 강사 약관 확인 (C-08), 회원가입 약관·만 14세 확인 | 11-06 팀 결정으로 제외 |
| 신고, 개인 제작 강의, 준강사, 강사 경고·정지 | 확장 / P2 |
| 영상 재생 중 로그인 연장·일시정지 2시간 규칙 | 구현 방식 미정 (작업분배 #06) |
