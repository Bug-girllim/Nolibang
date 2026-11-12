# 놀이방 LMS · 테스트 결과 및 트러블슈팅

2026-11-12. 팀이 남긴 실행 결과와 현재 코드의 수정 상태를 대조했다. 아래의 통과 건수와 응답은 **당시 환경에서 팀이 기록한 결과**다. 최신 커밋은 코드와 테스트 소스를 확인했으며, 이번 문서 작성 중 테스트·브라우저·시연 DB를 다시 실행하지 않았다.

## 1. 테스트 결과

| 점검 시점과 근거 | 범위 | 기록된 결과 | 확인 범위 |
| --- | --- | --- | --- |
| 11-10 · `de0c5fc` | 가입·권한, 취미·강의, 수강·대기, 영상·진도, 과제·수료, 업로드 | 수강·대기 7개, 영상·진도 9개, 과제·수료 11개 통과 및 1개 일부 통과 | 로컬 서버 HTTP 요청 및 자동 테스트. 변경된 기획 항목 일부 제외. 브라우저 플레이어 조작은 미검증 |
| 11-11 · `939c11a`를 기반으로 한 점검 브랜치 | 권한, 파일, DB 매핑, 배포 | 체크리스트 6개 통과·4개 일부 통과. Gradle 테스트 **477개 통과**로 기록 | 점검 브랜치 수정 포함. 로컬 DB·임시 업로드 폴더·prod 프로필 사용. 실제 Gmail·S3·HTTPS 시연 환경 미검증 |
| 11-12 · `8bca4ad` | 최신 수정 및 회귀 테스트 소스 대조 | 담당 강사 공백 제출, 파일 정리, 세션 만료, 탈퇴·성장 관련 코드와 테스트 확인 | 최신 커밋의 전체 테스트 통과 건수는 미확인 |

[기능 체크리스트 결과][qa] · [기준 커밋][commit]

### 대표 성공 사례

| 기능 | 당시 관찰 | 연결 테스트 |
| --- | --- | --- |
| 분반 자동 배정 | 담당 강사가 있는 분반 중 남은 자리가 가장 큰 곳에 배정. 동률이면 분반 번호순 | `EnrollmentServiceTest`, `SectionServiceTest` |
| 학습 진도 | 600초 영상에서 재생 누적 357초 → 361초로 60%를 넘어 `completed_at` 기록. 추가 재생으로 완료 시각이 바뀌지 않음 | `LessonProgressTest`, `ProgressServiceTest` |
| 과제 승인·수료 | 동시 승인 요청 4건 중 1건 성공, 3건은 409. 승인 회차 1건, 승인·수료 알림 각각 1건 | `ReviewServiceTest`, `ReviewRoundTest`, `BadgeServiceTest` |
| 담당 강사 교체 | 평가 초안과 마감 유지. 새 담당자에게 평가가 이어지고 이전 담당자 접근은 거부 | 평가 체크리스트 ⑤ |
| 로컬 영상 제공 | 3명이 보낸 구간 요청 90건 모두 206. 재시작 뒤 다시 로그인하면 영상과 저장 진도 유지 | 영상 체크리스트 ④ |

[기능 체크리스트 결과][qa]

## 2. 트러블슈팅

### 2.1 구형 DB에서 로그인·작품 조회 실패

- **증상**: 앱 기동 후 로그인이나 작품 조회에서 `Unknown column` 발생. 대표 컬럼은 `users.withdrawn_at`, `works.anonymous_like_count`다.
- **원인**: 새 Entity와 기존 DB 스키마가 맞지 않았다. `ddl-auto: none`이어서 앱이 기동할 때 기존 DB를 자동 갱신하지 않는다.
- **해결**: 최신 설치 SQL과 기존 DB용 변경 요청에 필요한 컬럼·테이블·뷰를 반영했다. 기존 설치는 DB 담당자가 현재 스키마를 확인하고 적용 범위를 결정한다.
- **검증**: PR #116 시점의 420개 중 172개 실패는 변경 기록에서 구형 테스트 DB와 연관된 것으로 보고됐다. 이후 477개 통과 기록은 다른 기준 커밋과 환경이다. 시연 DB의 적용 상태는 미확인이다.

