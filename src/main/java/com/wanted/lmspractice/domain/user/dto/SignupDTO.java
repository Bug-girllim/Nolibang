package com.wanted.lmspractice.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 회원가입 입력값과 형식 검증 규칙 (C-03 화면의 폼 한 장)
 * [담당] 팀원 1 · #04   [화면] C-03   [기획] FR-001·002   [API] US-02
 * [연결]
 *  - 호출받음: SignupController(@Valid @ModelAttribute), templates/user/signup.html(th:object·th:field)
 *  - 호출함:   없음 (형식만 검증하고, 중복 확인은 UserService가 한다)
 * [기술] 🟢 Bean Validation (@NotBlank·@Size·@Pattern·@Email)
 * 검증값은 11-06 HTML C-03 기준. 약관·만 14세 확인 항목은 없다(11-06 결정).
 * 보안: 비밀번호 두 칸은 toString에서 제외해 로그에 남지 않게 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"password", "passwordConfirm"})
public class SignupDTO {

    /*
     * 아이디·비밀번호 규칙 상수 — 강사 생성(InstructorCreateDTO, #05)·강사 새 비밀번호(PasswordChangeDTO, #05)·
     * 비밀번호 재설정(#06)이 같은 규칙을 쓰므로 한곳에 둔다(어노테이션 값이라 컴파일 상수여야 한다).
     */
    public static final String LOGIN_ID_REGEXP = "^(?=.*[A-Za-z])[A-Za-z0-9]{4,20}$";
    public static final String LOGIN_ID_MESSAGE = "아이디는 영문 또는 영문+숫자 4~20자로 입력해 주세요. (한글·특수문자·공백 불가)";
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 16;
    public static final String PASSWORD_SIZE_MESSAGE = "비밀번호는 8~16자로 입력해 주세요.";
    public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 포함하고 공백이 없어야 해요.";

    /** 영문 또는 영문+숫자(숫자만은 불가). 길이 4~20은 ❓ 임시값(API US-02: 길이 미정, #01 확정 필요) */
    @NotBlank(message = "아이디를 입력해 주세요.")
    @Pattern(regexp = LOGIN_ID_REGEXP, message = LOGIN_ID_MESSAGE)
    private String loginId;

    /** 8~16자, 영문·숫자·특수문자를 모두 포함(화면 안내문 기준, API ❓ "세 종류 모두 필수" 확인 필요), 공백 불가 */
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    @Pattern(regexp = PASSWORD_REGEXP, message = PASSWORD_MESSAGE)
    private String password;

    /** password와 같아야 한다 — 일치 여부는 두 값을 비교해야 해서 SignupController가 BindingResult로 확인한다 */
    @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
    private String passwordConfirm;

    /** 실명: 한글 2~5자 (담당 강사·관리자에게만 보인다 — NFR-04) */
    @NotBlank(message = "이름을 입력해 주세요.")
    @Pattern(regexp = "^[가-힣]{2,5}$", message = "이름은 한글 2~5글자로 입력해 주세요.")
    private String name;

    /** 계정당 고유(중복 검사는 UserService). 길이는 users.email(254)에 맞춘다 */
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일이 너무 깁니다.")
    private String email;

    /** 선택. 비어 있으면 NicknamePolicy가 자동 생성한다. 길이 2~20은 ❓ 임시값(API US-02: 길이 미정) */
    @Pattern(regexp = "^$|^\\S.{0,18}\\S$", message = "닉네임은 앞뒤 공백 없이 2~20자로 입력해 주세요.")
    private String nickname;

    /** 비밀번호 확인이 일치하는지 (SignupController에서 호출) */
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
