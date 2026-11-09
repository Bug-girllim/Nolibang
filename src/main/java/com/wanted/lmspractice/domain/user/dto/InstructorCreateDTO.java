package com.wanted.lmspractice.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 관리자 강사 계정 생성 폼 입력값과 형식 검증 (A-S04 "전문 강사 계정 생성")
 * [담당] 팀원 1 · #05   [화면] A-S04   [기획] FR-012, D-51   [API] AI-02
 * [연결]
 *  - 호출받음: AdminInstructorController(@Valid @ModelAttribute), templates/admin/instructors.html(th:object·th:field)
 *  - 호출함:   없음 (형식만 검증하고, 중복 확인은 UserService·InstructorAccountService가 한다)
 * [기술] 🟢 Bean Validation (@NotBlank·@Pattern·@Email·@Size)
 * 비밀번호 칸은 없다 — 임시 비밀번호는 서버가 만든다(InstructorAccountService).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class InstructorCreateDTO {

    /** API AI-02: 학습자 가입(US-02)과 같은 아이디 규칙 */
    @NotBlank(message = "아이디를 입력해 주세요.")
    @Pattern(regexp = SignupDTO.LOGIN_ID_REGEXP, message = SignupDTO.LOGIN_ID_MESSAGE)
    private String loginId;

    /** 실명 — 길이는 users.real_name(100)에 맞춘다. ❓ 강사 실명 형식 규칙은 자료에 없음(가입의 "한글 2~5자"를 강제하지 않음) */
    @NotBlank(message = "실명을 입력해 주세요.")
    @Size(max = 100, message = "실명이 너무 깁니다.")
    private String name;

    /** 계정당 고유 (중복 검사는 서비스) */
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일이 너무 깁니다.")
    private String email;

    /** 💡 API AI-02: 생성과 동시에 줄 취미 권한(선택). 체크박스를 하나도 고르지 않으면 빈 목록 */
    private List<Long> hobbyIds = new ArrayList<>();
}