[회원 탈퇴 컬럼 변경 요청][withdraw-column] · [개인정보 정리 DB 변경][withdraw-db] · [PR #116][perf] · [현재 스키마][schema]

### 2.2 강사 비활성화 후 기존 세션 유지

- **증상과 원인**: 새 로그인은 막혔지만 이미 로그인한 강사 세션은 유지됐다. 로그인 시점의 활성 상태 검사로는 기존 세션을 만료시키지 못했다.
- **해결**: `InstructorAccountService.changeStatus`에서 비활성화할 때 `LoginSessionService.expireAll`을 호출한다.
- **검증**: 현재 회귀 테스트는 비활성화된 강사의 기존 세션만 만료되는지 확인한다. 최신 커밋에서 해당 테스트는 재실행하지 않았다.

[초기 증상과 점검 결과][qa] · [강사 계정 회귀 테스트][instructor-test]

### 2.3 담당 강사 없는 분반의 제출 실패와 고아 이미지

- **증상**: 제출이 롤백되면서 작품·평가 행은 저장되지 않았지만 이미지 파일이 디스크에 남았다.
- **원인**: 담당 강사가 없어 제출 알림 단계에서 예외가 발생했다. 이미지 저장이 앞서 끝났고, 당시 정리 로직은 이후 단계의 DB 롤백까지 처리하지 못했다.
- **해결**: 담당 강사가 없으면 알림만 생략한다. 새 이미지 파일은 트랜잭션 롤백 시 삭제하고, 이미지 교체로 더 이상 쓰지 않는 기존 파일은 커밋 뒤 삭제한다.
- **검증**: 11-11 기록에서 `WorkServiceTest.rollbackDeletesStoredFiles`가 통과했다. 최신 코드에서 담당 공백 분기와 파일 정리 시점을 확인했다. 같은 조건의 최신 브라우저 제출은 미검증이다.

[업로드 점검 및 후속 결과][qa] · [과제 제출 서비스][assignment] · [작품 서비스][work]

### 2.4 취미 정렬값이 커지면 화면 로딩 실패

- **증상과 원인**: 정렬값 200에서 홈·관리자 취미 화면이 실패했다. MySQL 컬럼은 `INT`였는데 Entity가 `TINYINT`로 매핑해 Byte 범위를 벗어났다.
- **해결**: `Category`와 `Hobby`의 `TINYINT` 타입 강제 지정을 제거했다.
- **검증**: 11-11 점검 기록에 재현과 수정이 기록됐고, `HobbyCreateTest.sortOrderAbove127`이 회귀 조건을 검사한다.

[기능 체크리스트 결과][qa] · [취미 생성 회귀 테스트][sort-test]

### 2.5 잘못된 영상 범위 응답에 전체 파일 전송

- **증상과 원인**: 처리할 수 없는 구간 요청에 HTTP 416이 반환되면서 파일 본문도 함께 전송됐다. 기존 응답은 범위 오류를 판단하기 전에 영상 리소스를 반환했다.
- **해결**: `VideoRangeCheck`에서 범위를 먼저 검사해 416 응답을 본문 없이 종료한다. 유효한 구간은 기존 206 응답을 사용한다.
- **검증**: `VideoRangeCheckTest`가 정상 206과 본문 없는 416을 검사한다. 이 테스트의 최신 실행 결과는 미확인이다.

[범위 검사 코드][range] · [영상 범위 회귀 테스트][range-test]

### 2.6 메일 인증 테스트와 시연 링크 실패

- **증상과 원인**: 테스트 환경에 SMTP 인증이 없을 때 관련 테스트 8개가 `MailAuthenticationException`으로 실패했다. 별도로, 실제 메일을 받아도 `APP_BASE_URL`이 접근할 수 없는 주소면 인증 링크가 열리지 않는다.
- **해결**: 팀 점검에서는 테스트 SMTP로 가입·인증을 검증했다. 링크 확인은 앱의 실제 LAN 접속 주소를 `APP_BASE_URL`에 설정해 진행했다.
- **검증**: 테스트 SMTP 연결 후 관련 테스트가 통과했고, LAN 주소로 생성된 링크의 인증·로그인 및 링크 재사용 거부가 기록됐다. 실제 시연 주소와 Gmail 조합은 별도 확인이 필요하다.

[회원·배포 점검 결과][qa] · [제출용 실행 가이드][setup]

### 2.7 실행 폴더 변경 후 업로드가 보이지 않음

- **증상과 원인**: 다른 작업 폴더에서 jar를 실행하면 기존 업로드가 사라진 것처럼 보였다. 기본값 `./uploads`는 실행 폴더에 따라 달라진다.
- **해결**: `UPLOAD_DIR`에 기존 업로드가 위치한 절대 경로를 지정할 수 있게 했다.
- **검증**: 11-11 점검에서 절대 경로 설정 후 이미지·영상 정상 접근과 로컬 백업·복원 연습이 기록됐다. 시연 기기의 실제 경로·백업 일정은 미확인이다.

[기능 체크리스트 결과][qa] · [배포 운영 가이드][deploy]

### 2.8 공용 DB 환경에서 페이지 로딩 지연

- **증상**: 원격 공용 DB 연결 시 비회원 홈과 강의 상세의 응답이 느렸다.
- **원인과 수정**: PR #116과 변경 기록은 원격 DB 왕복 및 반복 조회를 개선 대상으로 다뤘다. 공통 조회와 커넥션 설정을 조정하고 같은 조건의 페이지 응답을 비교했다.
- **검증**: PR 보고에서 홈 **1,030 → 19ms**, 강의 상세 한 사례 **3,286 → 582ms**. 두 값 모두 해당 공용 DB의 비회원 화면 측정이다. 로그인 지연이나 로컬 시연 성능의 개선 수치로 사용할 수 없다.

[PR #116][perf] · [구현 변경 기록][changes]

## 3. 최신 코드에서 이어서 확인할 항목

| 영역 | 현재 근거 | 남은 확인 |
| --- | --- | --- |
| 탈퇴 | 11-11 체크리스트에 당시 작품 보존 결정이 남아 있다. 최신 `WithdrawalService`는 본인 작품·이미지 등 연관 데이터를 정리한다. `WithdrawalCleanupTest`, `WithdrawalTransactionTest`, `WithdrawalAndRejoinTest`가 삭제·익명화·롤백·30일 제한을 검사한다 | 최신 시연 환경에서 탈퇴 흐름 실행과 실제 파일 정리 확인 |
| 성장 시스템 | `GameServiceTest`에 기간별 중복 보상 제한, 장착 조건, 업적 중복 방지, 대표 배지 3칸 테스트가 있다 | 최신 실행 결과와 시연 화면 확인 |
| 이미지 유효성 | 11-11 기록은 헤더만 이미지인 손상 파일이 통과할 수 있다고 기록했다. 현재 저장 코드는 매직 바이트 검사를 사용한다 | 손상 이미지의 전체 디코딩 검증은 미확인 |
| 시연·운영 | 로컬/LAN과 임시 저장소 기준 기록 | 다른 기기 접속, 시연 주소 인증 링크, 브라우저의 영상 조작, 해당 환경에서 사용하는 저장소 확인 |

포트 충돌로 기동에 실패하면 실행 가이드의 8080 점유 항목을 확인한다. 팀원 화면 차이는 두 실행 환경의 `git rev-parse HEAD`와 실제 접속 주소부터 비교한다. 과거 화면 차이의 원인은 확인되지 않았다.

[최신 탈퇴 코드][withdraw] · [제출용 실행 가이드][setup] · [DB·정책 변경 정리](Nolibang_DB_Policy_2026-11-12.md)

[commit]: https://github.com/Bug-girllim/Nolibang/commit/8bca4ade831ce36001b9ce7e7335804d293ff63c
[perf]: https://github.com/Bug-girllim/Nolibang/pull/116
[qa]: ../진행_완/검증_파트/기능체크리스트_결과.md
[withdraw-column]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/DB_%EB%B3%80%EA%B2%BD%EC%9A%94%EC%B2%AD_%ED%9A%8C%EC%9B%90%ED%83%88%ED%87%B4_withdrawn_at.md
[withdraw-db]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/DB_%EB%B3%80%EA%B2%BD%EC%9A%94%EC%B2%AD_%ED%9A%8C%EC%9B%90%ED%83%88%ED%87%B4_%EA%B0%9C%EC%9D%B8%EC%A0%95%EB%B3%B4%EC%A0%95%EB%A6%AC.md
[schema]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/resources/sql/schema.sql
[changes]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EA%B8%B0%ED%9A%8D%EC%95%88_11.05/09-update_ing.md
[setup]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EB%86%80%EC%9D%B4%EB%B0%A9%20LMS%20%EC%A0%9C%EC%B6%9C%EC%9A%A9%20%EC%8B%A4%ED%96%89%20%EA%B0%80%EC%9D%B4%EB%93%9C.md
[deploy]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EB%B0%B0%ED%8F%AC_%EC%9A%B4%EC%98%81_%EA%B0%80%EC%9D%B4%EB%93%9C.md
[instructor-test]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/test/java/com/wanted/lmspractice/domain/user/service/InstructorAccountServiceTest.java
[assignment]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/service/AssignmentService.java
[work]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/service/WorkService.java
[sort-test]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/test/java/com/wanted/lmspractice/domain/hobby/service/HobbyCreateTest.java
[range]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/file/VideoRangeCheck.java
[range-test]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/test/java/com/wanted/lmspractice/global/file/VideoRangeCheckTest.java
[withdraw]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/service/WithdrawalService.java
