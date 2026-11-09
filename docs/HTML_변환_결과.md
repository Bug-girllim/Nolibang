# HTML 시안 → Thymeleaf 자동 변환 결과 (1단계)

> 2026-11-09 · 원본: `html/nolibang-lms-html.zip` (디자인 시안 75화면)
> 결과 위치: `lms-practice/src/main/resources/templates/`, `static/css/`, `static/images/`
> **1단계 = 디자인만 옮긴 상태.** 화면의 글자·숫자·목록은 시안의 샘플 값이다. 실제 데이터 연결(2단계)은 각 담당 이슈에서 한다.

## 1. 요약

| 항목 | 결과 |
|---|---|
| 변환한 화면 | **49개** 템플릿 + 공통 레이아웃 1개(`common/layout.html`) |
| 변환하지 않은 화면 | 26개 (변형 화면 통합·시안 모아보기 15 · 팀 결정 제외 6 · P2 4 · 개발용 1) — §4 |
| CSS | 시안 스타일을 원본 순서 그대로 22개 파일로 추출 (`static/css/`) |
| 이미지 | 실제로 쓰는 54개만 복사 (`static/images/`, 5.8MB) |
| 화면 비교 | 원본 시안과 변환본을 같은 크기로 캡처해 비교 — 달라진 픽셀 0.4~1.7% (삭제한 시연 도구·제외 메뉴·알림 숫자 차이) |
| 빌드 확인 | 여기서는 Spring을 실행할 수 없어 Thymeleaf 렌더링은 확인 못 함 → IntelliJ에서 서버 실행 후 확인 필요 |

## 2. 어떻게 바꿨나

1. 시안 파일(`.dc.html`)은 디자인 툴 데이터라 브라우저에서 React가 실행돼야 화면이 그려진다. 75개 화면을 브라우저로 그린 뒤 **완성된 HTML**을 뽑았다.
2. 시안 스타일(`<style>` 블록)을 파일로 빼고, 각 화면 `<head>`에 원래 순서대로 연결했다(순서가 바뀌면 디자인이 달라지므로).
3. 이미지 경로 `../_assets/…` → `th:src="@{/images/…}"`, CSS 안의 경로 → `../images/…`.
4. 화면 간 링크 `../1_learner/U-S01.dc.html` → `th:href="@{/hobbies}"` (API 명세서 URL). id가 필요한 링크는 시드 데이터 기준 예시값(`/courses/2` 등).
5. 시안 CSS가 링크 주소로 버튼을 꾸민 부분(`a[href="…C-03.dc.html"]`)은 실제 URL(`a[href$="/signup"]`)로 바꿨다.
6. 모든 화면에 반복되는 **머리글·강사/관리자 사이드 메뉴·바닥글**은 `common/layout.html` 조각으로 뺐다. 학습자 사이드 메뉴는 화면마다 내용이 달라 각 화면에 그대로 뒀다.
7. 시연용 요소(시연 상태 버튼, 검토 메모, 역할 전환 도구, 역할 배지)와 디자인 툴 속성(`data-dc-*`), 시안 스크립트를 지웠다.

## 3. 화면 ↔ 템플릿 ↔ 컨트롤러

