package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.RoundContext;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.Submission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 최종 과제 평가 화면(T-S04) — 학습자·강의·분반, 제출물(이미지·설명), 상태·마감, 작성 중 초안, 확정 결과, 이전 회차 이력
 * [담당] 팀원 3 · #14   [API] RV-02 모델 `review`   [기획] FR-030·031, D-37(확정 결과 정정 없음)
 * [연결] ReviewService.getDetail(from) → InstructorReviewController.detail → templates/instructor/review-detail.html
 *  - 이전 담당자가 확정한 회차의 피드백도 이력에 읽기 전용으로 남긴다(API RV-02)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewDetailDTO {

    private Long reviewRoundId;
    private String learnerName;       // 실명 — 담당 강사 화면에만(NFR-04)
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    private String assignmentTitle;
    private String submissionLabel;
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime dueAt;
    private ReviewStatus status;
    private String statusLabel;
    private boolean dueSoon;
    private boolean overdue;
    private String remainingText;
    /** 작성 중 초안 (담당이 바뀌어도 그대로 이어진다) */
    private String draftBody;
    private LocalDateTime draftUpdatedAt;
    /** 초안이 있어 시작 취소를 할 수 없는지 (RV-04) */
    private boolean hasDraft;
    /** 확정된 공식 피드백 또는 반려 사유 */
    private String feedbackBody;
    private LocalDateTime finalizedAt;
    /** 제출 작품 — 과제가 삭제됐으면 제목·설명은 null, 이미지는 빈 목록 */
    private String workTitle;
    private String workDescription;
    private List<Long> fileIds;
    /** 이 수강 건의 모든 회차 이력(제출·평가 시작·승인/반려) */
    private List<HistoryItem> history;

    /**
     * 평가 회차 + 수강 정보 + 제출물 + 같은 수강 건의 전체 회차 → 상세 화면 값.
     * 호출: ReviewService.getDetail
     */
    public static ReviewDetailDTO from(ReviewRound round, RoundContext context, Optional<Submission> submission,
                                       List<ReviewRound> allRounds, LocalDateTime now) {
        return new ReviewDetailDTO(
                round.getId(),
                context.getLearnerName(),
                context.getCourseTitle(),
                context.getLevelLabel(),
                context.getSectionName(),
                context.getAssignmentTitle(),
                ReviewListDTO.submissionLabel(round.getRoundNo()),
                round.getSubmittedAt(),
                round.getStartedAt(),
                round.getDueAt(),
                round.getStatus(),
                ReviewListDTO.statusLabel(round, now),
                round.isDueSoon(now),
                round.isOverdue(now),
                ReviewListDTO.remainingText(round, now),
                round.getDraftBody(),
                round.getDraftUpdatedAt(),
                round.hasDraft(),
                round.getFeedbackBody(),
                round.getFinalizedAt(),
                submission.map(Submission::getTitle).orElse(null),
                submission.map(Submission::getDescription).orElse(null),
                submission.map(Submission::getFileIds).orElse(List.of()),
                historyOf(allRounds));
    }

    /** 평가 마감 옆 안내 — " · 남은 1일 3시간 (임박)" / " · 초과 2일 0시간 (기한 초과)" / 확정된 건은 빈 글자 */
    public String getDueNotice() {
        if (!status.equals(ReviewStatus.WAITING) && !status.equals(ReviewStatus.IN_REVIEW)) {
            return "";
        }
        if (overdue) {
            return " · " + remainingText + " (기한 초과)";
        }
        return " · 남은 " + remainingText + (dueSoon ? " (임박)" : "");
    }

    /** 회차마다 제출 → 평가 시작 → 승인/반려(피드백 전문) 순서로 이력 줄을 만든다 */
    private static List<HistoryItem> historyOf(List<ReviewRound> rounds) {
        List<HistoryItem> items = new ArrayList<>();
        for (ReviewRound r : rounds) {
            items.add(new HistoryItem(r.getSubmittedAt(), ReviewListDTO.submissionLabel(r.getRoundNo()), null));
            if (r.getStartedAt() != null) {
                items.add(new HistoryItem(r.getStartedAt(), r.getRoundNo() + "회차 평가 시작", null));
            }
            if (r.getFinalizedAt() != null) {
                String result = r.getStatus() == ReviewStatus.APPROVED ? "수료 승인" : "반려";
                items.add(new HistoryItem(r.getFinalizedAt(), r.getRoundNo() + "회차 " + result, r.getFeedbackBody()));
            }
        }
        return items;
    }

    /** 이력 한 줄 — 시각, 내용, 확정 피드백(있을 때만, 읽기 전용) */
    @Getter
    @AllArgsConstructor
    @ToString
    public static class HistoryItem {
        private LocalDateTime at;
        private String label;
        private String feedback;
    }
}
