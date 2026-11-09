package com.wanted.lmspractice.domain.user.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.dto.AccountUpdateDTO;
import com.wanted.lmspractice.domain.user.dto.PasswordUpdateDTO;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 계정 설정 화면(U-S17) — "기본 정보" 탭(닉네임 변경)·"비밀번호" 탭(비밀번호 변경). 회원 탈퇴 탭은 P2라 제외.
 * [담당] 팀원 1 · #06   [화면] U-S17   [기획] FR-002, U-S17   [API] US-05
 * [연결]
 *  - 호출받음: 브라우저(GET /my/account, POST /my/account/nickname·/password) — SecurityConfig에서 로그인 사용자 세 역할 공용
 *  - 호출함:   UserService(getAccount·changeNickname·changePassword, 화면 표시용 미리 확인 isNicknameAvailableFor·isCurrentPassword)
 *  - 화면:     templates/user/account.html — 모델 account(AccountDTO)·accountUpdateDTO·passwordUpdateDTO·tab
 *  - 닉네임 중복 확인 버튼은 #04 SignupController의 GET /signup/check-nickname(@ResponseBody)을 그대로 재사용한다
 * [기술] 🟢 @Controller, @AuthenticationPrincipal AuthDetails(C1), @Valid·BindingResult, RedirectAttributes(flash)
 * 아이디·실명·이메일은 조회 전용이다(❓ 수정 허용 범위 자료 없음 → CONVENTIONS §10 #17 임시값).
 */
@Controller
@RequestMapping("/my/account")
@RequiredArgsConstructor
public class AccountController {

    private static final String VIEW = "user/account";
    private static final String TAB_INFO = "info";
    private static final String TAB_PASSWORD = "password";

    private final UserService userService;

    /**
     * 계정 설정 화면. 호출: GET /my/account?tab=info|password
     */
    @GetMapping
    public String accountForm(@AuthenticationPrincipal AuthDetails details,
                              @RequestParam(defaultValue = TAB_INFO) String tab,
                              Model model) {
        AccountUpdateDTO nicknameForm = new AccountUpdateDTO();
        nicknameForm.setNickname(userService.getAccount(details.getUserId()).getNickname());
        model.addAttribute("accountUpdateDTO", nicknameForm);
        model.addAttribute("passwordUpdateDTO", new PasswordUpdateDTO());
        return render(details, TAB_PASSWORD.equals(tab) ? TAB_PASSWORD : TAB_INFO, model);
    }

    /**
     * 닉네임 변경. 형식 오류·중복이면 같은 화면에 칸별 오류로 다시 보여 준다.
     * 성공하면 세션의 헤더 표시 이름도 바꿔 다시 로그인하지 않아도 새 닉네임이 보이게 한다.
     * 호출: POST /my/account/nickname (nickname)
     */
    @PostMapping("/nickname")
    public String updateNickname(@AuthenticationPrincipal AuthDetails details,
                                 @Valid @ModelAttribute("accountUpdateDTO") AccountUpdateDTO accountUpdateDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("nickname")
                && !userService.isNicknameAvailableFor(details.getUserId(), accountUpdateDTO.getNickname())) {
            bindingResult.rejectValue("nickname", "duplicate", ErrorCode.DUPLICATE_NICKNAME.getMessage());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordUpdateDTO", new PasswordUpdateDTO());
            return render(details, TAB_INFO, model);
        }

        String saved = userService.changeNickname(details.getUserId(), accountUpdateDTO.getNickname());
        details.getLoginUser().setNickname(saved);  // 세션에 든 헤더 표시용 값만 맞춘다(DB 저장은 위 서비스가 끝냄)
        redirectAttributes.addFlashAttribute("message", "닉네임을 변경했어요.");
        return "redirect:/my/account";
    }

    /**
     * 비밀번호 변경. 형식 오류·확인 불일치·현재 비밀번호 불일치·현재와 같은 새 비밀번호는 같은 화면에 칸별 오류로 보여 준다.
     * 호출: POST /my/account/password (currentPassword, newPassword, newPasswordConfirm)
     */
    @PostMapping("/password")
    public String updatePassword(@AuthenticationPrincipal AuthDetails details,
                                 @Valid @ModelAttribute("passwordUpdateDTO") PasswordUpdateDTO passwordUpdateDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("newPasswordConfirm") && !passwordUpdateDTO.isNewPasswordConfirmed()) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (!bindingResult.hasFieldErrors("currentPassword")
                && !userService.isCurrentPassword(details.getUserId(), passwordUpdateDTO.getCurrentPassword())) {
            bindingResult.rejectValue("currentPassword", "mismatch", ErrorCode.PASSWORD_MISMATCH.getMessage());
        }
        if (!bindingResult.hasFieldErrors("newPassword")
                && passwordUpdateDTO.getNewPassword().equals(passwordUpdateDTO.getCurrentPassword())) {
            bindingResult.rejectValue("newPassword", "same", "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }
        if (bindingResult.hasErrors()) {
            passwordUpdateDTO.setCurrentPassword(null);
            passwordUpdateDTO.setNewPassword(null);
            passwordUpdateDTO.setNewPasswordConfirm(null);
            AccountUpdateDTO nicknameForm = new AccountUpdateDTO();
            nicknameForm.setNickname(userService.getAccount(details.getUserId()).getNickname());
            model.addAttribute("accountUpdateDTO", nicknameForm);
            return render(details, TAB_PASSWORD, model);
        }

        userService.changePassword(details.getUserId(),
                passwordUpdateDTO.getCurrentPassword(), passwordUpdateDTO.getNewPassword());
        redirectAttributes.addFlashAttribute("message", "비밀번호를 변경했어요.");
        return "redirect:/my/account?tab=" + TAB_PASSWORD;
    }

    /** 화면 공통 모델(account·tab)을 담고 뷰 이름을 돌려준다 */
    private String render(AuthDetails details, String tab, Model model) {
        model.addAttribute("account", userService.getAccount(details.getUserId()));
        model.addAttribute("tab", tab);
        return VIEW;
    }
}
