# DB 변경 요청 — 회원 탈퇴(`users.withdrawn_at`) 반영

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-11-11 |
| 대상 | **로컬 MySQL을 쓰는 팀원 PC 각각** (공용 DB(Aiven)는 이미 반영됨 — 2026-11-11 확인) |
| 관련 커밋 | `720e6ad` [feat] 학습자 회원 탈퇴(WITHDRAWN) 기능 추가 (2026-11-10) |
| 기준 파일 | `src/main/resources/sql/schema.sql` 맨 끝 "회원 탈퇴(WITHDRAWN) 지원" 블록 |
| 데이터 영향 | 기존 행 삭제·변경 없음 (컬럼 1개 추가 + CHECK 제약 2개 교체·추가) |
| 소요 | SQL 2문장, 1초 이내 |

> 커밋 메시지에 나오는 `migration_withdraw.sql` 파일은 저장소에 없습니다. 이 문서의 SQL이 같은 내용입니다.

---

## 1. 무엇이 바뀌었나

`users` 표만 바뀝니다. 그 밖의 표·VIEW·제약은 최신 `schema.sql`과 같습니다(2026-11-11 로컬 DB와 표·컬럼·VIEW·CHECK 단위로 대조).

| 항목 | 이전 | 이후 |
|---|---|---|
| `users.withdrawn_at` | 없음 | `DATETIME(6) NULL` — 탈퇴 시각 (`first_login_at` 뒤) |
| `ck_users_status` | `PENDING_EMAIL`, `ACTIVE`, `INACTIVE` | 위 3개 + **`WITHDRAWN`** |
| `ck_users_withdrawn` | 없음 | `(status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)` — 탈퇴 상태일 때만 탈퇴 시각이 있다 |

## 2. 반영하지 않으면 생기는 문제

코드의 `User` 엔티티(`domain/user/entity/User.java`)가 `withdrawn_at`을 매핑하고 있어서, DB에 컬럼이 없으면 **회원(users)을 읽는 모든 기능이 SQL 오류**로 실패합니다.

```
JDBC exception executing SQL [Unknown column 'u1_0.withdrawn_at' in 'field list']
```

- `spring.jpa.hibernate.ddl-auto: none`이라 **앱은 정상적으로 뜹니다.** 그래서 문제를 바로 알아차리기 어렵고, 기능을 쓰는 순간 오류가 납니다.
- 회원을 읽는 기능은 모두 실패합니다. 예를 들면 로그인(`AuthService.loadUserByUsername`), 회원가입·닉네임 중복 확인, 마이페이지·계정 설정, 관리자 강사·회원 관리, 작성자 표시가 있는 화면 등입니다.
- 회원을 읽지 않는 비로그인 화면(메인·취미·강의 목록)은 동작합니다.
- 테스트(로컬 DB 기준)는 전체 420개 중 172개가 실패합니다. 그중 68개가 위 오류로 바로 실패하고, 나머지도 대부분 회원 조회가 앞에서 막혀 연쇄로 실패합니다.
- 컬럼만 추가하고 제약을 바꾸지 않은 경우, 회원 탈퇴(`status = 'WITHDRAWN'` 저장)가 `ck_users_status` 위반으로 실패합니다.

## 3. 적용 전 확인 (이미 적용됐는지)

```sql
-- 1이면 컬럼이 이미 있음
SELECT COUNT(*) AS has_withdrawn_at
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'withdrawn_at';

-- 1이면 새 제약이 이미 있음
SELECT COUNT(*) AS has_ck_users_withdrawn
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name = 'users' AND constraint_name = 'ck_users_withdrawn';
```

둘 다 `1`이면 이미 적용된 상태이니 4번을 건너뜁니다. 둘 다 `0`이면 4번을 실행합니다. 하나만 `1`이면 4번을 그대로 실행하지 말고 담당자와 상의해 주세요(부분 적용 상태).

## 4. 적용 SQL

사용할 DB(공용: `defaultdb`, 로컬: `nolibang`)를 선택한 뒤 실행합니다.

```sql
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL AFTER first_login_at,
    DROP CHECK ck_users_status;

ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING_EMAIL','ACTIVE','INACTIVE','WITHDRAWN')),
    ADD CONSTRAINT ck_users_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL));
```

- 기존 회원은 모두 `WITHDRAWN`이 아니고 `withdrawn_at`이 `NULL`이므로 새 제약에 걸리지 않습니다.
- MySQL 8.0.19 이상이 필요합니다(`DROP CHECK` 문법). 로컬 8.0.46, 공용 Aiven 8.x에서 사용할 수 있습니다.

## 5. 적용 후 확인

```sql
SHOW CREATE TABLE users;   -- withdrawn_at 컬럼, ck_users_status(WITHDRAWN 포함), ck_users_withdrawn 이 보이면 완료
```

앱에서는 로그인 → 마이페이지가 열리면 정상입니다. 로컬 DB라면 `./gradlew test`의 `Unknown column 'u1_0.withdrawn_at'` 오류가 사라집니다.

## 6. 되돌리기 (필요할 때만)

탈퇴한 회원(`status = 'WITHDRAWN'`)이 한 명도 없을 때만 가능합니다. 이미 탈퇴 회원이 있으면 되돌리지 마세요(이전 제약에 맞지 않아 실패합니다).

```sql
ALTER TABLE users
    DROP CHECK ck_users_withdrawn,
    DROP CHECK ck_users_status;

ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING_EMAIL','ACTIVE','INACTIVE')),
    DROP COLUMN withdrawn_at;
```

---

## 7. 함께 확인 부탁: 공용 DB 연결 수 부족

2026-11-11 오전, 공용 DB 구조를 읽기 전용으로 확인하려 했지만 `ERROR 1040 (08004): Too many connections`로 두 번 모두 거부됐습니다.
같은 날 다시 접속했을 때는 `max_connections` 46개 중 21개가 쓰이고 있었습니다. 이때 확인한 결과 **공용 DB에는 위 변경이 이미 반영돼 있습니다.** 공용 DB와 로컬 DB의 표·컬럼·CHECK·인덱스·FK를 비교했고, 차이는 이 변경뿐이었습니다.

- 앱 하나가 떠 있는 동안 커넥션 풀(Hikari)이 공용 DB 연결을 기본 10개씩 계속 잡고 있습니다. 확인 당시 한 PC의 앱이 10개를 잡고 있었습니다.
- 공용 DB의 최대 연결 수가 작으면 팀원 2~3명만 앱을 띄워도 가득 차고, 그다음 사람의 앱은 DB에 접속하지 못합니다.

요청:
1. Aiven 콘솔에서 연결 수 추이를 확인해 주세요. `max_connections`는 46개이고, 앱 하나가 10개를 잡으므로 4명이 동시에 띄우면 거의 가득 찹니다.
2. 개발용 앱의 풀 크기를 줄이는 방안을 검토해 주세요. 예: `application.yaml`에 `spring.datasource.hikari.maximum-pool-size: 3`, `minimum-idle: 1`.
