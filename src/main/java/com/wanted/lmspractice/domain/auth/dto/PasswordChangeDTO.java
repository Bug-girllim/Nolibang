package com.wanted.lmspractice.domain.auth.dto;

import com.wanted.lmspractice.domain.user.dto.SignupDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강사 첫 로그인 새 비밀번호 입력값과 형식 검증 (C-09)
 * [담당] 팀원 1 · #05   [화면] C-09   [기획] FR-012   [API] AU-14
 * [연결]
 *  - 호출받음: InstructorSetupController(@Valid @ModelAttribute), templates/auth/instructor-password.html(th:object·th:field)
 *  - 호출함:   SignupDTO의 비밀번호 규칙 상수(가입과 같은 규칙, 11-06 결정)
 * [기술] 🟢 Bean Validation (@NotBlank·@Size·@Pattern)
 * "아이디·임시 비밀번호와 달라야 함"은 저장된 값과 비교해야 해서 형식 검증이 아니라 InstructorAccountService가 확인한다.
 * 보안: 비밀번호 두 칸은 toString에서 제외해 로그에 남지 않게 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"newPassword", "newPasswordConfirm"})
public class PasswordChangeDTO {

    /** 8~16자, 영문·숫자·특수문자 모두 포함, 공백 불가 (가입 규칙과 같음) */
    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = SignupDTO.PASSWORD_MIN, max = SignupDTO.PASSWORD_MAX, message = SignupDTO.PASSWORD_SIZE_MESSAGE)
    @Pattern(regexp = SignupDTO.PASSWORD_REGEXP, message = SignupDTO.PASSWORD_MESSAGE)
    private String newPassword;

    /** newPassword와 같아야 한다 — 일치 여부는 InstructorSetupController가 BindingResult로 확인한다 */
    @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
    private String newPasswordConfirm;

    /** 비밀번호 확인이 일치하는지 (InstructorSetupController에서 호출) */
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
