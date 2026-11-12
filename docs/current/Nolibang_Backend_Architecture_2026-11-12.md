# 놀이방 LMS · 백엔드 아키텍처와 핵심 시퀀스

2026-11-12. 시퀀스는 주요 호출과 상태 전이를 보여 주며, 입력 검증과 화면 모델 구성은 필요한 곳에 표시했다.

## 1. 시스템 구성

```mermaid
flowchart TB
    B["브라우저 · Thymeleaf 화면 · JavaScript"]
    S["Spring Security · 세션 · CSRF"]
    C["Spring MVC Controller"]
    A["업무 Service · Policy · 트랜잭션"]
    Q["JPA Repository · 조회 Query"]
    D["MySQL · 테이블과 뷰"]
    F["로컬 파일 · 선택적 S3 호환 저장소"]
    M["SMTP · 인증 및 계정 복구"]
    T["서버 스케줄러"]
    B <-->|"HTML · 폼 · JSON · 파일"| S
    S --> C
    C --> A
    A --> Q
    Q --> D
    A --> F
    C -->|"인가 후 파일 응답"| F
    A --> M
    T --> A
```

Spring MVC가 화면과 일부 JSON·파일 엔드포인트를 제공한다. 세션과 역할 경계는 필터에서 확인하고, 수강 소유권·현재 담당 강사·작품 가시성 등은 업무 로직에서 검사한다. MySQL은 상태와 파일 키를 저장한다. 기본 파일 저장 방식은 로컬이며, S3 호환 모드에서도 강사·관리자 업로드 영상은 서버의 로컬 폴더에 저장한다. 서버 스케줄러는 수강 만료, 탈퇴 이메일 확인값 정리, 실패한 파일 삭제 재처리를 수행한다.

## 2. 수강 신청과 대기열 자동 확정

```mermaid
sequenceDiagram
    actor U as 학습자
    participant C as EnrollmentController
    participant E as EnrollmentService
    participant S as SeatService
    participant D as MySQL
    U->>C: POST /enrollments (courseId)
    C->>E: apply
    E->>D: 강의의 분반 행 잠금
    E->>E: 신청 자격 확인
    alt 기존 대기자 존재 또는 배정 가능한 자리 없음
        E->>D: WaitlistService로 대기 등록
        E-->>C: 대기 결과
    else 배정 가능
        E->>D: 수강 ACTIVE 저장
        E-->>C: 분반과 만료일
    end
    Note over E,S: 이후 취소·수료·만료로 자리 반환
    E->>S: 수강 상태 반영 후 fillVacancies
    S->>D: 분반 잠금과 FIFO 1순위 잠금
    loop 배정 가능한 자리와 대기자가 있는 동안
        S->>D: 수강 생성, 대기 CONFIRMED·수강 ID 연결
        S->>D: NotificationService로 자동 확정 알림 저장
    end
```

배정은 담당 강사가 있는 분반의 남은 자리 내림차순, 동률이면 분반 번호 오름차순이다. 신청과 자동 확정이 분반 잠금을 사용하며, 자동 확정은 수강 행을 먼저 저장한 뒤 대기 행에 연결한다. 만료 스케줄러도 자리가 반환되는 흐름을 호출한다.

## 3. 영상 접근과 진도 누적

```mermaid
sequenceDiagram
    actor U as 학습자 브라우저
    participant V as LessonVideoController
    participant C as LessonController
    participant P as ProgressService
    participant D as MySQL
    U->>V: GET /videos/{lessonId} (enrollmentId, Range)
    V->>P: 수강 소유권·강의 일치·시청 권한 확인
    P->>D: 수강과 차시 조회
    V-->>U: 로컬 MP4 200/206 또는 서명 URL 302
    U->>C: POST /lessons/{lessonId}/progress
    C->>P: streamKey·seq·playing·positionSec 전달
    P->>D: 수강·차시 확인과 진도 행 잠금
    alt 수료
        P-->>C: 기존 진도 반환
    else 유효한 수강 기간
        P->>P: 순서·재생 상태·서버 경과시간 기준으로 누적
        P->>D: 진도 반영, 60% 도달 시 완료 기록
    else 기간 종료 또는 수강 상태 불가
        P-->>C: 진도 보고 거부
    end
    C-->>U: 정상 요청에 LessonProgressDTO
```

