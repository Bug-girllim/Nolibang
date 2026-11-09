package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.ReviewContext;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.Submission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 강사 최종 과제 평가 화면(T-S04) 값 — 학습자·강의·분반, 제출물(이미지·제목·설명·회고), 마감·임박·초과,
 *        작성 중 초안, 확정된 피드백, 같은 수강 건의 제출·평가 이력
 * [담당] 팀원 3 · #14   [화면] T-S04   [API] RV-02 (모델 review)
 * [연결]
 *  - 만든 곳: ReviewService.getReviewDetail → from(...)
 *  - 쓰는 곳: InstructorReviewController.detail → templates/instructor/review-detail.html
 * [근거] API 명세 RV-02는 모델 타입을 ReviewDetailDTO로 적었지만, 그 이름은 #15가 학습자 결과 화면(U-S14)용 생성자로 먼저 썼다.
 *        필드가 거의 겹치지 않아 #15 코드를 건드리지 않도록 강사 화면용을 따로 두었다(❓ API 명세 모델 타입 수정 요청).
 *        이전 담당 강사가 확정한 피드백은 이력에 읽기 전용으로 남는다(RV-02).
 * [기술] 🟢 DTO 정적 팩토리 from()
 */
@AllArgsConstructor
@Getter
@ToString
public class InstructorReviewDetailDTO {

    private Long reviewRoundId;
    private String learnerName;
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    private int roundNo;
    private ReviewStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime dueAt;
    private LocalDateTime startedAt;
    private LocalDateTime finalizedAt;
    private boolean dueSoon;
    private boolean overdue;
    private String remainingText;
    /** 작성 중 초안 (진행 중일 때만 의미) */
    private String draftBody;
    private LocalDateTime draftUpdatedAt;
    private boolean hasDraft;
    /** 확정된 공식 피드백 또는 반려 사유 (확정 전 null) */
    private String feedbackBody;
    /** 제출물이 삭제된 회차면 false */
    private boolean submissionPresent;
    private String workTitle;
    private String workDescription;
    private String reflection;
    /** 제출 이미지 id (올린 순서) — /files/works/{fileId} */
    private List<Long> fileIds;
    /** 같은 수강 건의 제출·평가 이력 (시간 순) */
    private List<HistoryItem> history;

    public static InstructorReviewDetailDTO from(ReviewRound round, ReviewContext context, Submission submission,
                                                 List<ReviewRound> rounds, LocalDateTime now) {
        boolean present = submission != null;
        return new InstructorReviewDetailDTO(
                round.getId(),
                context.getLearnerName(),
                context.getCourseTitle(),
                context.getLevelLabel(),
                context.getSectionName(),
                round.getRoundNo(),
                round.getStatus(),
                round.getSubmittedAt(),
                round.getDueAt(),
                round.getStartedAt(),
                round.getFinalizedAt(),
                round.isDueSoon(now),
                round.isOverdue(now),
                round.isPending() ? ReviewListDTO.remainingText(round.getDueAt(), now) : null,
                round.getDraftBody(),
                round.getDraftUpdatedAt(),
                round.hasDraft(),
                round.getFeedbackBody(),
                present,
                present ? submission.getTitle() : null,
                present ? submission.getDescription() : null,
                present ? submission.getReflection() : null,
                present ? submission.getFileIds() : List.of(),
                historyOf(rounds));
    }

    // 회차마다 제출 → 평가 시작 → 확정(피드백 원문 포함) 순서로 펼친다. 이전 담당자의 확정 피드백도 그대로 보인다
    private static List<HistoryItem> historyOf(List<ReviewRound> rounds) {
        List<HistoryItem> items = new ArrayList<>();
        for (ReviewRound r : rounds) {
            items.add(new HistoryItem(r.getSubmittedAt(), r.getRoundNo() == 1 ? "최초 제출" : r.getRoundNo() + "회차 정식 재제출", null));
            if (r.getStartedAt() != null) {
                items.add(new HistoryItem(r.getStartedAt(), "평가 시작", null));
            }
            if (r.getFinalizedAt() != null) {
                String label = r.getStatus() == ReviewStatus.APPROVED ? "수료 승인" : "반려";
                items.add(new HistoryItem(r.getFinalizedAt(), label, r.getFeedbackBody()));
            }
        }
        return items;
    }

    public String getSubmissionLabel() {
        return ReviewListDTO.submissionLabelOf(roundNo);
    }

    public String getStatusLabel() {
        return ReviewListDTO.statusLabelOf(status, dueSoon, overdue);
    }

    public boolean isWaiting() {
        return status == ReviewStatus.WAITING;
    }

    public boolean isInReview() {
        return status == ReviewStatus.IN_REVIEW;
    }

    public boolean isFinalized() {
        return status == ReviewStatus.APPROVED || status == ReviewStatus.REJECTED;
    }

    /** 이력 한 줄 */
    @AllArgsConstructor
    @Getter
    @ToString
    public static class HistoryItem {
        private LocalDateTime at;
        private String label;
        /** 확정 줄이면 피드백·반려 사유 원문, 아니면 null */
        private String body;
    }
}
