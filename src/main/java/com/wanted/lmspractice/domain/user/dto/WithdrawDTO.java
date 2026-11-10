package com.wanted.lmspractice.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 계정 설정 "회원 탈퇴" 탭 입력값 — 현재 비밀번호 + 안내 확인 체크
 * [담당] 팀원 1 · #06   [화면] U-S17
 * [연결]
 *  - 호출받음: AccountController.withdraw(@Valid @ModelAttribute), templates/user/account.html
 *  - 이후:     비밀번호 일치·진행 중 수강 여부는 WithdrawalService.withdraw가 판단한다
 * [기술] 🟢 Bean Validation
 * 보안: 비밀번호 칸은 toString에서 제외해 로그에 남지 않게 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "currentPassword")
public class WithdrawDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @AssertTrue(message = "탈퇴 안내를 확인하고 동의해 주세요.")
    private boolean agree;
}