| 시안 | 템플릿 (`templates/` 아래) | 연결 (컨트롤러 · API) | 시안에서 동작하던 요소 수 |
|---|---|---|---|
| C-01 메인 홈 | `main/home.html` | MainController · CM-01 | 7 |
| C-02 로그인 | `auth/login.html` | AuthController · AU-01 | 4 |
| C-03 회원가입 | `user/signup.html` | SignupController · US-01 | 8 |
| C-04 이메일 인증 (발송 안내·결과 화면 공용) | `auth/verify-result.html` | EmailVerifyController · AU-04·AU-05 | 0 |
| C-05 아이디 찾기·비밀번호 재설정 | `auth/recovery.html` | AccountRecoveryController · AU-07 | 4 |
| C-06 접근·만료 안내 | `error/access.html` | GlobalExceptionHandler · Security (403·세션 만료 안내) | 10 |
| C-09 강사 첫 로그인 비밀번호 변경 | `auth/instructor-password.html` | InstructorSetupController · AU-13 | 2 |
| C-10 개인정보 처리방침 | `main/privacy.html` | MainController · (API 추가 필요: GET /privacy) | 0 |
| C-11 공통 UI 상태 컴포넌트 | `common/ui-states.html` | 컨트롤러 없음 — 공통 UI 상태(빈 화면·토스트 등) 참고용 | 6 |
| U-S01 전체 취미 | `hobby/list.html` | HobbyController · HB-01 | 5 |
| U-S01-A 대분류 취미 탐색 | `hobby/category.html` | HobbyController · HB-01 (대분류별 보기 — 4개 변형의 대표) | 7 |
| U-S02 세부 취미 상세 | `hobby/detail.html` | HobbyController · HB-02 (세부 취미 3개 변형 통합) | 5 |
| U-S03 수준별 강의 목록 | `course/list.html` | CourseController · CO-01 | 6 |
| U-S04 강의 상세 | `course/detail.html` | CourseController · CO-02 (강의 9개 변형 통합) | 0 |
| U-S05 수강 신청·대기 | `enrollment/apply.html` | EnrollmentController · EN-01~03 (강의 상세 신청 영역 fragment 원본) | 1 |
| U-S06 차시 영상 학습 | `learning/lesson.html` | LessonController · LE-01 | 6 |
| U-S07 최종 과제 | `review/assignment.html` | AssignmentController · AS-01 | 4 |
| U-S08 나의 학습 현황 | `learning/my-learning.html` | MyLearningController · ML-01 | 14 |
| U-S09 작품 게시판 | `work/board.html` | WorkBoardController · WB-01 | 8 |
| U-S10 작품 상세 | `work/detail.html` | WorkBoardController · WB-02 | 6 |
| U-S11 일반 작품 등록·수정 | `work/form.html` | MyWorkController · MW-01·MW-03 | 10 |
| U-S12 성장 타임라인 | `work/growth.html` | MyWorkController · MW-07 | 7 |
| U-S14 공식 피드백 | `review/result.html` | AssignmentController · AS-05 | 5 |
| U-S15 알림함 | `notification/list.html` | NotificationController · NT-01 | 12 |
| U-S16 작품 숨김·복구 사유 | `work/moderation.html` | MyWorkController · MW-08 | 0 |
| U-S17 계정 설정 | `user/account.html` | AccountController · US-05 | 6 |
| U-S18 1:1 문의 | `inquiry/list.html` | InquiryController · IQ-01·IQ-03 | 6 |
| T-S01 강사 홈·담당 분반 | `instructor/home.html` | InstructorHomeController · IN-01 | 0 |
| T-S02 담당 학습자 관리 | `instructor/students.html` | InstructorStudentController · IN-02 | 12 |
| T-S03 평가 대기·진행·완료 | `instructor/reviews.html` | InstructorReviewController · RV-01 | 9 |
| T-S04 최종 과제 평가 | `instructor/review-detail.html` | InstructorReviewController · RV-02 | 6 |
| T-S05 강사 통계 | `instructor/stats.html` | InstructorStatController · IN-04 | 1 |
| T-S06 문의·공개 댓글 | `instructor/inquiries.html` | InstructorInquiryController · II-01·II-02 | 6 |
| T-S09 강사 알림함 | `notification/instructor-list.html` | NotificationController · NT-01 (강사 알림함 — 학습자 알림함과 통합 검토) | 11 |
| T-S10 작품 게시판 (강사) | `work/board-instructor.html` | WorkBoardController · WB-01 (강사용 표시 — 학습자 게시판과 통합 검토) | 21 |
| A-S01 관리자 대시보드 | `admin/home.html` | AdminHomeController · AD-01 | 0 |
| A-S04 강사 계정·권한 | `admin/instructors.html` | AdminInstructorController · AI-01 | 13 |
| A-S05 분반 관리 | `admin/sections.html` | AdminSectionController · SE-01 | 8 |
| A-S08 공개 작품 숨김·복구 | `admin/works.html` | AdminWorkController · AW-01 | 11 |
| A-S12 대기열 조회 | `admin/waitlists.html` | AdminWaitlistController · WL-01 | 0 |
| A-S13 강사 통계 | `admin/stats.html` | AdminStatController · AD-02 | 6 |
| A-S14 작품 게시판 (관리자) | `admin/works-board.html` | AdminWorkController · AW-01 (관리자 게시판 보기) | 18 |
| A-S15 취미 탐색 (관리자) | `admin/hobbies.html` | AdminHobbyController · HB-03 | 22 |
| E-400 잘못된 요청 | `error/400.html` | GlobalExceptionHandler | 1 |
| E-403 접근 권한 없음 | `error/403.html` | GlobalExceptionHandler · Security | 1 |
| E-404 페이지 없음 | `error/404.html` | GlobalExceptionHandler | 1 |
| E-405 허용되지 않은 요청 방식 | `error/405.html` | GlobalExceptionHandler | 1 |
| E-500 서버 오류 | `error/500.html` | GlobalExceptionHandler | 1 |
| E-MODALS 오류 모달 7종 | `common/modals.html` | 컨트롤러 없음 — 오류 모달 fragment 원본 | 13 |

