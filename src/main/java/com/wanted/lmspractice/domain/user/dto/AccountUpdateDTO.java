package com.wanted.lmspractice.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 계정 설정 "기본 정보" 탭 입력값 — 수정 가능한 항목은 닉네임 하나다.
 *        아이디·실명·이메일은 조회 전용(❓ 수정 허용 범위 자료 없음 → CONVENTIONS §10 #17 임시값: 닉네임·비밀번호만 변경)
 * [담당] 팀원 1 · #06   [화면] U-S17   [기획] FR-002
 * [연결]
 *  - 호출받음: AccountController.updateNickname(@Valid @ModelAttribute), templates/user/account.html(th:object)
 *  - 호출함:   없음 (형식만 검증하고, 중복 확인은 UserService.changeNickname → NicknamePolicy가 한다)
 * [기술] 🟢 Bean Validation
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class AccountUpdateDTO {

    /** 가입 때와 같은 규칙(앞뒤 공백 없이 2~20자, ❓ 임시값 — SignupDTO.nickname과 동일). 계정 설정에서는 비울 수 없다 */
    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Pattern(regexp = "^\\S.{0,18}\\S$", message = "닉네임은 앞뒤 공백 없이 2~20자로 입력해 주세요.")
    private String nickname;
}
