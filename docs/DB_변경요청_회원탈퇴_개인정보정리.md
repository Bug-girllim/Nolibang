# DB 변경 요청 — 회원 탈퇴 개인정보 정리

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-11-11 |
| 대상 | **로컬 MySQL을 쓰는 팀원 PC 각각 + 공용 DB(Aiven)** |
| 관련 브랜치 | `fix/withdrawal-privacy` (회원 탈퇴 체크리스트 18개 항목 구현) |
| 기준 파일 | `src/main/resources/sql/migration_withdrawal_privacy.sql` (= `schema.sql` 맨 끝 "회원 탈퇴 개인정보 정리" 블록) |
| 데이터 영향 | 기존 행 삭제·변경 없음 (표 1개 추가, 컬럼 1개 NULL 허용, 컬럼 1개 추가, VIEW 1개 교체) |
| 소요 | SQL 4문장, 1초 이내 |

---

## 1. 무엇이 바뀌었나

| 항목 | 이전 | 이후 | 이유 |
|---|---|---|---|
| `withdrawn_email_holds` | 없음 | 새 표 — `email_hash CHAR(64)` PK, `withdrawn_at`, `expires_at` (= 탈퇴 + 30일, CHECK) | 이메일 **원문 대신** 재가입 제한 확인값(HMAC-SHA256)만 30×24시간 보관 (U-15 3번). `users`와 연결하는 칼럼을 일부러 두지 않았다 |
| `work_comments.author_id` | `NOT NULL` | **`NULL` 허용** | 탈퇴자 댓글을 원 계정과 끊어 익명 보존 (U-15 6번 "원 계정 ID로 연결되어서는 안 된다") |
| `works.anonymous_like_count` | 없음 | `INT UNSIGNED NOT NULL DEFAULT 0` (`published_at` 뒤) | 탈퇴자가 누른 좋아요를 계정 연결 없이 숫자로만 남김 (U-15 8번) |
| `v_public_works.like_count` | 좋아요 행 수 | 좋아요 행 수 **+ `anonymous_like_count`** | 게시판 좋아요 수·좋아요순 정렬에 익명 좋아요 포함 |

## 2. 반영하지 않으면 생기는 문제

- `Work` 엔티티가 `anonymous_like_count`를 매핑하므로 **작품을 읽는 모든 기능이 SQL 오류**(`Unknown column 'anonymous_like_count'`)로 실패한다. `ddl-auto: none`이라 앱은 뜨지만 기능을 쓰는 순간 오류가 난다.
- 가입·이메일 중복 확인이 `withdrawn_email_holds`를 읽으므로 **회원가입이 실패**한다.
- 회원 탈퇴가 댓글 익명화(`author_id = NULL`) 단계에서 실패해 전부 롤백된다.

## 3. 적용 전 확인 (이미 적용됐는지)

```sql
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'withdrawn_email_holds') AS has_holds,
  (SELECT IS_NULLABLE FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'work_comments' AND column_name = 'author_id') AS comment_author_nullable,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'works' AND column_name = 'anonymous_like_count') AS has_anon_likes;
```

`1 / YES / 1`이면 이미 적용된 상태다. `0 / NO / 0`이면 4번을 실행한다. 섞여 있으면 담당자와 상의한다(부분 적용 상태).

## 4. 적용 SQL

사용할 DB(공용: `defaultdb`, 로컬: `nolibang`)를 선택한 뒤 `migration_withdrawal_privacy.sql`을 그대로 실행한다.

```bash
mysql -u wanted -p nolibang < src/main/resources/sql/migration_withdrawal_privacy.sql
```

## 5. 앱 설정

- 새 설정 `app.withdrawal.email-hash-key` (환경변수 `WITHDRAWAL_EMAIL_HASH_KEY`). 로컬은 기본값으로 동작한다.
- 배포 서버는 긴 임의 문자열로 꼭 지정하고 **운영 중에는 바꾸지 않는다** — 키가 바뀌면 이미 저장된 확인값과 맞지 않아 30일 제한이 풀린다(`docs/배포_운영_가이드.md` 환경변수 표).

## 6. 이전 방식으로 탈퇴한 계정

이전 코드는 탈퇴 후에도 `users.email`에 원문을 30일 보관했다. 새 코드의 `WithdrawnEmailHoldScheduler`가 **서버 시작 1분 뒤와 매시간** 그런 계정을 찾아 원문을 지우고, 아직 30일 안이면 확인값을 만든다. 별도 SQL은 필요 없다.

## 7. 되돌리기

익명 댓글(`author_id IS NULL`)이 한 건도 없을 때만 `author_id`를 다시 `NOT NULL`로 바꿀 수 있다.

```sql
CREATE OR REPLACE VIEW v_public_works AS
SELECT w.id AS work_id, w.kind, w.hobby_id, w.level, v.title, w.published_at, w.created_at,
       u.nickname AS author_nickname,
       (SELECT COUNT(*) FROM work_likes l WHERE l.work_id = w.id) AS like_count
FROM works w
JOIN work_versions v ON v.id = w.display_version_id
JOIN users u ON u.id = w.author_id
WHERE w.visibility = 'PUBLIC' AND w.admin_hidden = 0 AND w.deleted_at IS NULL;
ALTER TABLE works DROP COLUMN anonymous_like_count;
ALTER TABLE work_comments MODIFY author_id BIGINT UNSIGNED NOT NULL;
DROP TABLE withdrawn_email_holds;
```
