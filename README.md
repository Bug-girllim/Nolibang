# 놀이방 (Nolibang) LMS

취미를 배우고, 과제를 제출하고, 작품을 나누는 학습 플랫폼입니다.
학습자가 취미를 고르고 수준별 강의(차시 3개)를 수강한 뒤 최종 과제를 제출하면, 담당 강사가 평가하고 승인되면 수료·배지를 받습니다.

- 사용자: **학습자**(가입·수강·과제·작품) / **강사**(담당 학습자 평가·문의 답변·통계) / **관리자**(강사 계정·분반·작품 관리)
- 이 저장소는 4명이 이슈 22개로 나눠 개발합니다. 진행 현황은 [Issues](../../issues)와 [Pull requests](../../pulls)에서 확인하세요.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어·빌드 | Java 21, Gradle |
| 프레임워크 | Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security, Bean Validation) |
| 화면 | Thymeleaf (+ `thymeleaf-extras-springsecurity6`) |
| DB | MySQL 8 (`utf8mb4`, 시간은 UTC로 저장) |
| 기타 | Lombok, Spring Boot DevTools, `spring-boot-starter-mail`(이메일 인증) |

## 시작하기

### 1. 준비물
- JDK 21
- MySQL 8 (로컬)

### 2. DB 만들기
`src/main/resources/application.yaml`은 DB 이름 `nolibang`, 계정 `wanted` / 비밀번호 `wanted`를 기본으로 씁니다.
MySQL에 root로 접속해서 아래를 실행하세요. 계정은 본인 환경에 맞게 바꿔도 되고, 그러면 `application.yaml`도 같이 바꿉니다.

```sql
CREATE DATABASE nolibang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'wanted'@'%' IDENTIFIED BY 'wanted';
GRANT ALL PRIVILEGES ON nolibang.* TO 'wanted'@'%';
FLUSH PRIVILEGES;
```

그다음 **순서대로** 테이블과 시드 데이터를 넣습니다. (`schema.sql`은 빈 DB용이며 재실행용이 아닙니다.)

```bash
mysql -u root -p --default-character-set=utf8mb4 nolibang < src/main/resources/sql/schema.sql
mysql -u root -p --default-character-set=utf8mb4 nolibang < src/main/resources/sql/seed.sql
```

`ddl-auto`는 `none`이라 테이블은 직접 만들어야 합니다. 테이블 구조를 바꾸면 `schema.sql`과 Entity를 같은 PR에서 함께 고칩니다.

### 3. 실행

```bash
./gradlew bootRun        # Windows: .\gradlew.bat bootRun
```

브라우저에서 http://localhost:8080 을 엽니다.

### 4. 테스트

```bash
./gradlew build          # 빌드 + 테스트
```

서비스 테스트(`@SpringBootTest`)는 로컬 MySQL의 `nolibang`에 시드가 들어 있어야 통과합니다. 테스트는 끝나면 롤백되어 DB를 바꾸지 않습니다.

## 시연 계정

| 아이디 | 비밀번호 | 역할 · 상태 |
|---|---|---|
| `learner` | `1234` | 학습자 (수강·대기·반려·작품 숨김 등 데이터 보유) |
| `learner2`, `learner3` | `1234` | 학습자 |
| `newbie` | `1234` | 학습자 · 이메일 **미인증** (로그인 불가 시연) |
| `teacherA`, `teacherB` | `1234` | 강사 |
| `teacherC` | `1234` | 강사 · **비활성** (로그인 불가 시연) |
| `teacherD` | `1234` | 강사 · 첫 로그인(임시 비밀번호 → 변경 화면으로 이동) |
| `admin` | `Admin!2026` | 관리자 |

## 이메일 인증

회원가입 후 인증 메일이 발송됩니다. SMTP를 설정하지 않으면(`spring.mail.host` 없음) 메일을 보내지 않고, **서버 로그에 `[개발용] ... 인증 링크:`로 링크를 출력**합니다. 개발·시연에서는 그 링크를 브라우저에 붙여 넣으면 됩니다.
SMTP를 쓰려면 `application.yaml`의 `spring:` 아래에 `mail` 설정을 추가합니다. 비밀번호 같은 값은 파일에 직접 쓰지 말고 환경 변수로 넣으세요.

## 프로젝트 구조

```
src/main/java/com/wanted/lmspractice
├── domain/{도메인}/{controller, service, entity, repository, dto, policy}
│     admin · auth · course · enrollment · hobby · inquiry
│     instructor · learning · notification · review · user · work
└── global/{config, controller, exception, file}   공통 설정·전역 예외·파일 저장

src/main/resources
├── templates/   Thymeleaf 화면 (폴더 = 도메인, 공통 조각은 common/layout.html)
├── static/      CSS·이미지·JS
└── sql/         schema.sql, seed.sql
uploads/seed/    시드 작품 이미지
docs/            프로젝트 문서
이슈_22개/        이슈 본문
```

## 개발 규칙

- **계층:** Controller는 요청·화면 결정만, 비즈니스 규칙은 Service·Policy·Entity에 둡니다.
- **도메인 경계:** 다른 도메인의 Repository를 직접 주입하지 않고, 그 도메인 Service의 계약 메서드로만 접근합니다.
- **예외:** `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`가 오류 화면으로 변환합니다.
- **로그인 사용자:** 컨트롤러에서 `@AuthenticationPrincipal AuthDetails`로 받습니다.
- **시간:** `LocalDateTime.now()`를 씁니다. `now(ZoneOffset.UTC)`는 한국 서버에서 9시간 어긋납니다. (자세한 이유는 `docs/CONVENTIONS.md` §9)
- **공통 파일 소유자:** `SecurityConfig`, `ErrorCode`, `build.gradle`, `application.yaml`, `common/layout.html`은 팀원 1이 소유하며, 수정이 필요하면 요청합니다.
- **브랜치·PR:** `main`은 보호하고, `feature/#이슈번호-짧은이름` 브랜치에서 작업해 PR로 올립니다. PR 제목은 `[#번호] 이슈 제목`, 병합은 Squash merge입니다.

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | 공통 규칙(상태값·도메인 간 계약·ErrorCode·URL·미확정 사항) — **가장 먼저 읽기** |
| [`docs/API_명세서.md`](docs/API_명세서.md) | 화면별 URL·요청값·응답·예외 |
| [`docs/패키지_구조_설계.md`](docs/패키지_구조_설계.md) | 패키지·클래스 설계와 계층 규칙 |
| [`docs/작업분배_GitHub이슈_PR전략.md`](docs/작업분배_GitHub이슈_PR전략.md) | 이슈 분배, 계약 메서드(C1~C15), PR 체크리스트 |
| [`docs/배운기술_정리.md`](docs/배운기술_정리.md) | 수업에서 배운 기술 정리(우선 사용) |
| [`docs/HTML_변환_결과.md`](docs/HTML_변환_결과.md) | 디자인 시안 → Thymeleaf 템플릿 변환 결과 |
