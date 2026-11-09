package com.wanted.lmspractice.domain.auth.dto;

import com.wanted.lmspractice.domain.user.dto.PasswordRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 계정 복구(C-05) "새 비밀번호 설정" 입력값 — 메일 링크의 토큰 + 새 비밀번호 두 번
 * [담당] 팀원 1 · #06   [화면] C-05   [기획] FR-004
 * [연결]
 *  - 호출받음: AccountRecoveryController.resetPassword(@Valid @ModelAttribute), templates/auth/recovery.html(숨은 칸 token)
 *  - 호출함:   PasswordRule(형식 상수, user 도메인의 같은 규칙 재사용)
 *  - 이후:     토큰 검증·1회 사용 처리와 비밀번호 저장은 UserService.resetPassword가 한다
 * [기술] 🟢 Bean Validation
 * 보안: 토큰 원문·비밀번호는 toString에서 제외해 로그에 남지 않게 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"token", "newPassword", "newPasswordConfirm"})
public class PasswordResetDTO {

    /** 메일 링크로 받은 RESET_PASSWORD 토큰 원문 (화면의 hidden 칸으로 다시 받는다) */
    @NotBlank(message = "인증 정보를 찾을 수 없습니다.")
    private String token;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = PasswordRule.MIN, max = PasswordRule.MAX, message = PasswordRule.SIZE_MESSAGE)
    @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.REGEX_MESSAGE)
    private String newPassword;

    /** newPassword와 같아야 한다 — AccountRecoveryController가 BindingResult로 확인한다 */
    @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
    private String newPasswordConfirm;

    public boolean isNewPasswordConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
