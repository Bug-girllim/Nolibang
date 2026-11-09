package com.wanted.lmspractice.domain.user.dto;

import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] AuthDetails 안에 담겨 세션에 저장되는 로그인 사용자 정보. 비밀번호·실명·이메일은 담지 않는다.
 * [담당] 팀원 1 · #02   [화면] 공통 헤더
 * [연결]
 *  - 호출받음: AuthDetails 생성자(from), 헤더 템플릿(sec:authentication="principal.loginUser.displayName")
 *  - 호출함:   User(엔티티 값 복사)
 * [기술] 🟢 DTO 정적 팩토리 from(entity). Serializable은 세션 저장용(UserDetails가 Serializable)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LoginUserDTO implements Serializable {

    private Long userId;
    private String loginId;
    private String nickname;        // 강사·관리자도 시드에서는 값이 있지만 null 가능
    private Role role;
    private UserStatus status;
    private boolean mustChangePassword;  // #05 PasswordChangeInterceptor가 사용

    /** User 엔티티 → 세션용 DTO */
    public static LoginUserDTO from(User user) {
        return new LoginUserDTO(
                user.getId(),
                user.getLoginId(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    /** 헤더에 표시할 이름: 닉네임이 있으면 닉네임, 없으면 아이디 */
    public String getDisplayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : loginId;
    }
}
