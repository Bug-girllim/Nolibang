package com.wanted.lmspractice.domain.user.dto;

import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 계정 설정 화면(U-S17)에 보여 줄 내 계정 정보 (조회 전용 항목 포함)
 * [담당] 팀원 1 · #06   [화면] U-S17
 * [연결]
 *  - 호출받음: UserService.getAccount(from), AccountController(모델 "account") → templates/user/account.html
 *  - 호출함:   없음
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * 실명은 본인 화면이라 보여 준다(다른 사람 화면에는 담지 않는다 — NFR-04). 비밀번호 해시는 담지 않는다.
 */
@Getter
@ToString(exclude = "realName")
@AllArgsConstructor
public class AccountDTO {

    private final String loginId;
    private final String realName;
    private final String email;
    private final String nickname;
    private final Role role;

    public static AccountDTO from(User user) {
        return new AccountDTO(user.getLoginId(), user.getRealName(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
