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
 * [역할] 평가 상세(T-S04)의 피드백 입력 폼 — 초안 저장·수료 승인·반려가 같은 입력 칸(content) 하나를 쓴다.
 * [담당] 팀원 3 · #14   [화면] T-S04   [API] RV-05·RV-06·RV-07 (폼 이름 content)
 * [연결]
 *  - 쓰는 곳: InstructorReviewController(@Valid — 승인·반려만, 초안은 검증 없이) → ReviewService
 *  - 화면: templates/instructor/review-detail.html (모델 decisionForm, th:field="*{content}")
 * [근거] DB 피드백 칸은 하나(feedback_body). 「잘한 점·개선할 점·다음 연습」은 작성 가이드로만 보인다(API RV-06, F-09).
 *        50자 검사는 여기(@Size)와 ReviewRound.finalizeAs 두 곳에서 한다(이중 방어) — 숫자는 엔티티 상수 하나만 쓴다.
 * [기술] 🟢 Bean Validation(@NotBlank, @Size) + @Valid + BindingResult
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewDecisionDTO {

    /** ❓ 임시값: 피드백·초안 최대 글자 수 — 기획에 상한이 없어(RV-05 "길이 상한 ❓") TEXT 칸 안에서 넉넉히 정했다. #01에서 확정 필요 */
    public static final int MAX_CONTENT_LENGTH = 5000;

    @NotBlank(message = "공식 피드백 또는 반려 사유를 입력해 주세요.")
    @Size(min = ReviewRound.MIN_FEEDBACK_LENGTH, max = MAX_CONTENT_LENGTH,
            message = "피드백·반려 사유는 50자 이상 5000자 이하로 입력해 주세요.")
    private String content;
}
