package com.wanted.lmspractice.domain.review.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.service.ReviewService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 강사 평가 목록(T-S03)·최종 과제 평가(T-S04) 요청을 받아 ReviewService에 넘기고, 화면 이름·redirect를 정한다(PRG)
 * [담당] 팀원 3 · #14   [화면] T-S03 instructor/reviews, T-S04 instructor/review-detail   [기획] FR-023·030·031   [API] RV-01~RV-07
 * [연결]
 *  - 호출받음: 브라우저 (SecurityConfig: /instructor/** = 강사(INSTRUCTOR)만 — 학습자·관리자는 403)
 *  - 호출함:   ReviewService, GlobalExceptionHandler(flash 대상이 아닌 오류는 공통 오류 화면)
 *  - 이동:     시작·시작 취소·초안 → 상세(/instructor/reviews/{id}), 승인·반려 → 완료 탭(/instructor/reviews?tab=DONE)
 * [기술] 🟢 @Controller·@GetMapping·@PostMapping·@PathVariable·@RequestParam·@ModelAttribute, 🟢 @Valid + BindingResult,
 *        🟢 RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러 전용 — EnrollmentController와 같은 방식)
 */
@Controller
@RequestMapping("/instructor/reviews")
@RequiredArgsConstructor
public class InstructorReviewController {

    /** 승인/반려 입력 탭 값 (상세 화면 Query `decision`) — API 명세에 없어 새로 정함 */
    private static final String DECISION_APPROVE = "APPROVE";
    private static final String DECISION_REJECT = "REJECT";
    /** 오류 뒤 돌아갈 상세 화면 id를 요청 주소에서 꺼낸다 */
    private static final Pattern DETAIL_PATH = Pattern.compile("/instructor/reviews/(\\d+)");

    private final ReviewService reviewService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /**
     * 평가 목록 — 탭(대기/진행 중/완료)·담당 분반·기한(마감 임박/기한 초과) 필터.
     * 호출: GET /instructor/reviews (API RV-01) ← 강사 사이드 메뉴, 승인·반려 후 redirect
     * @param due 기한 필터 — API 명세에 없어 새로 둔 값(SOON / OVERDUE), 템플릿의 "기한" select
     */
    @GetMapping
    public String list(@RequestParam(required = false) String tab,
                       @RequestParam(required = false) Long sectionId,
                       @RequestParam(required = false) String due,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model) {
        model.addAttribute("reviewPage",
                reviewService.getReviewPage(details.getUserId(), ReviewTab.from(tab), sectionId, emptyToNull(due)));
        return "instructor/reviews";
    }

    /**
     * 평가 상세 — 열람만으로는 잠기지 않는다. 승인·반려 입력칸은 작성 중 초안으로 채운다(오류로 돌아온 경우는 방금 입력값 유지).
     * 호출: GET /instructor/reviews/{reviewRoundId} (API RV-02) ← 목록 [조회], 처리 후 redirect
     */
    @GetMapping("/{reviewRoundId}")
    public String detail(@PathVariable Long reviewRoundId,
                         @RequestParam(required = false) String decision,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model) {
        InstructorReviewDetailDTO review = reviewService.getDetail(details.getUserId(), reviewRoundId);
        model.addAttribute("review", review);
        model.addAttribute("decision", DECISION_REJECT.equals(decision) ? DECISION_REJECT : DECISION_APPROVE);
        if (!model.containsAttribute("decisionForm")) {  // flash로 입력값이 넘어왔으면 그대로 둔다
            model.addAttribute("decisionForm", new ReviewDecisionDTO(review.getDraftBody()));
        }
        return "instructor/review-detail";
    }

    /**
     * 평가 시작(학습자 수정 잠금). 호출: POST /instructor/reviews/{id}/start (API RV-03) ← 목록·상세 [평가 시작]
     */
    @PostMapping("/{reviewRoundId}/start")
    public String start(@PathVariable Long reviewRoundId,
                        @AuthenticationPrincipal AuthDetails details,
                        RedirectAttributes redirectAttributes) {
        reviewService.start(details.getUserId(), reviewRoundId);
        redirectAttributes.addFlashAttribute("message", "평가를 시작했습니다. 학습자는 이제 과제를 수정할 수 없습니다.");
        return "redirect:/instructor/reviews/" + reviewRoundId;
    }

    /**
     * 평가 시작 취소(초안이 없을 때만). 호출: POST /instructor/reviews/{id}/cancel-start (API RV-04) ← 상세 [평가 시작 취소]
     */
    @PostMapping("/{reviewRoundId}/cancel-start")
    public String cancelStart(@PathVariable Long reviewRoundId,
                              @AuthenticationPrincipal AuthDetails details,
                              RedirectAttributes redirectAttributes) {
        reviewService.cancelStart(details.getUserId(), reviewRoundId);
        redirectAttributes.addFlashAttribute("message", "평가 시작을 취소했습니다. 평가 마감은 그대로 유지됩니다.");
        return "redirect:/instructor/reviews/" + reviewRoundId;
    }

    /**
     * 피드백 초안 저장. 호출: POST /instructor/reviews/{id}/draft (API RV-05) ← 상세 [초안 저장]
     * ❓ 요청값 이름: API는 `draft`지만, 시안이 초안·승인·반려에 입력칸 하나를 같이 쓰므로 승인·반려와 같은 `content`로 받는다(API 명세 수정 요청)
     */
    @PostMapping("/{reviewRoundId}/draft")
    public String saveDraft(@PathVariable Long reviewRoundId,
                            @RequestParam(name = "content", required = false) String draft,
                            @RequestParam(required = false) String decision,
                            @AuthenticationPrincipal AuthDetails details,
                            RedirectAttributes redirectAttributes) {
        reviewService.saveDraft(details.getUserId(), reviewRoundId, draft);
        redirectAttributes.addFlashAttribute("message", "임시 저장되었습니다.");
        return detailRedirect(reviewRoundId, decision);
    }

    /**
     * 수료 승인 확정 — 50자 미만이면 상세 화면으로 돌아가 입력값을 유지한다.
     * 호출: POST /instructor/reviews/{id}/approve (API RV-06) ← 상세 [수료 승인 확정]
     */
    @PostMapping("/{reviewRoundId}/approve")
    public String approve(@PathVariable Long reviewRoundId,
                          @Valid @ModelAttribute("decisionForm") ReviewDecisionDTO decisionForm,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal AuthDetails details,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return backWithInput(reviewRoundId, DECISION_APPROVE, decisionForm, bindingResult, redirectAttributes);
        }
        reviewService.approve(details.getUserId(), reviewRoundId, decisionForm);
        redirectAttributes.addFlashAttribute("message", "수료 승인을 확정했습니다. 학습자에게 알림이 전송되었습니다.");
        return "redirect:/instructor/reviews?tab=DONE";
    }

    /**
     * 반려 확정 — 50자 미만이면 상세 화면으로 돌아가 입력값을 유지한다.
     * 호출: POST /instructor/reviews/{id}/reject (API RV-07) ← 상세 [반려 확정]
     */
    @PostMapping("/{reviewRoundId}/reject")
    public String reject(@PathVariable Long reviewRoundId,
                         @Valid @ModelAttribute("decisionForm") ReviewDecisionDTO decisionForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthDetails details,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return backWithInput(reviewRoundId, DECISION_REJECT, decisionForm, bindingResult, redirectAttributes);
        }
        reviewService.reject(details.getUserId(), reviewRoundId, decisionForm);
        redirectAttributes.addFlashAttribute("message", "반려를 확정했습니다. 학습자에게 알림이 전송되었습니다.");
        return "redirect:/instructor/reviews?tab=DONE";
    }

    /**
     * 이 컨트롤러의 업무 오류 처리 — API ErrorCode 표에서 "flash"인 오류(409 INVALID_REVIEW_STATE·CANNOT_CANCEL_START,
     * 400 FEEDBACK_TOO_SHORT)는 상세 화면으로 돌아가 errorMessage로 안내하고 입력값을 유지한다.
     * 그 밖(404 REVIEW_NOT_FOUND, 403 NOT_ASSIGNED_INSTRUCTOR)은 공통 오류 화면.
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        HttpStatus status = e.getErrorCode().getStatus();
        Matcher matcher = DETAIL_PATH.matcher(request.getRequestURI());
        boolean flashError = status == HttpStatus.CONFLICT || status == HttpStatus.BAD_REQUEST;
        if (!flashError || !matcher.find()) {
            return globalExceptionHandler.handleBusiness(e, request);
        }
        redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        String content = request.getParameter("content");
        if (content != null) {
            redirectAttributes.addFlashAttribute("decisionForm", new ReviewDecisionDTO(content));
        }
        String decision = request.getRequestURI().endsWith("/reject") ? DECISION_REJECT : request.getParameter("decision");
        return detailRedirect(Long.valueOf(matcher.group(1)), decision);
    }

    /** 입력 검증 실패 → 상세 화면으로 돌아가며 첫 오류 문구와 입력값을 flash로 넘긴다(PRG 유지) */
    private String backWithInput(Long reviewRoundId, String decision, ReviewDecisionDTO form,
                                 BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
        redirectAttributes.addFlashAttribute("decisionForm", form);
        return detailRedirect(reviewRoundId, decision);
    }

    /** 상세 화면 주소 — 반려 탭에서 왔으면 반려 탭으로 돌아간다 */
    private String detailRedirect(Long reviewRoundId, String decision) {
        String url = "redirect:/instructor/reviews/" + reviewRoundId;
        return DECISION_REJECT.equals(decision) ? url + "?decision=" + DECISION_REJECT : url;
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
