# #03 [기반] DB 스키마 반영: Entity·Repository·시드 데이터·계약 스텁

## 담당 팀원
팀원 2 (DB를 작성한 팀원이 리뷰)

## 작업 목적
모든 도메인이 서로의 Entity·enum을 쓴다. Entity와 시드 데이터를 한 번에 맞춰 두면 4명이 각자 기능을 바로 개발·테스트할 수 있다. 다른 팀원이 아직 구현하지 않은 Service는 **계약 스텁**으로 미리 만들어 컴파일을 막지 않게 한다.

## 담당 폴더 및 패키지
- `src/main/resources/sql/schema.sql`, `sql/seed.sql`, `uploads/seed/*.png`
- `domain/*/entity`, `domain/*/repository`
- 계약 스텁: `domain/enrollment/service`, `domain/notification/service`, `domain/work/service`, `domain/review/service`, `domain/learning/service`

## 담당 클래스 (✅ 11-06 스켈레톤에 반영 완료)
- Entity 20개: `User`, `AuthToken`, `InstructorHobbyGrant`, `Category`, `Hobby`, `Course`, `Lesson`, `CourseSection`, `Enrollment`, `WaitlistEntry`, `LessonProgress`, `ReviewRound`, `Work`, `WorkVersion`, `WorkFile`, `WorkComment`, `WorkLike`, `Notification`, `InquiryThread`, `InquiryMessage`
- 복합 기본키 클래스 3개 🟡: `InstructorHobbyGrantId`, `LessonProgressId`, `WorkLikeId` (`@IdClass`)
- enum: `Role`, `UserStatus`, `TokenPurpose`, `ExposureState`, `CourseLevel`(+`CourseLevelConverter` 🟡), `CourseStatus`, `EnrollmentStatus`, `WaitlistStatus`, `ReviewStatus`, `WorkKind`, `Visibility`, `CommentStatus`, `InquiryStatus`, `NotificationKind`, `NotificationTargetType`
- Repository: Entity별 `~Repository` (복합키 표는 ID 타입이 `~Id`)
- 계약 스텁: `SeatService`, `EnrollmentService`(계약 메서드만), `NotificationService`, `WorkService`, `BadgeService`, `ProgressService`(계약 메서드만), `ReviewQueryService`

## 클래스별 역할
- Entity: `schema.sql` 표와 칼럼 1:1. `@Enumerated(STRING)`(값은 DB CHECK와 동일), 다른 표 참조는 Long id, `created_at`·`updated_at`은 DB 기록, DB 생성 칼럼은 매핑 안 함. **비즈니스 메서드·`create()`는 각 도메인 담당자가 자기 이슈에서 추가**
- seed.sql: 대분류 4·세부 취미 9·강의 9·차시 27(영상 경로·길이는 TODO 값), 분반 11, 시연 계정(learner·learner2·learner3·newbie·teacherA~D·admin), 수강·대기·진도·과제·평가·작품·댓글·문의·알림 테스트 데이터

## 구현 범위
- 남은 일: 로컬 MySQL에 `nolibang` DB 생성 → `schema.sql` → `seed.sql` 적재 → 앱 기동 → 매핑 오류 확인
- 스키마를 바꿔야 하면 `schema.sql`과 Entity를 **같은 PR**에서 함께 고치고 `데이터베이스/` 문서도 맞춘다

## 사용 기술
🟢 JPA Entity 매핑(chap03), 🟢 enum STRING 매핑, 🟢 SQL DDL·제약조건, 🟢 시드 SQL + `ddl-auto: none`, 🟡 `@IdClass`(복합키), 🟡 `AttributeConverter`(수준 1·2·3) — 11-06 팀 결정으로 사용

## 입력 및 출력
- 입력: `schema.sql`, `seed.sql`
- 출력: 기동되는 애플리케이션, 각 Repository 조회 성공

## 클래스 간 연결
- 스텁은 각 담당자가 이후 이슈에서 실제 구현으로 교체: SeatService·EnrollmentService(#09), NotificationService(#17), WorkService(#16), BadgeService(#15), ProgressService(#12), ReviewQueryService(#14)

## 선행 이슈
#01 (작업 시작), #02 (merge 순서)

## 완료 조건
- [ ] `./gradlew build` 성공, 기동 시 JPA 매핑 오류 없음(`ddl-auto: validate`로 한 번 확인 💡 — 확인 후 `none`으로 되돌림)
- [ ] schema.sql → seed.sql 실행 후 시연 계정(learner/1234 등) 로그인 가능
- [ ] 각 Repository `findAll()`·복합키 `findById(new ~Id(...))` 조회 성공
- [ ] 계약 스텁 시그니처가 §7과 일치

## 테스트 항목
- 정상: schema·seed 재실행(빈 DB) 시 오류 없음, enum 값이 DB 값과 같아 조회 시 변환 오류 없음, 수준 1·2·3 ↔ BASIC·INTERMEDIATE·ADVANCED 변환
- 예외: 정원 31 INSERT·반려 사유 50자 미만 INSERT·마감 임박 알림(REVIEW_DUE_SOON) INSERT가 DB CHECK로 거부되는지

## 주의 사항
- Entity 파일은 **merge 후 해당 도메인 담당자가 소유**한다(예: `ReviewRound`는 팀원 3). 다른 사람의 Entity에 메서드가 필요하면 소유자에게 요청.
- enum 값은 DB CHECK와 글자까지 같아야 한다. 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
