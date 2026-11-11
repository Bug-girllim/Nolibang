package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 최종 과제 화면(U-S07) 한 장에 필요한 값 — 강의·수강 기간·차시 완료 수·화면 상태·제출 이력·23일 경과 안내
 * [담당] 팀원 3 · #13   [화면] U-S07   [기획] FR-021·026, D-41   [API] AS-01
 * [연결]
 *  - 만든 곳: AssignmentService.getAssignmentView(of)
 *  - 쓰는 곳: AssignmentController → 모델 assignment(이 DTO), state·rounds·lateWarning(API 명세 이름 그대로 따로 넣음)
 *            → templates/review/assignment.html
 * [기술] 🟢 DTO 정적 팩토리
 * 화면 시각: DB는 UTC로 저장하고 Hibernate(jdbc.time_zone=UTC)가 서버 시간대로 바꿔 읽으므로, 여기 시각을 그대로 포맷하면 KST로 보인다.
 */
@Getter
@ToString
public class AssignmentViewDTO {

    private Long enrollmentId;
    /** 사이드 메뉴 "수강 신청·대기"(강의 상세) 링크용 */
    private Long courseId;
    /** 사이드 메뉴 "차시 영상 학습" 링크용 — 강의의 1차시. 차시가 없으면 null */
    private Long firstLessonId;
    private String courseTitle;
    private String levelLabel;
    /** 제목 칸 기본값 — 강의의 과제 제목 (AS-02 "기본값 = 강의의 과제 제목") */
    private String defaultTitle;
    private AssignmentState state;
    private long completedLessonCount;
    private int requiredLessonCount;
    /** 수강 종료 시각 (수강 확정 + 30일) */
    private LocalDateTime expiresAt;
    /** 수강 종료까지 남은 일수(올림, 0 이상) */
    private int remainingDays;
    /** 남은 기간이 평가 기한(7일)보다 짧아 "반려 시 재제출 기회가 없을 수 있습니다" 안내를 보일지 (D-41) */
    private boolean lateWarning;
    /** 평가 전 수정 폼을 보일지 — 평가 대기 + 수강 기간 안 */
    private boolean editable;
    /** 최신 회차 id(수정 폼 action·결과 화면 링크). 미제출이면 null */
    private Long latestRoundId;
    /** 결과가 확정된(승인·반려) 최신 회차 id — 공식 피드백 화면(AS-05, #15) 링크. 없으면 null */
    private Long resultRoundId;
    /** 최신 회차 평가 마감. 미제출이면 null */
    private LocalDateTime latestDueAt;
    /** 제출 이력 (최근 회차부터) */
    private List<ReviewStatusDTO> rounds;

    /** 마지막으로 제출한 작품 — 제목·설명·이미지 파일 id(올린 순서). 제출한 적이 없거나 제출물이 지워졌으면 비어 있다 */
    private String submittedTitle;
    private String submittedDescription;
    private List<Long> submittedFileIds = List.of();

    private static final long SECONDS_PER_DAY = 24L * 60 * 60;

    /**
     * 마지막 회차의 제출물(작품 버전)을 붙인다 — 화면의 「제출한 작품」과 수정·재제출 폼 기본값에 쓴다.
     * 호출: AssignmentService.getAssignmentView (ReviewQuery.findSubmission — 강사 평가 상세와 같은 조회)
     */
    public void attachSubmission(String title, String description, List<Long> fileIds) {
        this.submittedTitle = title;
        this.submittedDescription = description;
        this.submittedFileIds = fileIds == null ? List.of() : fileIds;
    }

    /** 보여 줄 제출물이 있는지 */
    public boolean hasSubmission() {
        return submittedTitle != null;
    }

    /**
     * 화면 값 조립. 호출: AssignmentService.getAssignmentView
     *
     * @param firstLessonId     강의의 1차시 id (없으면 null)
     * @param rounds            수강 건의 회차(최근 회차부터)
     * @param completedLessons  완료한 차시 수
     * @param requiredLessons   필수 차시 수 (ProgressService.REQUIRED_LESSONS)
     * @param periodOpen        제출할 수 있는 수강 기간인지 (SubmissionPolicy.isPeriodOpen)
     * @param lessonsDone       3차시 모두 완료 (ProgressService.isAllCompleted — 제출 검사와 같은 판정)
     */
    public static AssignmentViewDTO of(Enrollment enrollment, CourseBriefDTO course, String defaultTitle, Long firstLessonId,
                                       List<ReviewRound> rounds, long completedLessons, int requiredLessons,
                                       boolean periodOpen, boolean lessonsDone, LocalDateTime now) {
        ReviewRound latest = rounds.isEmpty() ? null : rounds.get(0);
        AssignmentViewDTO dto = new AssignmentViewDTO();
        dto.enrollmentId = enrollment.getId();
        dto.courseId = enrollment.getCourseId();
        dto.firstLessonId = firstLessonId;
        dto.courseTitle = course.getTitle();
        dto.levelLabel = course.getLevelLabel();
        dto.defaultTitle = defaultTitle;
        dto.state = AssignmentState.decide(latest == null ? null : latest.getStatus(), periodOpen, lessonsDone);
        dto.completedLessonCount = completedLessons;
        dto.requiredLessonCount = requiredLessons;
        dto.expiresAt = enrollment.getExpiresAt();
        dto.remainingDays = remainingDays(enrollment.getExpiresAt(), now);
        // D-41: 30일 중 23일이 지나(남은 기간 7일 이하) 평가 기한(7일)을 다 쓰면 반려된 뒤 재제출할 시간이 없을 수 있다.
        // remainingDays 는 올림한 값이라 "23일 경과"(남은 7일 이하)부터 경고하려면 <= 로 비교한다.
        dto.lateWarning = periodOpen && dto.remainingDays <= ReviewRound.REVIEW_DUE_DAYS && dto.state.canBeRejectedLater();
        dto.editable = dto.state == AssignmentState.WAITING && periodOpen;
        dto.latestRoundId = latest == null ? null : latest.getId();
        dto.latestDueAt = latest == null ? null : latest.getDueAt();
        dto.resultRoundId = (latest != null && (latest.isRejected() || latest.getStatus() == ReviewStatus.APPROVED))
                ? latest.getId() : null;
        dto.rounds = rounds.stream().map(ReviewStatusDTO::ofRound).toList();
        return dto;
    }

    /** 남은 기간 표시 (예: D-12, 마지막 날은 D-day) */
    public String getRemainingLabel() {
        return remainingDays == 0 ? "D-day" : "D-" + remainingDays;
    }

    /** 수강 종료까지 남은 일수 — 하루 단위 올림, 0 아래로 내려가지 않음 (나의 학습 현황 MyLearningService와 같은 계산) */
    private static int remainingDays(LocalDateTime expiresAt, LocalDateTime now) {
        long seconds = Duration.between(now, expiresAt).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) ((seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY);
    }
}
