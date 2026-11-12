# 놀이방 LMS 제출용 실행 가이드

GitHub `main` 브랜치를 ZIP으로 내려받아 다른 컴퓨터에서 실행하는 순서입니다.
아래 순서대로 하면 **별도로 전달받을 비밀값 없이** 로컬에서 실행됩니다.

| 순서 | 할 일 | 필수 여부 |
|---|---|---|
| 1 | 사전 설치 프로그램 | 필수 |
| 2 | 환경변수 설정 | 선택 (기본값으로 실행 가능) |
| 3 | DB 생성 및 권한 설정 | 필수 |
| 4 | 테이블 생성 | 필수 |
| 5 | 초기 데이터 삽입 | 필수 |
| 6 | 서버 실행 | 필수 |
| 7 | 계정별 로그인 확인 | 확인 |
| 8 | 이미지와 영상 확인 | 확인 |
| 9 | 자주 발생하는 실행 오류 | 참고 |

---

## 1. 사전 설치 프로그램

| 프로그램 | 버전 | 필수 | 비고 |
|---|---|---|---|
| JDK | **21** | 필수 | `build.gradle`의 Java toolchain이 21로 고정되어 있습니다. JDK 자동 다운로드 설정이 없으므로 21이 설치되어 있어야 합니다. |
| MySQL Server | **8.x** | 필수 | 팀 검증 환경: MySQL 8.0. 문자셋 `utf8mb4`, 정렬 `utf8mb4_0900_ai_ci`. |
| MySQL 클라이언트 | MySQL 8.x 동봉 `mysql` 또는 MySQL Workbench | 필수 (둘 중 하나) | SQL 파일 3개를 실행할 때 사용합니다. |
| Gradle | — | 설치 불필요 | 프로젝트에 포함된 Gradle Wrapper(`gradlew`, `gradlew.bat`)를 사용합니다. 첫 실행 때 인터넷 연결이 필요합니다(의존성 다운로드). |
| IntelliJ IDEA | — | 선택 | 명령줄 대신 IDE에서 실행할 때 사용합니다. |

확인 명령:

```bash
java -version      # "21" 이 보여야 합니다
mysql --version    # 8.x
```

---

## 2. 환경변수 설정 (선택)

**기본값만으로 실행됩니다.** 아래 3~5단계대로 `nolibang` DB와 `wanted / wanted` 계정을 만들면 환경변수를 하나도 넣지 않아도 됩니다.

- 기본값의 출처는 `src/main/resources/application.yaml`입니다.
- 이 앱은 `.env` 파일을 **자동으로 읽지 않습니다**. 루트의 `.env.example`은 어떤 값을 바꿀 수 있는지 보여 주는 참고용입니다.
- 값을 바꾸려면 셸이나 IDE 실행 설정에 환경변수로 넣으세요.

