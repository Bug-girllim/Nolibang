package com.wanted.lmspractice.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 계정 설정 "비밀번호" 탭 입력값 — 현재 비밀번호 + 새 비밀번호 두 번
 * [담당] 팀원 1 · #06   [화면] U-S17
 * [연결]
 *  - 호출받음: AccountController.updatePassword(@Valid @ModelAttribute), templates/user/account.html
 *  - 호출함:   PasswordRule(형식 상수)
 *  - 이후:     현재 비밀번호 일치·새 비밀번호가 현재와 다른지는 UserService.changePassword가 판단한다
 * [기술] 🟢 Bean Validation
 * 보안: 비밀번호 칸은 toString에서 제외해 로그에 남지 않게 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"currentPassword", "newPassword", "newPasswordConfirm"})
public class PasswordUpdateDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = PasswordRule.MIN, max = PasswordRule.MAX, message = PasswordRule.SIZE_MESSAGE)
    @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.REGEX_MESSAGE)
    private String newPassword;

    /** newPassword와 같아야 한다 — 두 값 비교라 AccountController가 BindingResult로 확인한다 */
    @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
    private String newPasswordConfirm;

    /** 새 비밀번호 확인 일치 여부 (AccountController에서 호출) */
    public boolean isNewPasswordConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
