package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 수료 승인 공식 피드백·반려 사유 입력 (칸 하나 — DB review_rounds.feedback_body)
 * [담당] 팀원 3 · #14   [API] RV-06·RV-07 Form `content`, 상세 화면 모델 `decisionForm`
 * [연결] InstructorReviewController(approve·reject의 @Valid) → ReviewService.approve·reject → ReviewRound.approve·reject(50자 재검증)
 *  - "잘한 점 / 개선할 점 / 다음 연습"은 작성 가이드일 뿐 각 칸 필수가 아니다(F-09) — content 하나로 받는다
 * [기술] 🟢 Bean Validation @NotBlank·@Size(min = 50) + 엔티티 재검증(이중 방어)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewDecisionDTO {

    /** 공식 피드백 또는 반려 사유 — 50자 이상(DB CHECK, O-04 종결값) */
    @NotBlank(message = "피드백·반려 사유를 입력해 주세요.")
    @Size(min = ReviewRound.MIN_FEEDBACK_LENGTH, message = "피드백·반려 사유는 50자 이상이어야 합니다.")
    private String content;
}
