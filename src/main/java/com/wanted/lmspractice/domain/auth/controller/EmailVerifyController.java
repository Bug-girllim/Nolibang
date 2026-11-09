package com.wanted.lmspractice.domain.auth.controller;

import com.wanted.lmspractice.domain.auth.service.EmailVerificationService;
import com.wanted.lmspractice.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 이메일 인증 안내(발송 후)·인증 결과·인증 메일 재발송 화면 (C-04, 한 템플릿을 공용으로 쓴다)
 * [담당] 팀원 1 · #04   [화면] C-04   [기획] FR-001   [API] AU-04·05·06
 * [연결]
 *  - 호출받음: 브라우저(GET /auth/verify/sent·/auth/verify?token=, POST /auth/verify/resend), SignupController(가입 후 redirect)
 *  - 호출함:   EmailVerificationService(verify·resend·maskEmail)
 *  - 화면:     templates/auth/verify-result.html — 모델 sent·maskedEmail / success·errorMessage 로 세 상태를 나눠 그린다
 * [기술] 🟢 @Controller, RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러의 BusinessException만 같은 화면으로 처리)
 */
@Controller
@RequestMapping("/auth/verify")
@RequiredArgsConstructor
public class EmailVerifyController {

    private static final String VIEW = "auth/verify-result";

    private final EmailVerificationService emailVerificationService;

    /**
     * 인증 메일 발송 안내. 호출: GET /auth/verify/sent (AU-04) ← 가입(US-02)·재발송(AU-06)의 redirect
     * flash로 받은 이메일은 일부만 가려 보여 준다. 새로고침 등으로 flash가 없으면 이메일 없이 안내만 보인다.
     */
    @GetMapping("/sent")
    public String sent(Model model) {
        Object email = model.asMap().get("email");  // redirect 때 담은 flash 값 (가입한 이메일 원문)
        model.addAttribute("sent", true);
        model.addAttribute("maskedEmail", email == null ? null : EmailVerificationService.maskEmail(email.toString()));
        return VIEW;
    }

    /**
     * 이메일 인증 처리. 호출: GET /auth/verify?token= (AU-05) ← 메일 링크
     * 성공하면 "로그인하기" 버튼을 보여 준다(가입 직후 자동 로그인은 없다 — F-01).
     * 실패(INVALID_TOKEN·TOKEN_EXPIRED)는 아래 handleBusiness가 같은 화면에 메시지와 재발송 폼으로 보여 준다.
     * token이 없어도(로그인 화면의 "인증 메일 재발송" 링크) 같은 실패 화면으로 간다.
     */
    @GetMapping
    public String verify(@RequestParam(required = false) String token, Model model) {
        emailVerificationService.verify(token);
        model.addAttribute("success", true);
        return VIEW;
    }

    /**
     * 인증 메일 재발송. 호출: POST /auth/verify/resend (AU-06)
     * 이메일이 없거나 이미 인증된 경우에도 똑같이 안내 화면으로 보내 계정 존재 여부를 드러내지 않는다.
     */
    @PostMapping("/resend")
    public String resend(@RequestParam(defaultValue = "") String email, RedirectAttributes redirectAttributes) {
        emailVerificationService.resend(email);
        redirectAttributes.addFlashAttribute("email", email.trim());
        return "redirect:/auth/verify/sent";
    }

    /**
     * 이 컨트롤러에서 난 BusinessException을 오류 화면이 아니라 같은 인증 화면에 메시지로 보여 준다 (AU-05: "같은 화면에 success=false").
     * 전역 GlobalExceptionHandler보다 이 컨트롤러 안의 핸들러가 먼저 적용된다.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, Model model) {
        model.addAttribute("success", false);
        model.addAttribute("errorMessage", e.getErrorCode().getMessage());
        return VIEW;
    }
}
