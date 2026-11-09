package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 평가 회차 하나의 상태 — 계약 C11 "최신 평가 상태" 반환값이자, 최종 과제 화면(U-S07)의 제출 이력 한 줄
 * [담당] 팀원 3 · #14(C11) · #13(이력 필드·ofRound 추가)   [API] AS-01 모델 rounds(List&lt;ReviewStatusDTO&gt;), C11
 * [연결]
 *  - 만든 곳: AssignmentService.getAssignmentView(ofRound — 이력), ReviewQueryService.getLatestStatus(#14 — C11)
 *  - 쓰는 곳: templates/review/assignment.html(이력), MyLearningService·MyCourseDTO(#11 — reviewRoundId·status·firstSubmittedAt만 읽음)
 * [기술] 🟢 DTO 정적 팩토리
 *
 * [#11 추가] 나의 학습 현황(U-S08)이 쓰는 최소 필드(reviewRoundId·status·firstSubmittedAt)를 먼저 정의했다.
 * 규칙: 아직 과제를 내지 않았으면 status가 null이다(notSubmitted()). 최신 상태를 채우는 실제 구현은 #14의 몫이다.
 * [#13 추가] 이력 표시용 roundNo·submittedAt·dueAt·rejectReason. #11이 읽는 필드는 그대로 두었다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewStatusDTO {

    /** 평가 회차 id — 피드백 결과 화면 링크(/assignments/{reviewRoundId}/result)에 쓴다. 미제출이면 null */
    private Long reviewRoundId;
    /** 회차의 상태(평가 대기·진행·승인·반려). 아직 제출하지 않았으면 null */
    private ReviewStatus status;
    /** 최초 제출 시각(1회차 제출 시각) — C11 최신 상태에서만 채운다. 미제출이거나 이력 한 줄이면 null */
    private LocalDateTime firstSubmittedAt;
    /** 몇 번째 제출인지 (1 = 최초 제출, 2부터 재제출) */
    private Integer roundNo;
    /** 이 회차의 제출 시각 */
    private LocalDateTime submittedAt;
    /** 이 회차의 평가 마감 (제출 + 7일) */
    private LocalDateTime dueAt;
    /** 반려 사유 — 반려 확정 회차에만 채운다(승인 피드백 전문은 결과 화면 AS-05에서 본다) */
    private String rejectReason;

    /** 아직 최종 과제를 제출하지 않은 상태 */
    public static ReviewStatusDTO notSubmitted() {
        return new ReviewStatusDTO();
    }

    /**
     * 회차 한 행 → 이력 한 줄. 호출: AssignmentService.getAssignmentView
     * 반려 사유는 확정된 반려에만 넣는다 — 평가 중 초안(draft_body)은 학습자에게 보이지 않는다.
     */
    public static ReviewStatusDTO ofRound(ReviewRound round) {
        ReviewStatusDTO dto = new ReviewStatusDTO();
        dto.reviewRoundId = round.getId();
        dto.status = round.getStatus();
        dto.roundNo = round.getRoundNo();
        dto.submittedAt = round.getSubmittedAt();
        dto.dueAt = round.getDueAt();
        dto.rejectReason = round.isRejected() ? round.getFeedbackBody() : null;
        return dto;
    }

    /** 최종 과제를 한 번이라도 제출했는지 */
    public boolean isSubmitted() {
        return status != null;
    }

    /** 1회차면 "최초 제출", 그 뒤는 "n차 재제출" */
    public String getRoundLabel() {
        return (roundNo == null || roundNo == 1) ? "최초 제출" : (roundNo - 1) + "차 재제출";
    }

    /** 화면 표시용 상태 이름 */
    public String getStatusLabel() {
        if (status == null) {
            return "미제출";
        }
        return switch (status) {
            case WAITING -> "평가 대기";
            case IN_REVIEW -> "평가 진행 중";
            case APPROVED -> "수료 승인";
            case REJECTED -> "반려";
        };
    }
}
