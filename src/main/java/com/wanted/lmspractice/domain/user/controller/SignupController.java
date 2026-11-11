package com.wanted.lmspractice.domain.user.controller;

import com.wanted.lmspractice.domain.user.dto.SignupDTO;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 회원가입 화면(C-03) 표시·가입 처리, 아이디·닉네임 중복 확인(@ResponseBody)
 * [담당] 팀원 1 · #04   [화면] C-03   [기획] FR-001·002   [API] US-01~04
 *       약관·만 14세 확인은 없다(11-06 결정)
 * [연결]
 *  - 호출받음: 브라우저(GET·POST /signup, GET /signup/check-id·check-nickname)
 *  - 호출함:   UserService(signup·중복 확인)
 *  - 이동:     가입 성공 → /auth/verify/sent (EmailVerifyController, 인증 메일 안내)
 * [기술] 🟢 @Controller, @Valid·BindingResult, RedirectAttributes(flash), @ResponseBody
 */
@Controller
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignupController {

    private final UserService userService;

    /**
     * 회원가입 화면. 호출: GET /signup (US-01)
     * 모델: signupDTO(빈 객체) → templates/user/signup.html
     */
    @GetMapping
    public String signupForm(Model model) {
        model.addAttribute("signupDTO", new SignupDTO());
        return "user/signup";
    }

    /**
     * 회원가입 처리. 호출: POST /signup (US-02)
     * 형식 오류·비밀번호 불일치·중복이 있으면 입력값을 유지한 채 같은 화면을 다시 보여 준다(비밀번호 칸은 비움).
     * 성공하면 자동 로그인 없이 인증 메일 안내 화면으로 이동한다(F-01).
     */
    @PostMapping
    public String signup(@Valid @ModelAttribute("signupDTO") SignupDTO signupDTO,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        // 형식 검증(@Valid)으로 알 수 없는 것은 BindingResult에 직접 오류로 넣는다 — 같은 화면 재표시를 위해 예외를 쓰지 않는다
        if (!bindingResult.hasFieldErrors("passwordConfirm") && !signupDTO.isPasswordConfirmed()) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
        }
        if (!bindingResult.hasFieldErrors("loginId") && !userService.isLoginIdAvailable(signupDTO.getLoginId())) {
            bindingResult.rejectValue("loginId", "duplicate", ErrorCode.DUPLICATE_LOGIN_ID.getMessage());
        }
        if (!bindingResult.hasFieldErrors("email") && !userService.isEmailAvailableForSignup(signupDTO.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", ErrorCode.DUPLICATE_EMAIL.getMessage());
        }
        if (!bindingResult.hasFieldErrors("nickname") && StringUtils.hasText(signupDTO.getNickname())
                && !userService.isNicknameAvailable(signupDTO.getNickname())) {
            bindingResult.rejectValue("nickname", "duplicate", ErrorCode.DUPLICATE_NICKNAME.getMessage());
        }

        if (bindingResult.hasErrors()) {
            signupDTO.setPassword(null);
            signupDTO.setPasswordConfirm(null);
            return "user/signup";
        }

        userService.signup(signupDTO);  // 여기서 중복을 한 번 더 확인한다(동시 가입 대비)
        redirectAttributes.addFlashAttribute("email", signupDTO.getEmail());
        return "redirect:/auth/verify/sent";
    }

    /**
     * 아이디 중복 확인. 호출: GET /signup/check-id?loginId= (US-03, signup.html의 JS)
     * 의미 고정: true = 사용 가능, false = 이미 사용 중. 최종 판단은 가입(POST)에서 다시 한다.
     */
    @GetMapping("/check-id")
    @ResponseBody
    public boolean checkId(@RequestParam String loginId) {
        return userService.isLoginIdAvailable(loginId);
    }

    /**
     * 닉네임 중복 확인. 호출: GET /signup/check-nickname?nickname= (US-04, 계정 설정 U-S17에서도 재사용)
     * 의미 고정: true = 사용 가능, false = 사용 중.
     */
    @GetMapping("/check-nickname")
    @ResponseBody
    public boolean checkNickname(@RequestParam String nickname) {
        return userService.isNicknameAvailable(nickname);
    }
}
