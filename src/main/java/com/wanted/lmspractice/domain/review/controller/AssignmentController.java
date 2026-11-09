package com.wanted.lmspractice.domain.review.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.review.dto.ReviewDetailDTO;
import com.wanted.lmspractice.domain.review.service.ReviewResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 최종 과제 제출·수정·재제출(U-S07), 평가 결과·공식 피드백(U-S14)
 * 담당: 팀원 3 · #13 (#15)
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 *
 * [#15 추가] AS-05 결과 화면 핸들러만 넣었다. #13의 제출·수정·재제출 핸들러(AS-01~04)는 이 아래에 이어서 추가한다(같은 파일이라 merge 충돌 시 양쪽을 모두 남긴다).
 *  - 호출함: ReviewResultService.getResult
 * [기술] 🟢 @Controller, 🟢 @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final ReviewResultService reviewResultService;

    // TODO #13: 핸들러 메서드 작성 (AS-01~04)

    /**
     * 평가 결과·공식 피드백 (API AS-05, 화면 U-S14) — 알림 REVIEW_APPROVED/REVIEW_REJECTED 클릭 시에도 이 화면으로 온다.
     * 모델: review(ReviewDetailDTO), badges(승인 시 획득 배지), canResubmit(반려 + 기간 내)
     */
    @GetMapping("/{reviewRoundId}/result")
    public String result(@AuthenticationPrincipal AuthDetails auth,
                         @PathVariable Long reviewRoundId,
                         Model model) {
        ReviewDetailDTO review = reviewResultService.getResult(auth.getUserId(), reviewRoundId);
        model.addAttribute("review", review);
        model.addAttribute("badges", review.getBadges());
        model.addAttribute("canResubmit", review.isCanResubmit());
        return "review/result";
    }
}