| 환경변수 | 기본값 | 언제 바꾸나 |
|---|---|---|
| `LOCAL_DB_URL` | `jdbc:mysql://localhost:3306/nolibang?...` | MySQL 포트나 DB 이름이 다를 때 |
| `LOCAL_DB_USERNAME` / `LOCAL_DB_PASSWORD` | `wanted` / `wanted` | 3단계에서 다른 계정을 만들었을 때 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | (비어 있음) | 인증 메일을 실제로 보내야 할 때만. **비밀값이므로 별도로 전달받습니다.** |
| `APP_BASE_URL` | `http://localhost:8080` | 메일 속 링크 주소를 바꿀 때 |
| `UPLOAD_DIR` | `./uploads` | 업로드 폴더를 프로젝트 밖에 둘 때 (보통 바꾸지 않음) |
| `FILE_STORAGE`, `S3_*` | `local` | 클라우드 저장소를 쓸 때만. 제출 실행에는 필요 없습니다. |
| `WITHDRAWAL_EMAIL_HASH_KEY` | 개발용 기본 키 | 운영 배포 때만 |
| `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | (없음) | `prod`·`shared` 프로필 전용입니다. **제출 실행에는 사용하지 않습니다.** |

설정 예시:

```powershell
# Windows PowerShell (현재 창에서만 유효)
$env:LOCAL_DB_PASSWORD = "내비밀번호"
```

```bash
# macOS / Linux
export LOCAL_DB_PASSWORD=내비밀번호
```

IntelliJ에서는 **Run/Debug Configurations → Environment variables**에 넣습니다.

---

## 3. DB 생성 및 권한 설정

MySQL에 **root**로 접속해 `src/main/resources/sql/setup_database.sql`을 한 번 실행합니다.

이 파일이 하는 일:
- `nolibang` DB를 만듭니다(`utf8mb4`).
- `wanted / wanted` 계정을 만들고 `nolibang`에 대한 권한을 줍니다.

모든 셸에서 동작하는 방법은 `mysql` 안에서 `SOURCE`를 쓰는 것입니다. 경로는 슬래시(`/`)로 적고, 프로젝트를 압축 해제한 실제 경로로 바꾸세요.

```text
mysql -u root -p --default-character-set=utf8mb4
mysql> SOURCE C:/Users/사용자/Downloads/Nolibang-main/src/main/resources/sql/setup_database.sql;
```

- Windows에서 `mysql` 명령을 찾지 못하면 `"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"`로 실행하세요(설치 위치에 따라 다름).
- MySQL Workbench에서는 root로 접속한 뒤 **File → Open SQL Script**로 파일을 열고 실행(⚡)합니다.

---

## 4. 테이블 생성

`wanted` 계정으로 `nolibang` DB에 접속해 `schema.sql`을 실행합니다. 이 파일은 **빈 DB용**이라 두 번 실행하면 실패합니다.

```text
mysql -u wanted -pwanted --default-character-set=utf8mb4 nolibang
mysql> SOURCE C:/.../Nolibang-main/src/main/resources/sql/schema.sql;
```

- `src/main/resources/sql/migration_*.sql` 파일은 **이미 있던 DB에 덧붙이는 변경분**입니다. 내용이 `schema.sql` 끝에 이미 들어 있으므로 새로 설치할 때는 실행하지 않습니다.

---

## 5. 초기 데이터 삽입

같은 접속에서 `seed.sql`을 실행합니다.

```text
mysql> SOURCE C:/.../Nolibang-main/src/main/resources/sql/seed.sql;
```

| 구간 | 내용 |
|---|---|
| 기본 데이터 | 관리자 1명, 대분류 4, 세부 취미 9, 강의 9, 차시 27(영상 경로 포함) |
| 시연 데이터 | 시연 계정 8명, 분반, 수강, 대기열, 진도, 작품 7개(공개 4), 평가, 댓글·좋아요, 1:1 문의, 알림 |

- **시연 직전에 적재하세요.** 수강 기간(30일)과 평가 기한(7일)이 **적재한 시각을 기준으로** 계산됩니다. 예를 들어 "평가 마감 임박" 시연 데이터는 적재 다음 날부터 기한이 지난 상태가 됩니다.
- **다시 처음 상태로 되돌리려면** root로 `DROP DATABASE nolibang;`을 실행한 뒤 3 → 4 → 5단계를 다시 합니다. 시연 중 바뀐 데이터(비밀번호 변경, 작품 등록 등)도 이 방법으로 되돌립니다.
- Windows `cmd.exe`에서는 `mysql -u wanted -pwanted --default-character-set=utf8mb4 nolibang < src\main\resources\sql\seed.sql`도 됩니다. PowerShell은 `<` 입력 리다이렉션을 지원하지 않으므로 `SOURCE`를 쓰세요.

---

## 6. 서버 실행

**프로젝트 루트 폴더**(`build.gradle`이 있는 폴더)에서 실행합니다. 업로드 폴더가 `./uploads` 상대 경로라서, 다른 폴더에서 실행하면 작품 이미지와 강의 영상을 찾지 못합니다.

```powershell
# Windows
.\gradlew.bat bootRun
```

```bash
# macOS / Linux — ZIP에는 실행 권한이 없으므로 처음 한 번 권한을 줍니다
chmod +x gradlew
./gradlew bootRun
```

- 로그에 `Started LmsPracticeApplication`이 나오면 준비된 것입니다.
- 브라우저에서 **http://localhost:8080** 을 엽니다. 포트는 기본값 8080이고, 별도 포트 설정은 없습니다.
- IntelliJ에서는 `LmsPracticeApplication`의 main을 실행합니다. 이때 작업 디렉터리가 프로젝트 루트인지 확인하세요(기본값이 루트입니다).

선택 — 테스트:

```bash
./gradlew test     # Windows: .\gradlew.bat test
```

- 테스트는 로컬 `nolibang`에 시드가 적재된 상태여야 통과합니다.
- 일부 테스트는 실제로 로그인·저장을 하므로, **테스트를 돌렸다면 시연 전에 5단계로 다시 적재**하는 것을 권장합니다.

---

## 7. 계정별 로그인 확인

로그인 화면(http://localhost:8080/auth/login)에도 같은 목록이 표시됩니다.

| 아이디 | 비밀번호 | 역할 · 초기 상태 | 확인할 것 |
|---|---|---|---|
| `admin` | `demo_Admin01` | 관리자 | 취미·강의 관리, 강사 배정, 작품 숨김 관리 |
| `learnerA` | `demo_L01` | 학습자 · 수강 이력 없음 | 비어 있는 '나의 학습', 빈자리가 있는 강의 신청 |
| `learnerB` | `demo_L02` | 학습자 · 색연필화 입문 수강 중(영상 1개 남음), 디지털 드로잉 입문 평가 대기, 디저트 만들기 중급 수료(수강 기간 경과), 공개 작품 2개 | 영상 재생·진도, 과제 제출, 수료 기록, 기간 만료 후 재시청, 내 작품 수정·삭제 |
| `learnerC` | `demo_L03` | 학습자 · 디지털 드로잉 입문(강사B 분반) 수강 중, 색연필화 입문 정원 마감으로 대기 중, 공개 작품 2개 | 대기 상태 → 관리자가 정원을 늘리면 자동 확정·알림, 내 작품과 남의 작품의 메뉴 차이 |
| `teacherA` | `demo_T01` | 강사 · 디지털 드로잉 입문 A분반(learnerB) | 담당 분반·수강생, learnerB 최종 과제 평가 |
| `teacherB` | `demo_T02` | 강사 · 같은 강의 B분반(learnerC) | 담당 분반·수강생·과제 목록 |
| `teacherC` | `demo_T03` | 비활성 강사 | 로그인이 막히고 비활성 안내가 나옴 |
| `teacherD` | `demo_T04` | 첫 로그인 전(임시 비밀번호) | 로그인 직후 비밀번호 변경 화면으로 이동 |
| `newbie` | `Newbie_01` | 이메일 미인증 | 로그인이 막히고 이메일 인증 안내가 나옴 |

참고:
- **같은 계정은 한 곳에서만 로그인됩니다.** 다른 브라우저에서 같은 계정으로 로그인하면 먼저 로그인한 쪽이 로그아웃됩니다(한 기기 로그인 정책).
- teacherD는 비밀번호를 바꾸면 `demo_T04`로 다시 로그인할 수 없습니다. 처음 상태가 필요하면 5단계로 다시 적재합니다.
- **새로 회원가입한 계정**은 이메일 인증이 필요합니다. 메일을 설정하지 않았다면 서버 로그에 `[개발용] SMTP가 설정되지 않아 메일을 보내지 않았습니다. ... 링크:`가 출력되니, 그 링크를 브라우저에 붙여 넣으세요.

---

## 8. 이미지와 영상 확인

| 항목 | 저장 위치 (프로젝트 기준 상대 경로) | 확인 방법 |
|---|---|---|
| 시연 작품 이미지 5장 | `uploads/seed/*.png` | 아무 계정으로 로그인 → **작품 게시판**에 공개 작품 4개가 그림과 함께 표시됨 |
| 강의 영상 27개 (각 10분) | `uploads/videos/{drawing\|cooking}/{취미}/…mp4` | learnerB 로그인 → **나의 학습** → 색연필화 기초 → 차시 재생 |
| 새로 올린 작품 이미지 | `uploads/works/` (실행 중 생성, Git 제외) | 작품 등록 후 상세 화면 |
| 관리자가 올린 대표 사진 | `uploads/hobby/` (실행 중 생성, Git 제외) | 관리자 취미 관리 |

- `uploads` 폴더는 정적 파일로 바로 공개되지 않고, 앱을 거쳐서만 내려갑니다.
  - 작품 이미지: `/files/works/{파일 id}` — **로그인한 계정**만 볼 수 있습니다.
  - 강의 영상: `/videos/{차시 id}` — **학습자 계정**만, 본인 수강 건으로 재생됩니다.
  - 대분류·취미 대표 사진: `/hobbies/image-files/{이름}` — 로그인 없이 볼 수 있습니다.
- DB에는 절대 경로가 아니라 `seed/…`, `videos/…` 같은 상대 키만 저장됩니다. 그래서 프로젝트를 어디에 풀어도 루트에서 실행하면 그대로 표시됩니다.

---

## 9. 자주 발생하는 실행 오류

| 증상 | 원인 | 해결 |
|---|---|---|
| `Cannot find a Java installation ... languageVersion=21` | JDK 21이 없거나 Gradle이 찾지 못함 | JDK 21 설치 후 `JAVA_HOME`을 21로 지정 |
| `Communications link failure` | MySQL 서버가 꺼져 있음 / 포트가 3306이 아님 | MySQL 서비스 시작, 포트가 다르면 `LOCAL_DB_URL` 지정 |
| `Access denied for user 'wanted'` | 3단계를 하지 않았거나 비밀번호가 다름 | `setup_database.sql`을 root로 실행, 또는 `LOCAL_DB_USERNAME/PASSWORD` 지정 |
| `Unknown database 'nolibang'` | DB가 없음 | 3단계 실행 |
| 서버는 뜨는데 화면에서 오류 / `Table ... doesn't exist` | 테이블이 없음 (`ddl-auto: none`이라 앱이 표를 만들지 않음) | 4·5단계 실행 |
| `schema.sql` 실행 중 `Table ... already exists` | 이미 적재된 DB에 다시 실행함 | `DROP DATABASE nolibang;` 후 3 → 4 → 5단계 |
| `seed.sql` 실행 중 `Duplicate entry` | 시드를 두 번 실행함 | 위와 같이 처음부터 다시 적재 |
| 로그인이 안 되고 "아이디 또는 비밀번호" 오류 | 시드를 넣지 않았거나 예전 시드 | 5단계 다시 적재. 비밀번호는 7단계 표 참고 |
| 한글이 `???`로 보임 | 적재할 때 문자셋이 utf8mb4가 아님 | `--default-character-set=utf8mb4`로 접속해 다시 적재 |
| 작품 이미지·강의 영상이 안 나옴(404) | 프로젝트 루트가 아닌 곳에서 실행함 / 압축 해제 중 파일이 빠짐 | 루트에서 실행. `uploads/seed`, `uploads/videos` 폴더에 파일이 있는지 확인 |
| 영상만 404이고 영상 파일 이름이 깨져 보임 | 압축 프로그램이 한글 파일 이름을 잘못 풂 | 다른 압축 프로그램(예: Windows 기본 탐색기, 7-Zip)으로 다시 해제 |
| `Port 8080 was already in use` | 다른 프로그램이 8080 사용 중 | 해당 프로그램 종료 후 다시 실행 |
| `./gradlew: Permission denied` (macOS/Linux) | ZIP에 실행 권한이 없음 | `chmod +x gradlew` |
| 회원가입 후 인증 메일이 오지 않음 | 메일 미설정(정상) | 서버 로그의 `[개발용] ... 링크:`를 브라우저에 붙여 넣기 |

---

## 별도 전달 항목

| 항목 | 필요 여부 |
|---|---|
| 로컬 실행용 DB 계정 | **불필요** — `setup_database.sql`이 `wanted / wanted`를 만듭니다 |
| 시연 계정 비밀번호 | **불필요** — 7단계 표, 로그인 화면, README에 공개되어 있습니다(시연 전용 값) |
| 인증 메일 실제 발송용 Gmail 계정·앱 비밀번호 (`MAIL_USERNAME`, `MAIL_PASSWORD`) | 선택 — 실제 메일 발송을 보여야 할 때만 별도 전달 |
| 팀 공용 DB 접속 정보 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | 제출 실행에는 사용하지 않음. Git에 포함하지 않습니다 |
| 클라우드 저장소 키 (`S3_*`) | 제출 실행에는 사용하지 않음 |
