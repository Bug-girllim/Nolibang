package com.wanted.lmspractice.domain.review.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.review.dto.AssignmentSubmitDTO;
import com.wanted.lmspractice.domain.review.dto.AssignmentViewDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDetailDTO;
import com.wanted.lmspractice.domain.review.service.AssignmentService;
import com.wanted.lmspractice.domain.review.service.ReviewResultService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 최종 과제 화면(AS-01)·최초 제출(AS-02)·평가 전 수정(AS-03)·반려 후 재제출(AS-04) 요청을 받아 서비스에 넘기고,
 *        결과를 flash 안내와 함께 과제 화면으로 redirect한다(PRG). 평가 결과·공식 피드백 화면(AS-05)도 이 컨트롤러가 맡는다(#15).
 * [담당] 팀원 3 · #13 (#15)   [화면] U-S07 — templates/review/assignment.html, U-S14 — templates/review/result.html
 *       [기획] FR-021·022·026, D-41   [API] AS-01~AS-05
 * [연결]
 *  - 호출받음: 브라우저 (SecurityConfig: /assignments/** = 학습자(USER)만), 진입 링크: 나의 학습 현황·차시 화면 [최종 실습으로], 평가 알림
 *  - 호출함:   AssignmentService(#13 getAssignmentView·submit·edit·resubmit), ReviewResultService(#15 getResult),
 *             GlobalExceptionHandler(404·403·500은 공통 오류 화면)
 *  - 모델:     (AS-01) assignment(AssignmentViewDTO), state(AssignmentState), rounds(List&lt;ReviewStatusDTO&gt;), lateWarning(boolean),
 *             form(AssignmentSubmitDTO) — state·rounds·lateWarning·form은 API 명세 모델 이름 그대로
 *             (AS-05) review(ReviewDetailDTO), badges, canResubmit
 *  - 이동:     AS-02~04 성공·업무 오류(400·409) 모두 redirect:/assignments?enrollmentId={id} (+ flash message / errorMessage)
 * [기술] 🟢 @Controller·@GetMapping·@PostMapping, 🟢 @Valid + BindingResult(같은 화면 다시 그림), 🟢 List&lt;MultipartFile&gt;,
 *        🟢 RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러 전용 — EnrollmentController와 같은 방식), 🟢 @AuthenticationPrincipal
 * ❓ 50MB를 넘는 파일은 컨트롤러에 오기 전에 Spring(multipart max-file-size)이 막아 공통 500 화면이 된다
 *    — GlobalExceptionHandler에서 FILE_TOO_LARGE로 바꾸는 처리가 필요하다(팀원 1 요청).
 */
@Controller
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private static final String VIEW = "review/assignment";
    private static final String MSG_SUBMITTED = "제출되었습니다. 담당 강사의 평가를 기다려 주세요.";
    private static final String MSG_EDITED = "제출물을 수정했습니다. 평가 마감은 바뀌지 않습니다.";
    private static final String MSG_RESUBMITTED = "재제출되었습니다. 새 평가 마감은 제출 시각부터 7일입니다.";

    private final AssignmentService assignmentService;
    private final ReviewResultService reviewResultService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /**
     * 최종 과제 화면 — 상태에 따라 제출 폼·수정 폼·재제출 폼·잠금 안내·수료 안내를 보여 준다.
     * 호출: GET /assignments?enrollmentId= (API AS-01)
     */
    @GetMapping
    public String view(@RequestParam Long enrollmentId,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model) {
        AssignmentViewDTO view = assignmentService.getAssignmentView(details.getUserId(), enrollmentId);
        addViewModel(model, view, AssignmentSubmitDTO.withDefaultTitle(view.getDefaultTitle()));
        return VIEW;
    }

    /**
     * 최초 제출 — 제목·설명·이미지 1~5장. 성공하면 평가 대기(수료 아님).
     * 호출: POST /assignments (API AS-02, multipart/form-data) ← review/assignment.html 제출 폼
     */
    @PostMapping
    public String submit(@RequestParam Long enrollmentId,
                         @Valid @ModelAttribute("form") AssignmentSubmitDTO form,
                         BindingResult bindingResult,
                         @RequestParam(value = "images", required = false) List<MultipartFile> images,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return redisplay(details, enrollmentId, form, model);
        }
        assignmentService.submit(details.getUserId(), enrollmentId, form, images);
        redirectAttributes.addFlashAttribute("message", MSG_SUBMITTED);
        return redirectToAssignment(enrollmentId);
    }

    /**
     * 평가 시작 전 수정 — 같은 제출물을 고친다, 마감 불변. 이미지를 고르지 않으면 기존 이미지 유지.
     * 호출: POST /assignments/{reviewRoundId}/edit (API AS-03) ← review/assignment.html 수정 폼
     * 폼의 enrollmentId(숨은 칸)는 입력 오류 때 다시 그릴 화면을 고르는 데만 쓴다 — 권한·대상 판단은 서비스가 회차로 한다.
     */
    @PostMapping("/{reviewRoundId}/edit")
    public String edit(@PathVariable Long reviewRoundId,
                       @RequestParam Long enrollmentId,
                       @Valid @ModelAttribute("form") AssignmentSubmitDTO form,
                       BindingResult bindingResult,
                       @RequestParam(value = "images", required = false) List<MultipartFile> images,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return redisplay(details, enrollmentId, form, model);
        }
        Long ownedEnrollmentId = assignmentService.edit(details.getUserId(), reviewRoundId, form, images);
        redirectAttributes.addFlashAttribute("message", MSG_EDITED);
        return redirectToAssignment(ownedEnrollmentId);
    }

    /**
     * 반려 후 정식 재제출 — 새 제출물·새 평가 회차·새 7일 마감.
     * 호출: POST /assignments/resubmit (API AS-04) ← review/assignment.html 재제출 폼
     */
    @PostMapping("/resubmit")
    public String resubmit(@RequestParam Long enrollmentId,
                           @Valid @ModelAttribute("form") AssignmentSubmitDTO form,
                           BindingResult bindingResult,
                           @RequestParam(value = "images", required = false) List<MultipartFile> images,
                           @AuthenticationPrincipal AuthDetails details,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return redisplay(details, enrollmentId, form, model);
        }
        assignmentService.resubmit(details.getUserId(), enrollmentId, form, images);
        redirectAttributes.addFlashAttribute("message", MSG_RESUBMITTED);
        return redirectToAssignment(enrollmentId);
    }

    /**
     * [#15] 평가 결과·공식 피드백 (API AS-05, 화면 U-S14) — 알림 REVIEW_APPROVED/REVIEW_REJECTED 클릭 시에도 이 화면으로 온다.
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

    /**
     * 이 컨트롤러의 업무 오류 처리 — API 명세에서 "flash"로 정한 오류(400·409: 3차시 미완료, 이미 제출, 이미지 수·형식·크기,
     * 평가 중 수정 잠금, 재제출 불가 등)는 과제 화면으로 redirect하며 errorMessage로 안내한다(E-MODALS "평가 중 과제 잠금"도 flash로 대신).
     * 그 밖(404 없는·남의 수강 건과 회차, 403 기간 만료, 500)과 화면 조회(GET — AS-01·AS-05) 중 오류는 공통 오류 화면 규칙을 그대로 쓴다.
     * 돌아갈 곳은 폼의 enrollmentId — 숫자가 아니면 공통 오류 화면.
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        HttpStatus status = e.getErrorCode().getStatus();
        String enrollmentId = request.getParameter("enrollmentId");
        boolean flashable = "POST".equals(request.getMethod())
                && (status == HttpStatus.BAD_REQUEST || status == HttpStatus.CONFLICT)
                && enrollmentId != null && enrollmentId.matches("\\d+");
        if (!flashable) {
            return globalExceptionHandler.handleBusiness(e, request);
        }
        redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        return "redirect:/assignments?enrollmentId=" + enrollmentId;
    }

    /** 입력 형식 오류 — 입력값과 필드 오류를 유지한 채 같은 화면을 다시 그린다(API §1-6). 고른 이미지는 브라우저 규칙상 다시 골라야 한다 */
    private String redisplay(AuthDetails details, Long enrollmentId, AssignmentSubmitDTO form, Model model) {
        AssignmentViewDTO view = assignmentService.getAssignmentView(details.getUserId(), enrollmentId);
        addViewModel(model, view, form);
        return VIEW;
    }

    private void addViewModel(Model model, AssignmentViewDTO view, AssignmentSubmitDTO form) {
        model.addAttribute("assignment", view);
        model.addAttribute("state", view.getState());
        model.addAttribute("rounds", view.getRounds());
        model.addAttribute("lateWarning", view.isLateWarning());
        model.addAttribute("form", form);
    }

    private String redirectToAssignment(Long enrollmentId) {
        return "redirect:/assignments?enrollmentId=" + enrollmentId;
    }
}
