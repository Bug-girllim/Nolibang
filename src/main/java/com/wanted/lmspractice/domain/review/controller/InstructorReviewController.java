package com.wanted.lmspractice.domain.review.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.review.dto.DueFilter;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 강사 평가 화면 — 평가 대기/진행/완료 목록(T-S03), 최종 과제 평가(T-S04)의 URL·입력·화면 이동
 * [담당] 팀원 3 · #14   [화면] T-S03, T-S04   [API] RV-01~RV-07
 * [연결]
 *  - 호출함:   ReviewService(목록·상세·시작·시작 취소·초안·승인·반려)
 *  - 화면:     templates/instructor/reviews.html, templates/instructor/review-detail.html
 *  - 권한:     /instructor/** 는 SecurityConfig가 INSTRUCTOR만 통과시킨다(학습자 URL 접근 → 403).
 *             "현재 분반 담당자" 확인은 서비스(ReviewAssignmentPolicy)가 한다. 관리자는 평가하지 않는다.
 * [근거] 승인·반려 입력 오류(50자 미만)는 같은 상세 화면을 다시 보여 주며 입력값을 유지한다(RV-06 💡).
 *        「수료 승인/반려」 탭은 ?mode=reject 링크로 서버가 다시 그린다(시안의 JS 탭 대신).
 * [기술] 🟢 @Controller, @GetMapping/@PostMapping, @Valid + BindingResult, RedirectAttributes flash, @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/instructor/reviews")
@RequiredArgsConstructor
public class InstructorReviewController {

    private static final String MODE_REJECT = "reject";

    private final ReviewService reviewService;

    /**
     * 평가 목록 (RV-01) — 탭(기본 WAITING), 분반 필터, 기한 필터(due=SOON/OVERDUE ❓ 명세 추가 필요)
     * 모델: page(ReviewListPageDTO — 행·탭 건수·분반 선택지), reviews(행 목록), tab, sectionId, due
     */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails user,
                       @RequestParam(defaultValue = "WAITING") ReviewTab tab,
                       @RequestParam(required = false) Long sectionId,
                       @RequestParam(required = false) DueFilter due,
                       Model model) {
        ReviewListPageDTO page = reviewService.getReviewList(user.getUserId(), tab, sectionId, due);
        model.addAttribute("page", page);
        model.addAttribute("reviews", page.getReviews());
        model.addAttribute("tab", tab);
        model.addAttribute("sectionId", sectionId);
        model.addAttribute("due", due);
        return "instructor/reviews";
    }

    /**
     * 평가 상세 (RV-02) — 열람만으로는 잠기지 않는다. 입력 칸에는 저장된 초안을 채워 둔다.
     * 모델: review(InstructorReviewDetailDTO), decisionForm(ReviewDecisionDTO), mode(approve/reject)
     */
    @GetMapping("/{reviewRoundId}")
    public String detail(@AuthenticationPrincipal AuthDetails user,
                         @PathVariable Long reviewRoundId,
                         @RequestParam(required = false) String mode,
                         Model model) {
        InstructorReviewDetailDTO review = reviewService.getReviewDetail(user.getUserId(), reviewRoundId);
        if (!model.containsAttribute("decisionForm")) {
            model.addAttribute("decisionForm", new ReviewDecisionDTO(review.getDraftBody()));
        }
        return showDetail(review, mode, model);
    }

    /** 평가 시작 (RV-03) — 학습자 수정 잠금 */
    @PostMapping("/{reviewRoundId}/start")
    public String start(@AuthenticationPrincipal AuthDetails user, @PathVariable Long reviewRoundId,
                        RedirectAttributes redirectAttributes) {
        reviewService.start(user.getUserId(), reviewRoundId);
        redirectAttributes.addFlashAttribute("message", "평가를 시작했습니다. 학습자는 이제 과제를 수정할 수 없습니다.");
        return "redirect:/instructor/reviews/" + reviewRoundId;
    }

    /** 평가 시작 취소 (RV-04) — 초안이 비어 있을 때만, 마감 유지 */
    @PostMapping("/{reviewRoundId}/cancel-start")
    public String cancelStart(@AuthenticationPrincipal AuthDetails user, @PathVariable Long reviewRoundId,
                              RedirectAttributes redirectAttributes) {
        reviewService.cancelStart(user.getUserId(), reviewRoundId);
        redirectAttributes.addFlashAttribute("message", "평가 시작을 취소했습니다. 평가 마감은 그대로입니다.");
        return "redirect:/instructor/reviews/" + reviewRoundId;
    }

    /**
     * 피드백 초안 저장 (RV-05) — 승인·반려와 같은 입력 칸을 쓰므로 폼 이름은 content다(❓ 명세는 draft — 수정 요청).
     * 초안은 50자 미만이어도 저장된다(검증 없음).
     */
    @PostMapping("/{reviewRoundId}/draft")
    public String saveDraft(@AuthenticationPrincipal AuthDetails user, @PathVariable Long reviewRoundId,
                            @ModelAttribute("decisionForm") ReviewDecisionDTO form,
                            @RequestParam(required = false) String mode,
                            RedirectAttributes redirectAttributes) {
        reviewService.saveDraft(user.getUserId(), reviewRoundId, form.getContent());
        redirectAttributes.addFlashAttribute("message", "임시 저장되었습니다.");
        return redirectToDetail(reviewRoundId, mode);
    }

    /** 수료 승인 (RV-06) — 50자 미만이면 같은 화면에 입력값을 유지해 다시 보여 준다 */
    @PostMapping("/{reviewRoundId}/approve")
    public String approve(@AuthenticationPrincipal AuthDetails user, @PathVariable Long reviewRoundId,
                          @Valid @ModelAttribute("decisionForm") ReviewDecisionDTO form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return showDetail(reviewService.getReviewDetail(user.getUserId(), reviewRoundId), null, model);
        }
        reviewService.approve(user.getUserId(), reviewRoundId, form);
        redirectAttributes.addFlashAttribute("message", "수료 승인을 확정했습니다. 학습자에게 알림이 전송되었습니다.");
        return "redirect:/instructor/reviews?tab=DONE";
    }

    /** 반려 (RV-07) — 사유 50자 이상 */
    @PostMapping("/{reviewRoundId}/reject")
    public String reject(@AuthenticationPrincipal AuthDetails user, @PathVariable Long reviewRoundId,
                         @Valid @ModelAttribute("decisionForm") ReviewDecisionDTO form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return showDetail(reviewService.getReviewDetail(user.getUserId(), reviewRoundId), MODE_REJECT, model);
        }
        reviewService.reject(user.getUserId(), reviewRoundId, form);
        redirectAttributes.addFlashAttribute("message", "반려를 확정했습니다. 학습자에게 알림이 전송되었습니다.");
        return "redirect:/instructor/reviews?tab=DONE";
    }

    private String showDetail(InstructorReviewDetailDTO review, String mode, Model model) {
        model.addAttribute("review", review);
        model.addAttribute("mode", MODE_REJECT.equals(mode) ? MODE_REJECT : "approve");
        return "instructor/review-detail";
    }

    private String redirectToDetail(Long reviewRoundId, String mode) {
        String base = "redirect:/instructor/reviews/" + reviewRoundId;
        return MODE_REJECT.equals(mode) ? base + "?mode=" + MODE_REJECT : base;
    }
}
