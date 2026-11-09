package com.wanted.lmspractice.domain.auth.controller;

import com.wanted.lmspractice.domain.auth.dto.PasswordResetDTO;
import com.wanted.lmspractice.domain.auth.service.EmailVerificationService;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 계정 복구 화면(C-05) — 이메일 입력 → 인증 메일 → 아이디 표시 / 새 비밀번호 설정
 * [담당] 팀원 1 · #06   [화면] C-05   [기획] FR-004(P1)   [API] AU-07
 * [연결]
 *  - 호출받음: 브라우저(로그인 화면의 "아이디 찾기·비밀번호 재설정" 링크, 복구 메일 링크)
 *  - 호출함:   EmailVerificationService(sendRecovery·recoverLoginId·checkToken), UserService.resetPassword
 *  - 화면:     templates/auth/recovery.html 한 장을 단계(step 1~3)·탭(tab=id|password)으로 나눠 그린다
 *  - 이동:     비밀번호 재설정 성공 → /auth/login (flash message)
 * [기술] 🟢 @Controller, @Valid·BindingResult, RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러 안의 BusinessException만)
 * 규칙: 소유 이메일로 받은 링크(토큰) 없이는 아이디도 비밀번호도 얻을 수 없다(#06 완료 조건).
 *       미가입 이메일도 "보냈다"는 같은 안내를 보여 계정 존재 여부를 드러내지 않는다(❓ 임시 결정, AU-06과 같은 방식).
 */
@Controller
@RequestMapping("/auth/recovery")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private static final String VIEW = "auth/recovery";
    private static final String TAB_ID = "id";
    private static final String TAB_PASSWORD = "password";

    private final EmailVerificationService emailVerificationService;
    private final UserService userService;

    /**
     * 1단계: 이메일 입력 화면. 메일 발송 후 redirect로 돌아오면 flash sent·maskedEmail이 있어 2단계 안내를 함께 보인다.
     * 호출: GET /auth/recovery?tab=id|password (로그인 화면 링크, 탭 전환)
     */
    @GetMapping
    public String recoveryForm(@RequestParam(defaultValue = TAB_ID) String tab, Model model) {
        boolean sent = Boolean.TRUE.equals(model.asMap().get("sent"));
        model.addAttribute("tab", normalizeTab(tab));
        model.addAttribute("step", sent ? 2 : 1);
        return VIEW;
    }

    /**
     * 복구 메일 발송. 가입 여부와 관계없이 같은 안내로 돌아간다(계정 존재 숨김).
     * 호출: POST /auth/recovery/send (email, tab)
     */
    @PostMapping("/send")
    public String send(@RequestParam(defaultValue = "") String email,
                       @RequestParam(defaultValue = TAB_ID) String tab,
                       RedirectAttributes redirectAttributes) {
        String normalizedTab = normalizeTab(tab);
        if (email.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "가입한 이메일을 입력해 주세요.");
            return "redirect:/auth/recovery?tab=" + normalizedTab;
        }
        emailVerificationService.sendRecovery(email, toPurpose(normalizedTab));
        redirectAttributes.addFlashAttribute("sent", true);
        redirectAttributes.addFlashAttribute("maskedEmail", EmailVerificationService.maskEmail(email.trim()));
        return "redirect:/auth/recovery?tab=" + normalizedTab;
    }

    /**
     * 3단계(아이디 찾기): 메일 링크를 열면 토큰을 1회 사용하고 아이디를 보여 준다.
     * 호출: GET /auth/recovery/login-id?token= ← 아이디 찾기 메일 링크
     * 실패(INVALID_TOKEN·TOKEN_EXPIRED)는 handleBusiness가 1단계 화면에 메시지로 보여 준다.
     */
    @GetMapping("/login-id")
    public String showLoginId(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("loginId", emailVerificationService.recoverLoginId(token));
        model.addAttribute("tab", TAB_ID);
        model.addAttribute("step", 3);
        return VIEW;
    }

    /**
     * 3단계(비밀번호 재설정): 토큰이 유효한지만 확인하고 새 비밀번호 폼을 보여 준다(사용 처리는 저장할 때).
     * 호출: GET /auth/recovery/password?token= ← 비밀번호 재설정 메일 링크
     */
    @GetMapping("/password")
    public String passwordForm(@RequestParam(required = false) String token, Model model) {
        emailVerificationService.checkToken(token, TokenPurpose.RESET_PASSWORD);
        PasswordResetDTO dto = new PasswordResetDTO();
        dto.setToken(token);
        model.addAttribute("passwordResetDTO", dto);
        model.addAttribute("tab", TAB_PASSWORD);
        model.addAttribute("step", 3);
        return VIEW;
    }

    /**
     * 새 비밀번호 저장. 형식 오류·확인 불일치면 같은 폼을 다시 보여 주고(비밀번호 칸은 비움), 성공하면 로그인 화면으로 보낸다.
     * 호출: POST /auth/recovery/password (token, newPassword, newPasswordConfirm)
     */
    @PostMapping("/password")
    public String resetPassword(@Valid @ModelAttribute("passwordResetDTO") PasswordResetDTO passwordResetDTO,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("newPasswordConfirm") && !passwordResetDTO.isNewPasswordConfirmed()) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
        }
        if (bindingResult.hasErrors()) {
            passwordResetDTO.setNewPassword(null);
            passwordResetDTO.setNewPasswordConfirm(null);
            model.addAttribute("tab", TAB_PASSWORD);
            model.addAttribute("step", 3);
            return VIEW;
        }
        userService.resetPassword(passwordResetDTO.getToken(), passwordResetDTO.getNewPassword());
        redirectAttributes.addFlashAttribute("message", "비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요.");
        return "redirect:/auth/login";
    }

    /**
     * 이 컨트롤러의 BusinessException(만료·재사용·목적이 다른 토큰 등)을 오류 화면이 아니라 1단계 화면 메시지로 보여 준다.
     * 다시 메일을 받을 수 있게 하려는 것 — EmailVerifyController.handleBusiness와 같은 방식.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, HttpServletRequest request, Model model) {
        model.addAttribute("errorMessage", e.getErrorCode().getMessage() + " 메일을 다시 받아 주세요.");
        model.addAttribute("tab", request.getRequestURI().endsWith("/" + TAB_PASSWORD) ? TAB_PASSWORD : TAB_ID);
        model.addAttribute("step", 1);
        return VIEW;
    }

    /** 알 수 없는 탭 값은 아이디 찾기로 본다 */
    private String normalizeTab(String tab) {
        return TAB_PASSWORD.equals(tab) ? TAB_PASSWORD : TAB_ID;
    }

    /** 화면 탭 → 토큰 목적. 가입 인증(VERIFY_EMAIL)은 이 화면에서 만들 수 없다 */
    private TokenPurpose toPurpose(String tab) {
        return TAB_PASSWORD.equals(tab) ? TokenPurpose.RESET_PASSWORD : TokenPurpose.RECOVER_LOGIN_ID;
    }
}
