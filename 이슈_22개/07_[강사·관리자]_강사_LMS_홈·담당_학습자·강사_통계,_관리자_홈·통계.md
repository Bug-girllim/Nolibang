# #07 [강사·관리자] 강사 LMS 홈·담당 학습자·강사 통계, 관리자 홈·통계

## 담당 팀원
팀원 1

## 작업 목적
FR-051(P0 강사 통계), 05 §7-0(강사 담당 분반·학습자 조회), D-59(관리자 홈). 여러 도메인 데이터를 모아 보여 주는 **조회 전용 화면**이라 데이터 흐름이 완성된 후반에 진행한다.

## 담당 폴더 및 패키지
`domain/instructor/{controller,service,dto,repository}`, `domain/admin/{controller,service,dto}`, `templates/instructor`, `templates/admin`

## 담당 클래스
`InstructorHomeController`, `InstructorStudentController`, `InstructorStatController`, `InstructorDashboardService`, `InstructorStatService`, `InstructorStatQuery`, `SectionSummaryDTO`, `StudentProgressDTO`, `SectionStatDTO`, `AdminHomeController`, `AdminStatController`, `AdminDashboardService`, `AdminSummaryDTO`

## 클래스별 역할
- `InstructorStatQuery`: `@Repository` + `@PersistenceContext EntityManager`. 분반별 수강 중·수료·반려·평가 대기 수를 native SQL `GROUP BY`로 집계(D-58: 현재 숫자만). 통계 정의(HTML T-S05 하단 "통계 정의")는 ❓ #01에서 확정
- `InstructorStatService`: 강사 본인/관리자 권한 확인 후 통계 반환. A-S13에서도 재사용
- `InstructorHomeController`(T-S01): 담당 분반 목록, 활성 수강·평가 대기·임박·초과 요약
- `InstructorStudentController`(T-S02): 담당 분반 학습자 이름(실명)·차시 진행·남은 기간·과제 상태. 다른 분반 학습자 차단
- `AdminHomeController`(A-S01): 강의/차시 수, 활성 수강 합계, 평가 대기, 대기 인원, 담당 공백 분반
- `AdminStatController`(A-S13): 강사별·분반별 현재 숫자

## 구현 범위
T-S01, T-S02, T-S05, A-S01, A-S13. 관리자 메뉴에서 범위 제외된 항목(대분류·세부 취미·콘텐츠 검수)은 표시하지 않음

## 사용 기술
🟢 EntityManager native 쿼리(JPA chap03) — 남의 Repository를 수정하지 않기 위해, 🟢 MySQL GROUP BY·JOIN(04_MySQL)

## 입력 및 출력
- `InstructorStatService.getSectionStats(Long instructorId)` → `List<SectionStatDTO>`(분반명, 수강 중, 수료, 반려, 평가 대기)
- `InstructorDashboardService.getStudents(Long instructorId, Long sectionId, 상태 필터)` → `List<StudentProgressDTO>`

## 클래스 간 연결
- 진도·과제 상태는 계약 메서드 사용: `ProgressService.getLessonStatuses()`(팀원 3), `ReviewQueryService.getLatestStatus()`(팀원 3), 마감 임박·초과는 `ReviewRound` 계산 메서드(팀원 3)
- 분반·수강 정보는 `EnrollmentService`/`SectionService` 조회 메서드(팀원 2) — 필요한 메서드는 §7에 추가 요청

## 선행 이슈
#03 (작업 시작 가능, 시드 데이터로 개발), #09·#14 (실데이터 검증)

## 완료 조건
- [ ] 통계 4개 숫자가 시드 + 실제 흐름(신청·제출·승인·반려) 후 DB 상태와 일치
- [ ] 강사 A가 강사 B 분반 학습자·통계 조회 불가, 학습자는 통계 URL 403
- [ ] `InstructorStatQuery` 집계 테스트 🟡

## 테스트 항목
- 정상: 분반별 숫자, 합계, 관리자 강사별 상세
- 예외: 담당 분반 없는 강사(빈 목록), 다른 강사 sectionId를 URL로 조작, 담당 공백 분반 표시

## 주의 사항
- 집계 쿼리는 다른 팀원의 테이블 상태값에 의존하므로 **enum 값 변경 시 반드시 공유**받아야 한다.
- 학습자 실명은 담당 강사·관리자에게만 표시(NFR-04).
