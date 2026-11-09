package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.RoundContext;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강사 평가 목록(T-S03)의 한 행 — 과제·학습자 실명·강의·분반·제출 구분·제출/마감 시각·남은 시간·마감 임박/초과 경고
 * [담당] 팀원 3 · #14   [API] RV-01 `ReviewListDTO`   [기획] FR-023, NFR-04(실명은 담당 강사에게만)
 * [연결] ReviewService.getReviewPage(from) → ReviewListPageDTO.reviews → templates/instructor/reviews.html
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewListDTO {

    private Long reviewRoundId;
    private String assignmentTitle;
    private String learnerName;
    private String courseTitle;
    private Long sectionId;
    private String sectionName;
    /** 제출 구분 — 1회차 "최초 제출", 2회차부터 "정식 재제출 (n회차)" */
    private String submissionLabel;
    private LocalDateTime submittedAt;
    private LocalDateTime dueAt;
    private LocalDateTime finalizedAt;
    private ReviewStatus status;
    /** 상태 칩 글자 — 대기·진행 중·승인·반려, 대기·진행 중 마감 초과면 "기한 초과" */
    private String statusLabel;
    /** 마감까지 48시간 이하 → "마감 임박" 경고 */
    private boolean dueSoon;
    /** 마감 지남 → "기한 초과" 경고(평가 의무는 계속) */
    private boolean overdue;
    /** 남은 시간 — "5일 0시간" / "초과 2일 0시간" / 완료면 "-" */
    private String remainingText;

    /**
     * 평가 회차 + 수강 정보 → 목록 한 행. 마감 판단은 엔티티(isDueSoon·isOverdue) 한곳에서 한다.
     * 호출: ReviewService.getReviewPage
     */
    public static ReviewListDTO from(ReviewRound round, RoundContext context, LocalDateTime now) {
        return new ReviewListDTO(
                round.getId(),
                context.getAssignmentTitle(),
                context.getLearnerName(),
                context.getCourseTitle(),
                context.getSectionId(),
                context.getSectionName(),
                submissionLabel(round.getRoundNo()),
                round.getSubmittedAt(),
                round.getDueAt(),
                round.getFinalizedAt(),
                round.getStatus(),
                statusLabel(round, now),
                round.isDueSoon(now),
                round.isOverdue(now),
                remainingText(round, now));
    }

    /** 제출 구분 글자 — 상세 화면(ReviewDetailDTO)도 같은 규칙을 쓴다 */
    static String submissionLabel(int roundNo) {
        return roundNo == 1 ? "최초 제출" : "정식 재제출 (" + roundNo + "회차)";
    }

    /** 상태 칩 글자 — 상세 화면도 같은 규칙을 쓴다 */
    static String statusLabel(ReviewRound round, LocalDateTime now) {
        if (round.isOverdue(now)) {
            return "기한 초과";
        }
        return switch (round.getStatus()) {
            case WAITING -> "대기";
            case IN_REVIEW -> "평가 진행 중";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
        };
    }

    /** 마감까지 남은 시간(또는 초과 시간)을 "n일 n시간"으로 — 평가가 끝난 건은 "-" */
    static String remainingText(ReviewRound round, LocalDateTime now) {
        if (!round.isPending()) {
            return "-";
        }
        Duration gap = Duration.between(now, round.getDueAt()).abs();
        String text = gap.toDays() + "일 " + gap.toHoursPart() + "시간";
        return round.isOverdue(now) ? "초과 " + text : text;
    }
}