로컬 영상에 잘못된 Range를 보내면 416을 반환한다. 수료자는 영상을 다시 볼 수 있고, 다시 보기로 진도는 갱신되지 않는다.

## 4. 최종 과제 제출부터 승인·반려까지

```mermaid
sequenceDiagram
    actor L as 학습자
    participant A as AssignmentService
    actor I as 담당 강사
    participant R as ReviewService
    participant D as DB·파일 저장
    L->>A: AssignmentController를 통해 제출
    A->>D: 수강·평가 회차 잠금 후 제출 조건 검사
    A->>D: 작품 버전·이미지와 WAITING 평가 저장
    I->>R: InstructorReviewController를 통해 평가 시작
    R->>D: 회차 잠금과 담당 확인, IN_REVIEW
    I->>R: 승인 또는 반려
    alt 승인
        R->>D: 평가 APPROVED
        R->>D: EnrollmentService로 수료·자리 반환·대기 배정
        R->>D: 승인·수료 알림 저장
    else 반려
        R->>D: 평가 REJECTED와 반려 알림 저장
        opt 수강 기간 만료
            R->>D: 수강 EXPIRED, 자리 반환
        end
    end
```

승인과 수료, 후속 자리 배정, DB 알림 저장은 같은 트랜잭션에 속한다. 평가 대기 중 수정은 기존 버전을 갱신한다. 기간 내 반려 후 재제출은 새 작품 버전과 새 평가 회차를 만든다. 제출 마감 전에 올라온 과제는 수강 기간이 지난 뒤에도 평가할 수 있다.

## 5. 회원 탈퇴와 파일 정리

```mermaid
sequenceDiagram
    actor L as 학습자
    participant C as AccountController
    participant W as WithdrawalService
    participant D as MySQL
    participant F as 파일 저장소
    L->>C: POST /my/account/withdraw
    C->>W: 학습자·동의·비밀번호 확인 흐름
    W->>D: 대기 취소와 진행 중 수강 확인
    alt 진행 중 수강 있음
        W-->>C: 탈퇴 거절, 대기 취소 롤백
    else 탈퇴 가능
        W->>D: 연관 기록 정리, 댓글·좋아요 익명 처리
        W->>D: 계정 익명화, 이메일 확인값과 만료일 저장
        W->>D: 인증 토큰 삭제
        W->>W: 기존 세션 만료 표시
        W->>D: 트랜잭션 커밋
        W->>F: 커밋 후 이미지 삭제, 실패 시 재처리
        W-->>C: 탈퇴 처리 완료
        C-->>L: 로그아웃 및 탈퇴 완료 화면 이동
    end
```

서비스는 같은 계정의 기존 세션에 만료를 표시하고, Controller는 현재 요청의 세션을 로그아웃한다. DB 롤백 시 커밋 후 이미지 삭제 작업은 실행되지 않는다.

## 코드 근거

- [SecurityConfig](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/config/SecurityConfig.java)
- [EnrollmentService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/service/EnrollmentService.java)
- [SeatService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/service/SeatService.java)
- [EnrollmentExpiryService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/enrollment/service/EnrollmentExpiryService.java)
- [LessonVideoController](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/controller/LessonVideoController.java)
- [ProgressService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/learning/service/ProgressService.java)
- [AssignmentService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/service/AssignmentService.java)
- [ReviewService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/review/service/ReviewService.java)
- [WithdrawalService](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/service/WithdrawalService.java)
- [AccountController](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/controller/AccountController.java)
- [FileDeleteRetryScheduler](https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/file/FileDeleteRetryScheduler.java)