각 템플릿 맨 위 주석에 **원본 화면, 연결 컨트롤러, 동작하던 요소 목록**을 적어 뒀다.

## 4. 변환하지 않은 화면

| 시안 | 이유 |
|---|---|
| C-07 | 약관 확인 — 11-06 팀 결정으로 제외 |
| C-08 | 강사 약관 — 11-06 팀 결정으로 제외 |
| U-S01-baking | U-S01-A(hobby/category)와 같은 화면, 데이터만 다름 |
| U-S01-craft | hobby/category와 같은 화면(준비 중 상태 표시) |
| U-S01-photo | hobby/category와 같은 화면(비공개 — 관리자만) |
| U-S02-digital | hobby/detail로 통합 |
| U-S02-dessert | hobby/detail로 통합 |
| U-S02-list | 시안 모아보기(목차) 화면 |
| U-S04-cp-mid | course/detail로 통합 |
| U-S04-cp-adv | course/detail로 통합 |
| U-S04-dd-basic | course/detail로 통합 |
| U-S04-dd-mid | course/detail로 통합 |
| U-S04-dd-adv | course/detail로 통합 |
| U-S04-ds-basic | course/detail로 통합 |
| U-S04-ds-mid | course/detail로 통합 |
| U-S04-ds-adv | course/detail로 통합 |
| U-S04-list | 시안 모아보기(목차) 화면 |
| U-S13 | 공개 프로필 — P2 |
| T-S07 | 경고·담당 정지 — P2 |
| T-S08 | 강사 강의 등록·편집 — 11-06 팀 결정으로 제외 |
| A-S02 | 대분류 관리 — 시드 데이터(D-59) |
| A-S03 | 세부 취미 편집 — 시드 데이터(D-59) |
| A-S06 | 콘텐츠 검수 — 시드 데이터(D-59) |
| A-S07 | 영상 제공 종료 — P2 |
| A-S11 | 강사 경고·정지 — P2 |
| E-PREVIEW | 개발용 미리보기 — 기획 FR에 없음 |

## 5. 공통 레이아웃 사용법 (`common/layout.html`)

```html
<header th:replace="~{common/layout :: gnb(menu='hobby')}"></header>      <!-- menu: hobby · learning · works · lms · admin · '' -->
<aside th:replace="~{common/layout :: teacherSide(active='T-S03')}"></aside> <!-- active = 화면 ID -->
<aside th:replace="~{common/layout :: adminSide(active='A-S05')}"></aside>
<div th:replace="~{common/layout :: footer}"></div>
```

머리글은 로그인 상태로 자동 분기한다: `loginUser`가 없으면 비로그인, `role`이 USER / INSTRUCTOR / ADMIN이면 각 역할 메뉴.

