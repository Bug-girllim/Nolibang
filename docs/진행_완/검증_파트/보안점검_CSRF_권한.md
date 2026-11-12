# Spring Security CSRF·역할별 권한 점검

- 점검일: 2026-11-10 / 대상: `main` (PR #77 머지 시점, `f8a7e60`)
- 방법: `SecurityConfig` 코드 검토 + 빌드한 jar를 별도 포트(8081)·시드 DB(`wanted_lms`)로 띄워 실제 HTTP 요청으로 확인. 소스는 수정하지 않음.
- 계정(시드): 학습자 `learner`·`learner3`, 강사 `teacherA`·`teacherB`·`teacherD`(임시 비밀번호), `teacherC`(비활성), 관리자 `admin`, 미인증 `newbie`
- 조치: CSRF 활성화·세션 쿠키 SameSite=Lax 반영 (feature/enable-csrf-and-cookie). 아래 표는 조치 전 점검 결과다.
- 범례: ✅ 통과 · ❌ 문제 · ⚠️ 개선 권장

## 요약

| 구분 | 결과 |
|---|---|
| 역할별 URL 접근 제어 (비로그인·USER·INSTRUCTOR·ADMIN, 33개 URL) | ✅ 모두 설계대로 |
| 소유자 검사 (다른 사람 자원 접근) | ✅ 읽기·쓰기 모두 차단 |
| 세션 관리 (고정 공격·한 기기 로그인·상태별 로그인) | ✅ |
| **CSRF 방어** | ❌ **꺼져 있음 (`csrf.disable()`)** — 실제 공격 재현됨 |
| 쿠키·보안 헤더 | ⚠️ SameSite·Secure 미지정 |

가장 중요한 것은 **CSRF 비활성화 1건**이다. 나머지 권한 구조는 양호하다.

---

## 1. 체크리스트와 결과

### A. CSRF

| # | 확인 항목 | 결과 | 근거 |
|---|---|---|---|
| A-1 | CSRF 보호가 켜져 있는가 | ❌ | `SecurityConfig.java:81` `.csrf(csrf -> csrf.disable())` (주석: API 명세 §1-5 결정 전까지 수업 방식) |
| A-2 | 토큰 없이 외부 Origin으로 상태 변경 POST가 막히는가 | ❌ | `learner`로 `Origin: http://evil.example`·토큰 없이 `POST /my/account/nickname` → 302 성공, DB 닉네임이 실제로 바뀜. `POST /works/1/like`도 200 |
| A-3 | 로그아웃이 POST로만 되는가 | ❌ | `GET /auth/logout` → 302 후 세션 종료 확인. 외부 페이지의 `<img src="/auth/logout">`로 강제 로그아웃 가능 (주석의 "POST만 허용"은 CSRF를 끈 상태에서는 성립하지 않음) |
| A-4 | GET 요청이 상태를 바꾸지 않는가 | ⚠️ | `GET /notifications/{id}`가 읽음 처리, `GET /auth/verify?token=`이 이메일 인증 처리. 둘 다 토큰/소유자 검사가 있어 위험은 낮지만 GET 부작용 |
| A-5 | 활성화 시 깨지는 곳이 있는가 (영향 범위) | ✅ 확인만 | POST 폼 49개가 모두 `th:action` → 활성화하면 Thymeleaf가 토큰을 자동 삽입. `fetch` POST 2곳(`static/js/lesson-progress.js`, `work-like.js`)만 헤더 토큰 추가 필요. MockMvc 테스트는 0개라 영향 없음 |

**CSRF 위험도**: 닉네임 변경, 수강 취소(`/enrollments/{id}/cancel`), 작품 공개 전환, 댓글 삭제, 비밀번호 변경(`/my/account/password`, 현재 비밀번호 입력이 있으면 완화) 등 로그인 사용자의 모든 POST가 대상이다. 관리자가 외부 링크를 열면 `/admin/instructors`(강사 생성·상태 변경), `/admin/works/{id}/hide` 도 위조 가능하다.

### B. URL 권한 (역할 × URL)

요청 결과: 200=허용, 302→/error/401=비로그인 차단, 403=권한 없음.

| URL | 비로그인 | USER | INSTRUCTOR | ADMIN | 설계 일치 |
|---|---|---|---|---|---|
| `/`, `/hobbies`, `/courses` | 200 | 200 | 200 | 200 | ✅ |
| `/my/learning`, `/my/works/*`, `/enrollments`, `/lessons`, `/videos`, `/assignments`, `/inquiries` | 401 화면 | 허용 | 403 | 403 | ✅ |
| `/works`, `/notifications`, `/my/account` | 401 화면 | 200 | 200 | 200 | ✅ (공용) |
| `/instructor/**` (홈·리뷰·문의·학생·통계) | 401 화면 | 403 | 200 | 403 | ✅ |
| `/admin/**` (홈·강사·분반·대기·취미·작품·통계) | 401 화면 | 403 | 403 | 200 | ✅ |
| `/auth/instructor/password` | 401 화면 | 403 | 허용 | 403 | ✅ |
| `/actuator`, `/h2-console`, `/v3/api-docs` | 401 화면 | 404 | 404 | 404 | ✅ 노출 없음 |

| # | 확인 항목 | 결과 | 근거 |
|---|---|---|---|
| B-1 | 규칙 순서(좁은 패턴이 먼저)가 맞는가 | ✅ | `/auth/instructor/**`가 `/auth/**` permitAll보다 앞, `/my/account/**`가 `/my/**`보다 앞 |
| B-2 | 비로그인 → 로그인 필요 화면 | ✅ | 전 URL 302 `/error/401` |
| B-3 | 권한 없음 → 403 화면 | ✅ | 필터·컨트롤러 예외 모두 403 (`GlobalExceptionHandler`) |
| B-4 | 모르는 URL 기본값이 `authenticated()`인가 | ✅ | `anyRequest().authenticated()` |
| B-5 | 상태 변경 POST도 역할 제한이 적용되는가 | ✅ | USER→`/admin/*` POST 403, INSTRUCTOR→`/admin/*` 403, ADMIN→`/enrollments`·`/my/works/*`·`/instructor/reviews/*` 403, 비로그인 전부 401 |
| B-6 | 정적 리소스 외 `permitAll`이 과하지 않은가 | ✅ | `/auth/**`, `/signup/**`, `/error/**`, `/privacy`, `/hobbies/**`, `/courses/**`만 공개 |
| B-7 | 메서드 보안(`@PreAuthorize`) 사용 여부 | ✅ 해당 없음 | 사용하지 않고 URL 규칙 + Service/Policy에서 처리 (`@EnableMethodSecurity` 없음) |

### C. 소유자·담당자 검사 (IDOR)

`learner3`(id 4)가 `learner`(id 2) 자원에, `teacherB`가 `teacherA` 담당 자원에 접근.

| # | 시도 | 결과 |
|---|---|---|
| C-1 | 남의 비공개 작품 `GET /works/2` | ✅ 403 |
| C-2 | 관리자 숨김 작품 `GET /works/5` | ✅ 403 |
| C-3 | 남의 비공개·숨김 작품 파일 `/files/works/3`, `/6` | ✅ 403 |
| C-4 | 공개 작품·파일 `/works/1`, `/files/works/1` | ✅ 200 (의도) |
| C-5 | 남의 작품 수정·공개 전환·성찰 `/my/works/2/*` | ✅ 404 (존재 은닉) |
| C-6 | 남의 수강 취소 `POST /enrollments/1/cancel` | ✅ 404 |
| C-7 | 남의 문의 조회·답글 `/inquiries/1`, `POST /inquiries/1/messages` | ✅ 404, 메시지 수 변화 없음(DB 확인) |
| C-8 | 남의 과제 결과·수정, 남의 알림, 남의 수강 차시 | ✅ 404 |
| C-9 | 남의 댓글 수정·삭제 `/comments/2/*` | ✅ 403, DB 변화 없음 |
| C-10 | 남의 작품 좋아요 `POST /works/2/like` (비공개) | ✅ 403 |
| C-11 | 타 강사 담당 리뷰 조회·승인 `/instructor/reviews/1`, `/4` | ✅ 403 |
| C-12 | 타 강사 담당 문의 조회·답변 `/instructor/inquiries/1` | ✅ 403, 답변 저장 안 됨(DB 확인) |
| C-13 | 본인 자원 정상 접근(대조군) | ✅ 모두 200 |
| C-14 | 익명의 작품 파일 직접 접근 `/files/works/*` | ✅ 401 화면 (공개 작품 파일도 로그인 필요 — 설계 확인 필요) |

### D. 인증·세션

| # | 확인 항목 | 결과 | 근거 |
|---|---|---|---|
| D-1 | 로그인 시 세션 ID 교체(세션 고정 방어) | ✅ | 로그인 전 세션 없음 → 로그인 후 신규 `JSESSIONID` 발급 (Spring 기본 `changeSessionId`) |
| D-2 | 한 기기 로그인 | ✅ | 같은 계정 재로그인 → 이전 세션은 `/error/401-duplicate` |
| D-3 | 비활성 강사 로그인 차단 | ✅ | `teacherC` → `?error=disabled` |
| D-4 | 이메일 미인증 로그인 차단 | ✅ | `newbie` → `?error=unverified` |
| D-5 | 임시 비밀번호 강사 강제 변경 | ✅ | `teacherD`는 `/instructor`·`/my/account`·`/works` 등 전부 `/auth/instructor/password`로 이동, 로그아웃만 가능 |
| D-6 | 로그인 후 외부 URL로 리다이렉트 | ✅ | `redirect`·`next`·`continue` 파라미터 무시, 항상 `/`(역할별 홈) |
| D-7 | 비밀번호 BCrypt | ✅ | `BCryptPasswordEncoder` |
| D-8 | 로그인 실패 메시지로 계정 존재 노출 | ⚠️ | 없는 아이디·틀린 비번은 동일(`?error`) ✅. 단 비활성(`disabled`)·미인증(`unverified`)은 올바른 자격일 때만 구분되어 비밀번호를 아는 사람에게만 노출 — 낮은 위험 |
| D-9 | 세션 만료 시간 | ✅ | `server.servlet.session.timeout: 60m`, 만료 시 `/error/401-expired` |

### E. 쿠키·응답 헤더

| # | 확인 항목 | 결과 | 근거 |
|---|---|---|---|
| E-1 | `HttpOnly` | ✅ | `Set-Cookie: JSESSIONID=…; Path=/; HttpOnly` |
| E-2 | `SameSite` | ⚠️ | 속성 없음. 브라우저 기본(Lax)에 의존 — CSRF를 끈 지금은 이것이 사실상 유일한 완화 수단 |
| E-3 | `Secure` | ⚠️ | 없음. HTTPS 배포 시 필요 (`server.servlet.session.cookie.secure=true`) |
| E-4 | `X-Frame-Options`, `X-Content-Type-Options`, `Cache-Control` | ✅ | `DENY`, `nosniff`, `no-store` (Spring 기본) |
| E-5 | CSP, HSTS | ⚠️ | 미설정 (HSTS는 HTTPS 배포 시) |

---

## 2. 조치 권장

### 필수 (P0)

1. **CSRF 활성화** — `SecurityConfig.java:81`의 `.csrf(csrf -> csrf.disable())` 제거(기본값 사용).
   - Thymeleaf `th:action` 폼 49개는 토큰이 자동 삽입되므로 코드 변경 불필요.
   - `fetch` POST 2곳(`lesson-progress.js`, `work-like.js`)에 `X-CSRF-TOKEN` 헤더 추가. 토큰은 레이아웃 `<head>`에 `<meta name="_csrf" th:content="${_csrf.token}">`, `<meta name="_csrf_header" th:content="${_csrf.headerName}">`를 넣어 JS가 읽도록 한다.
   - 로그아웃은 자동으로 POST만 허용된다. 로그아웃 링크가 `<a href>`로 되어 있다면 폼 버튼으로 바꿔야 한다(`templates/common/layout.html` 확인).
   - 로그인 폼(`/auth/login`)도 토큰이 필요하므로 `th:action` 사용 여부를 확인한다.
   - 활성화 후 재검증: 토큰 없는 POST → 403, 정상 화면의 폼·좋아요·진도 보고 → 정상.

### 권장 (P1)

2. 세션 쿠키 `SameSite=Lax`(또는 Strict), 운영 HTTPS에서 `Secure` 지정 (`application.yaml`의 `server.servlet.session.cookie.*`).
3. GET 부작용 정리: `GET /notifications/{id}`의 읽음 처리를 POST(`/read`)로 분리하거나, 현재 방식 유지 시 팀에서 의도된 동작임을 문서화.
4. 익명 사용자의 공개 작품 파일 접근(C-14)이 의도인지 결정 — 작품 게시판이 로그인 전용이라면 일관됨.

### 선택 (P2)

5. CSP 헤더 도입(인라인 `style`·스크립트가 많아 단계적으로 적용).
6. 미인증·비활성 계정 구분 메시지 정책 재검토(D-8).
7. ✅ 인증·권한 회귀 테스트: `SecurityConfigTest`(MockMvc 87건)로 역할×URL, CSRF, 소유자 검사, 계정 상태를 고정했다.

---

## 3. 점검 범위와 한계

- 컨트롤러 40여 개 중 URL 접근 매트릭스는 대표 URL 33개, 소유자 검사는 작품·파일·수강·문의·과제·알림·댓글·리뷰에 대해 수행했다. 모든 POST 엔드포인트를 일일이 시험한 것은 아니다.
- `@RequestParam` 이름을 모르는 경우 400이 나올 수 있어, 쓰기 시도는 DB 변화 여부로 최종 판정했다.
- 파일 업로드(확장자·크기·경로 조작), XSS(Thymeleaf `th:utext` 사용 여부), SQL 인젝션(네이티브 쿼리 파라미터 바인딩), 비밀번호 재설정 토큰 만료·재사용은 이번 범위 밖이다.
- 점검에 쓴 DB는 점검 후 시드 상태로 되돌렸다(점검 중 `learner` 닉네임을 바꾼 흔적 포함).
