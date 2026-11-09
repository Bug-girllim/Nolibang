package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.ReviewContext;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 강사 평가 목록(T-S03) 표 1행 — 학습자 실명, 강의·분반, 제출 구분, 제출·마감 시각, 남은 시간, 마감 임박·초과
 * [담당] 팀원 3 · #14   [화면] T-S03   [API] RV-01 (ReviewListDTO 항목별)
 * [연결]
 *  - 만든 곳: ReviewService.getReviewList → from(round, context, now)
 *  - 쓰는 곳: templates/instructor/reviews.html (모델 reviews)
 * [근거] 임박·초과는 ReviewRound.isDueSoon·isOverdue 한곳에서 판단한 값을 그대로 담는다.
 *        학습자 실명은 강사 화면에만 표시한다(NFR-04).
 * [기술] 🟢 DTO 정적 팩토리 from()
 */
@AllArgsConstructor
@Getter
@ToString
public class ReviewListDTO {

    private Long reviewRoundId;
    private Long sectionId;
    private String learnerName;
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    private int roundNo;
    private ReviewStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime dueAt;
    private LocalDateTime finalizedAt;
    /** 마감까지 48시간 이하 → "마감 임박" */
    private boolean dueSoon;
    /** 마감 지남 → "기한 초과" (평가 의무는 계속) */
    private boolean overdue;
    /** 남은 시간 표시 — "5일 0시간" / "초과 2일 0시간", 확정된 회차는 "-" */
    private String remainingText;

    public static ReviewListDTO from(ReviewRound round, ReviewContext context, LocalDateTime now) {
        return new ReviewListDTO(
                round.getId(),
                context.getSectionId(),
                context.getLearnerName(),
                context.getCourseTitle(),
                context.getLevelLabel(),
                context.getSectionName(),
                round.getRoundNo(),
                round.getStatus(),
                round.getSubmittedAt(),
                round.getDueAt(),
                round.getFinalizedAt(),
                round.isDueSoon(now),
                round.isOverdue(now),
                round.isPending() ? remainingText(round.getDueAt(), now) : "-");
    }

    /**
     * 마감까지 남은(지난) 시간 글자 — 목록·상세가 같은 형식을 쓰도록 한곳에 둔다.
     * 호출: from, InstructorReviewDetailDTO.from
     */
    public static String remainingText(LocalDateTime dueAt, LocalDateTime now) {
        Duration gap = Duration.between(now, dueAt);
        boolean passed = gap.isNegative();
        Duration abs = gap.abs();
        String text = abs.toDays() + "일 " + abs.toHoursPart() + "시간";
        return passed ? "초과 " + text : text;
    }

    /** 제출 구분 — 1회차는 최초 제출, 그 뒤는 반려 후 정식 재제출(마감 새로 7일) */
    public String getSubmissionLabel() {
        return submissionLabelOf(roundNo);
    }

    /** 제출 구분 글자 — 목록·상세 공용 */
    public static String submissionLabelOf(int roundNo) {
        return roundNo == 1 ? "최초 제출" : "정식 재제출 (" + roundNo + "회차)";
    }

    /** 상태 칩 글자 */
    public String getStatusLabel() {
        return statusLabelOf(status, dueSoon, overdue);
    }

    /** 상태 칩 글자 — 목록·상세 공용 */
    public static String statusLabelOf(ReviewStatus status, boolean dueSoon, boolean overdue) {
        if (overdue || dueSoon) {
            return overdue ? "기한 초과" : "마감 임박";   // 확정 전 회차만 true가 된다(ReviewRound.isPending)
        }
        return switch (status) {
            case WAITING -> "대기";
            case IN_REVIEW -> "평가 진행 중";
            case APPROVED -> "수료 승인";
            case REJECTED -> "반려";
        };
    }

    public boolean isWaiting() {
        return status == ReviewStatus.WAITING;
    }
}
