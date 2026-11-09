package com.wanted.lmspractice.domain.auth.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.auth.dto.PasswordChangeDTO;
import com.wanted.lmspractice.domain.auth.service.AuthService;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.config.PasswordChangeInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 강사 첫 로그인 비밀번호 변경 화면(C-09) 표시·변경 처리
 * [담당] 팀원 1 · #05   [화면] C-09   [기획] FR-012   [API] AU-13·14
 *       약관 확인 화면 C-08은 만들지 않는다(11-06 결정) — 임시 비밀번호 로그인 → C-09 → 강사 홈
 * [연결]
 *  - 호출받음: AuthSuccessHandler(임시 비밀번호 강사 로그인 직후 이동), PasswordChangeInterceptor(다른 URL 접근 시 이동)
 *             — INSTRUCTOR만(SecurityConfig "/auth/instructor/**")
 *  - 호출함:   InstructorAccountService(getInstructor·isUsableNewPassword·changeInitialPassword),
 *             AuthService.loadUserByUsername(변경 후 세션 로그인 정보 갱신)
 *  - 이동:     변경 성공 → /instructor (강사 홈 T-S01, #07)
 *  - 화면:     templates/auth/instructor-password.html
 * [기술] 🟢 @Controller, @Valid·BindingResult, RedirectAttributes(flash), @AuthenticationPrincipal
 *        🟡 미학습 기술: SecurityContextRepository로 세션의 로그인 정보 교체 — 아래 refreshLogin 주석 참고
 */
@Controller
@RequestMapping("/auth/instructor")
@RequiredArgsConstructor
public class InstructorSetupController {

    private static final String INSTRUCTOR_HOME = "/instructor";

    private final InstructorAccountService instructorAccountService;
    private final AuthService authService;
    // Spring Security 기본 세션 저장 방식과 같은 저장소 (세션 속성 SPRING_SECURITY_CONTEXT)
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * 새 비밀번호 설정 화면. 호출: GET /auth/instructor/password (AU-13)
     * 모델: instructor(아이디·이메일 표시), passwordChangeDTO(빈 폼)
     * 이미 바꾼 강사가 주소로 들어오면 강사 홈으로 보낸다.
     */
    @GetMapping("/password")
    public String passwordForm(@AuthenticationPrincipal AuthDetails details, Model model) {
        if (!details.getLoginUser().isMustChangePassword()) {
            return "redirect:" + INSTRUCTOR_HOME;
        }
        model.addAttribute("instructor", instructorAccountService.getInstructor(details.getUserId()));
        model.addAttribute("passwordChangeDTO", new PasswordChangeDTO());
        return PasswordChangeInterceptor.PASSWORD_CHANGE_VIEW;
    }

    /**
     * 새 비밀번호 저장. 호출: POST /auth/instructor/password (AU-14)
     * 형식 오류·확인 불일치·아이디/임시 비밀번호와 같음 → 같은 화면 재표시(비밀번호 칸은 비움).
     * 성공 → 세션의 mustChangePassword를 false로 갱신한 뒤 강사 홈으로 이동(그래야 인터셉터가 더 막지 않는다).
     */
    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AuthDetails details,
                                 @Valid @ModelAttribute("passwordChangeDTO") PasswordChangeDTO passwordChangeDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        if (!details.getLoginUser().isMustChangePassword()) {
            return "redirect:" + INSTRUCTOR_HOME;
        }
        if (!bindingResult.hasFieldErrors("newPasswordConfirm") && !passwordChangeDTO.isPasswordConfirmed()) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
        }
        // AU-14: 아이디·임시 비밀번호와 같으면 INVALID_NEW_PASSWORD — 화면 재표시를 위해 예외 대신 BindingResult에 넣는다
        if (!bindingResult.hasFieldErrors("newPassword")
                && !instructorAccountService.isUsableNewPassword(details.getUserId(), passwordChangeDTO.getNewPassword())) {
            bindingResult.rejectValue("newPassword", "unusable", "아이디·임시 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }
        if (bindingResult.hasErrors()) {
            passwordChangeDTO.setNewPassword(null);
            passwordChangeDTO.setNewPasswordConfirm(null);
            model.addAttribute("instructor", instructorAccountService.getInstructor(details.getUserId()));
            return PasswordChangeInterceptor.PASSWORD_CHANGE_VIEW;
        }

        instructorAccountService.changeInitialPassword(details.getUserId(), passwordChangeDTO);  // 같은 규칙을 한 번 더 확인한다
        refreshLogin(details, request, response);
        redirectAttributes.addFlashAttribute("message", "비밀번호가 변경되었습니다.");
        return "redirect:" + INSTRUCTOR_HOME;
    }

    /**
     * 세션에 저장된 로그인 정보(AuthDetails)를 DB 최신 값으로 바꾼다.
     * 🟡 미학습 기술: SecurityContextHolder·SecurityContextRepository 직접 사용
     *   - 필요한 이유: 로그인 정보는 로그인할 때 세션에 한 번 저장되므로, 바꾸지 않으면 mustChangePassword = true가 남아
     *     PasswordChangeInterceptor가 계속 C-09로 돌려보낸다(완료 조건 "변경 후 강사 홈" 불가).
     *   - 배운 기술로 대신할 수 없는 이유: 배운 방식은 다시 로그인시키는 것뿐인데, 그러면 변경 직후 강사 홈으로 바로 갈 수 없다.
     */
    private void refreshLogin(AuthDetails details, HttpServletRequest request, HttpServletResponse response) {
        UserDetails refreshed = authService.loadUserByUsername(details.getUsername());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