**머리글이 쓰는 모델 (모든 화면 공통 — #02에서 `@ControllerAdvice` + `@ModelAttribute`로 넣는 것을 제안, API 명세서 §1-8)**

| 모델 | 용도 | 상태 |
|---|---|---|
| `loginUser` | 닉네임·역할 (`nickname`, `role`) | API 명세서에 있음 — `LoginUserDTO` 필드 확정 필요 (#02) |
| `unreadCount` | 알림 벨 숫자 | API 명세서에 있음 (#17) |
| `headerNotifications` | 알림 벨 미리보기 목록 (`id`, `title`, `body`, `readAt`, `createdAt`) | ❓ **새로 필요** — 시안에 미리보기가 있어 넣음. 이름·필드는 #17에서 확정 |

## 6. 시안과 달라진 점

| 내용 | 이유 |
|---|---|
| 머리글·사이드 메뉴에서 "내 담당 취미", "경고 내역", "대분류 관리", "세부 취미", "콘텐츠 검수" 메뉴 삭제 | 팀 결정·P2로 만들지 않는 화면 (기획 07 §8: 없는 기능을 쓸 수 있는 것처럼 보이지 않게) |
| 머리글의 역할 배지(비로그인/학습자/강사/관리자) 삭제 | 시연용 표시 |
| 로그아웃 링크 → `POST /auth/logout` 폼 | Spring Security 로그아웃 방식 |
| 알림 벨의 샘플 알림 4개 → `th:each` 한 줄 | 실제 알림 목록을 보여 주기 위해 |
| 메인 홈 머리글에 로그인 상태(닉네임·로그아웃) 추가 | 시안에는 비로그인 상태만 있음 |
| 시안의 "회원가입" 링크(약관 화면 C-07) → `/signup` | 약관 단계 제외(11-06) |
| 강사 알림함 "피드백 마감" 샘플 알림 | 샘플 알림 자체를 지웠으므로 남지 않음 (마감 임박 알림 제외 결정과 일치) |

**본문에서 막아 둔 링크 (만들지 않는 화면으로 가는 버튼) — 2단계에서 버튼째 지우기 권장**

| 템플릿 | 링크 | 개수 |
|---|---|---|
| `admin/hobbies.html` | "대기1" → A-S06 | 1 |
| `admin/hobbies.html` | "대분류 관리" → A-S02 | 1 |
| `admin/hobbies.html` | "세부 취미 추가" → A-S03 | 4 |
| `admin/hobbies.html` | "세부 취미 편집" → A-S03 | 1 |
| `admin/hobbies.html` | "이동" → A-S03 | 1 |
| `admin/hobbies.html` | "편집" → A-S02 | 8 |
| `admin/hobbies.html` | "편집" → A-S03 | 8 |
| `admin/home.html` | "경고 관리" → A-S11 | 1 |
| `admin/home.html` | "콘텐츠 (시드 데이터)조회 전용 · 검수 화면은 후속" → A-S06 | 1 |
| `admin/instructors.html` | "경고·정지" → A-S11 | 1 |
| `notification/instructor-list.html` | "담당 취미 보기" → T-S08 | 1 |
| `work/detail.html` | "작성자 프로필" → U-S13 | 1 |

지금은 `href="#"`과 안내 `title`만 넣어 두었다. 화면 배치가 깨지지 않게 하려고 자동으로 지우지 않았다.

## 7. 시안에서 동작하던 요소

시안은 React가 탭 전환·모달·필터·버튼 반응을 처리했다. 변환본에서는 **모두 멈춘 상태**이고 기본 상태 화면만 남았다. 화면별 목록은 각 템플릿 맨 위 주석에 있다.

| 종류 | 다시 만드는 방법 (배운 기술 우선) |
|---|---|
| 탭 (예: 평가 대기/진행/완료) | 탭을 링크로 바꾸고 `?tab=` 쿼리로 서버가 다시 그림 (API 명세서 RV-01 방식) |
| 필터·정렬 select | `<form method="get">`로 감싸 조회 요청 |
| 등록·수정·삭제 버튼 | `<form method="post" th:action="@{…}">` + redirect (PRG) |
| 중복 확인·좋아요·재생 보고 | `@ResponseBody` + 짧은 JS (API 명세서 US-03·WB-03·LE-02) |
| 모달·토스트 | 완료 메시지는 flash `message`로 표시, 확인 창은 `confirm()` 또는 짧은 JS 🟡 |
| 오류·빈 화면 같은 다른 상태 | 시안의 "시연 상태" 도구로만 볼 수 있던 상태라 변환본에 없음 → 필요한 화면은 시안을 보고 `th:if`로 추가 |

## 8. 다음 단계 (2단계: 데이터 연결)

1. **#02 (팀원 1):** 서버를 켜서 화면이 뜨는지 확인 → `loginUser`·`unreadCount` 공통 모델 추가 → 로그인 폼 `th:action`·입력 이름 연결
2. **각 이슈 담당자:** 자기 화면의 샘플 값을 `th:text`·`th:each`·`th:if`·`th:action`으로 바꾸고, 필요한 동작만 다시 만든다 (§7)
3. **API 명세서에 없는 주소:** `GET /privacy`(C-10 개인정보 처리방침) — 추가 여부 결정 필요
4. **통합 검토:** 강사 알림함(`notification/instructor-list`)·강사 게시판(`work/board-instructor`)·관리자 게시판(`admin/works-board`)을 학습자용 템플릿과 합칠지 결정
5. 변형 화면(대분류 4종·세부 취미 3종·강의 9종)은 템플릿 하나에 데이터만 바꿔 넣는다. 공예(준비 중)·사진·영상(비공개) 상태 표시는 시안 `U-S01-craft`·`U-S01-photo`를 참고해 `th:if`로 추가

## 9. 다시 변환하려면

시안이 바뀌면 같은 방법으로 다시 변환할 수 있다. 변환 도구(렌더링 스크립트·매핑표·변환기)는 요청하면 프로젝트에 같이 넣어 줄 수 있다. 단, **2단계 바인딩을 시작한 뒤에 다시 변환하면 바인딩이 덮어써지므로** 그 뒤에는 바뀐 화면만 손으로 옮긴다.
