# DB 변경 요청 — 차시 영상 경로(`lessons.video_key`) 맞추기

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-11-11 |
| 대상 | **로컬 MySQL을 쓰는 팀원 PC 각각** — 2026-11-10 `seed.sql` 변경 전에 로컬 DB를 만든 경우 (공용 DB는 확인하지 않음) |
| 관련 커밋 | `f49d32c` [chore] seed.sql 차시 영상 경로를 저장소의 실제 영상 파일로 변경 (2026-11-10) |
| 기준 파일 | `src/main/resources/sql/seed.sql` 의 `INSERT INTO lessons ...` (video_key 계산식) |
| 데이터 영향 | `lessons.video_key` 값만 바뀜 (예전 경로인 27행). 행 추가·삭제 없음, 수강·진도 기록은 그대로 |
| 소요 | SQL 1문장, 1초 이내. 여러 번 실행해도 안전 (이미 새 경로면 0행 변경) |

---

## 1. 무엇이 바뀌었나

`seed.sql` 이 만드는 차시 영상 경로가 저장소에 실제로 있는 영상 파일(`uploads/videos/…`)을 가리키도록 바뀌었습니다.

| 항목 | 이전 | 이후 |
|---|---|---|
| `lessons.video_key` 예시 | `videos/course-1/lesson-3.mp4` | `videos/drawing/색연필화/색연필화_입문_3차시_10분.mp4` |
| 실제 파일 | 없음 | `uploads/videos/{drawing,cooking}/…` 27개 (Git에 포함) |

## 2. 반영하지 않으면 생기는 문제

- 학습자 **차시 영상 학습 화면**(`/lessons/{차시}?enrollmentId=…`)에서 영상이 재생되지 않습니다.
- 영상 주소 `/videos/{차시}` 가 파일을 찾지 못해 **404** 를 돌려줍니다 (`LessonVideoController` — 파일이 없으면 NOT_FOUND).

## 3. 적용 전 확인 (이미 적용됐는지)

```sql
USE nolibang;
SELECT COUNT(*) AS old_path_count FROM lessons WHERE video_key LIKE 'videos/course-%';
```

- `0` → 이미 새 경로입니다. 아무것도 하지 않아도 됩니다.
- `0` 이 아니면 (보통 `27`) → 아래 4번을 실행합니다.

영상 파일이 있는지도 확인합니다: `uploads/videos/drawing/색연필화/`, `uploads/videos/drawing/디지털드로잉/`, `uploads/videos/cooking/디저트만들기/` 에 `.mp4` 가 9개씩 있어야 합니다 (`git pull` 하면 받아집니다).

## 4. 적용 SQL

`seed.sql` 과 같은 계산식으로, **예전 경로인 행만** 바꿉니다.

```sql
USE nolibang;

UPDATE lessons l
JOIN courses c ON c.id = l.course_id
SET l.video_key = CONCAT('videos/',
        CASE c.hobby_id WHEN 1 THEN 'drawing/색연필화/색연필화'
                        WHEN 2 THEN 'drawing/디지털드로잉/디지털드로잉'
                        WHEN 3 THEN 'cooking/디저트만들기/디저트만들기' END,
        '_', ELT(c.level, '입문', '중급', '고급'), '_', l.lesson_no, '차시_10분.mp4')
WHERE l.video_key LIKE 'videos/course-%';
```

> MySQL Workbench 에서 Safe Updates 오류(1175)가 나면: Edit → Preferences → SQL Editor → "Safe Updates" 체크 해제 후 다시 연결하거나, 실행 전에 `SET SQL_SAFE_UPDATES = 0;` 을 한 줄 넣습니다.

## 5. 적용 후 확인

```sql
SELECT COUNT(*) AS old_path_count FROM lessons WHERE video_key LIKE 'videos/course-%';   -- 0 이어야 함
SELECT id, video_key FROM lessons ORDER BY id LIMIT 3;                                     -- 새 경로로 보여야 함
```

그다음 학습자 계정(`learner` / `1234`)으로 로그인해 **나의 학습 → 수강 중인 강의 → 차시 영상** 을 열어 재생되는지 봅니다.

## 6. 되돌리기 (필요할 때만)

예전 경로는 실제 파일이 없어 되돌릴 이유는 거의 없습니다. 필요하면 `seed.sql` 변경 전 규칙(`videos/course-{강의id}/lesson-{차시번호}.mp4`)으로 돌립니다.

```sql
UPDATE lessons SET video_key = CONCAT('videos/course-', course_id, '/lesson-', lesson_no, '.mp4');
```

> 관리자·강사가 웹에서 올린 영상(`videos/uploaded/…`)을 연결해 둔 차시도 이 문장이 덮어쓰므로, 되돌릴 때는 `WHERE` 로 대상을 좁히세요.
