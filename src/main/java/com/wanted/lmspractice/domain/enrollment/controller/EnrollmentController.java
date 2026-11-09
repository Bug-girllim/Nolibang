package com.wanted.lmspractice.domain.enrollment.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.enrollment.dto.ApplyResultDTO;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 수강 신청·대기 신청(EN-01)·대기 취소(EN-02)·수강 취소(EN-03) 요청을 받아 서비스에 넘기고, 결과를 flash 안내와 함께 redirect한다(PRG)
 * [담당] 팀원 2 · #09   [화면] U-S05 — 강의 상세(course/detail)의 신청 영역 조각 course/enroll-area.html에서 폼을 보낸다
 *       [기획] FR-052·053·054·055, D-52~D-55   [API] EN-01·EN-02·EN-03
 * [연결]
 *  - 호출받음: 브라우저 POST (SecurityConfig: /enrollments/** = 학습자(USER)만)
 *              course/enroll-area.html(신청·대기 신청·대기 취소·수강 취소), course/list.html·hobby/detail.html(신청·대기 신청)
 *  - 호출함:   EnrollmentService(apply·cancel), WaitlistService(cancel), GlobalExceptionHandler(409가 아닌 오류는 공통 오류 화면)
 *  - 이동:     EN-01 → /courses/{courseId}, EN-02 → returnUrl 또는 /my/learning, EN-03 → /my/learning
 * [기술] 🟢 @Controller·@PostMapping·@PathVariable·@RequestParam, 🟢 RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러 전용)
 */
@Controller
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    /** returnUrl이 없거나 허용하지 않는 값일 때 돌아갈 화면 (나의 학습, ML-01) */
    private static final String DEFAULT_RETURN_URL = "/my/learning";
    /**
     * 돌아갈 주소는 이 사이트 안의 경로만 — 외부 주소로 보내는 열린 redirect를 막는다(API EN-02 "내부 경로만 허용").
     * "/" 하나로 시작해야 한다: "//다른사이트"는 브라우저가 외부 주소로 해석하므로 (?!/)로 막는다.
     */
    private static final String SAFE_PATH = "^/(?!/)[A-Za-z0-9/_\\-]*$";

    private final EnrollmentService enrollmentService;
    private final WaitlistService waitlistService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /**
     * 수강 신청 — 즉시 확정 또는 대기 등록. 대기 신청 버튼도 같은 요청이다(서버가 대기 여부를 정한다, D-52).
     * 호출: POST /enrollments (API EN-01) ← course/enroll-area.html·course/list.html·hobby/detail.html 의 courseId 폼
     * 성공: redirect:/courses/{courseId} + flash message("수강이 확정되었습니다…" / "대기 n번으로 등록되었습니다…")
     */
    @PostMapping
    public String apply(@RequestParam Long courseId,
                        @AuthenticationPrincipal AuthDetails details,
                        RedirectAttributes redirectAttributes) {
        ApplyResultDTO result = enrollmentService.apply(details.getUserId(), courseId);
        redirectAttributes.addFlashAttribute("message", result.getMessage());
        return "redirect:/courses/" + courseId;
    }

    /**
     * 대기 취소. 다시 대기 신청하면 마지막 순번이 된다.
     * 호출: POST /enrollments/waitlist/{waitlistId}/cancel (API EN-02) ← course/enroll-area.html [대기 취소]
     * 성공: redirect:{returnUrl 또는 /my/learning} + flash message
     */
    @PostMapping("/waitlist/{waitlistId}/cancel")
    public String cancelWaiting(@PathVariable Long waitlistId,
                                @RequestParam(required = false) String returnUrl,
                                @AuthenticationPrincipal AuthDetails details,
                                RedirectAttributes redirectAttributes) {
        waitlistService.cancel(details.getUserId(), waitlistId);
        redirectAttributes.addFlashAttribute("message", "대기를 취소했습니다. 다시 대기 신청하면 마지막 순번으로 등록됩니다.");
        return "redirect:" + safeReturnUrl(returnUrl);
    }

    /**
     * 수강 취소 — 화면에서 "취소한 강의는 다시 신청할 수 없습니다" 확인을 받은 뒤 전송된다(D-54·D-55).
     * 호출: POST /enrollments/{enrollmentId}/cancel (API EN-03) ← course/enroll-area.html [수강 취소]
     * 성공: redirect:/my/learning + flash message
     */
    @PostMapping("/{enrollmentId}/cancel")
    public String cancel(@PathVariable Long enrollmentId,
                         @AuthenticationPrincipal AuthDetails details,
                         RedirectAttributes redirectAttributes) {
        enrollmentService.cancel(details.getUserId(), enrollmentId);
        redirectAttributes.addFlashAttribute("message", "수강을 취소했습니다. 취소한 강의는 다시 신청할 수 없습니다.");
        return "redirect:" + DEFAULT_RETURN_URL;
    }

    /**
     * 이 컨트롤러의 업무 오류 처리 — API 명세의 "flash" 오류(409: 이미 수강·대기 중, 재신청 불가, 배정 분반 없음, 평가 중 취소 불가 등)는
     * 원래 화면으로 redirect하며 errorMessage로 안내하고(E-MODALS "정원·상태 변경"·"재신청 불가"를 flash로 대신),
     * 그 밖(404 없는 강의·수강 건·대기, 403 준비 중 취미)은 공통 오류 화면 규칙(GlobalExceptionHandler)을 그대로 쓴다.
     * 돌아갈 곳: 신청(courseId가 있는 요청)은 그 강의 상세, 대기·수강 취소는 returnUrl(없으면 나의 학습).
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (e.getErrorCode().getStatus() != HttpStatus.CONFLICT) {
            return globalExceptionHandler.handleBusiness(e, request);
        }
        redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        return "redirect:" + backUrl(request);
    }

    /** 오류 뒤 돌아갈 화면 — 신청이면 그 강의 상세, 아니면 returnUrl(없으면 나의 학습) */
    private String backUrl(HttpServletRequest request) {
        String courseId = request.getParameter("courseId");
        if (courseId != null && courseId.matches("\\d+")) {
            return "/courses/" + courseId;
        }
        return safeReturnUrl(request.getParameter("returnUrl"));
    }

    /** 사이트 안 경로(/로 시작, 영문·숫자·/·_·- 만)만 허용하고 나머지는 나의 학습으로 — 외부 사이트로 보내는 주소 차단 */
    private String safeReturnUrl(String returnUrl) {
        return (returnUrl != null && returnUrl.matches(SAFE_PATH)) ? returnUrl : DEFAULT_RETURN_URL;
    }
}
