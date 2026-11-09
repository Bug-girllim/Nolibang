package com.wanted.lmspractice.domain.auth.dto;

import com.wanted.lmspractice.domain.user.dto.LoginUserDTO;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * [역할] Spring Security가 세션에 보관하는 로그인 사용자 (UserDetails 구현체)
 * [담당] 팀원 1 · #02 (#04: isEnabled 조건 추가)   [화면] 전 화면
 * [연결]
 *  - 호출받음: 모든 컨트롤러의 @AuthenticationPrincipal AuthDetails → getUserId(), getRole() (계약 C1, 메서드명 변경 금지),
 *             Security의 DaoAuthenticationProvider(getPassword·isEnabled), 헤더 템플릿(sec:authentication)
 *  - 호출함:   LoginUserDTO.from(User)
 *  - 계약:     C1 AuthDetails
 * [기술] 🟢 UserDetails 구현
 */
public class AuthDetails implements UserDetails {

    private final LoginUserDTO loginUser;
    private final String passwordHash;  // BCrypt 비교용. LoginUserDTO에 넣지 않아 화면 모델로 새어 나가지 않게 한다

    public AuthDetails(User user) {
        this.loginUser = LoginUserDTO.from(user);
        this.passwordHash = user.getPasswordHash();
    }

    /** 계약 C1: 로그인한 회원의 users.id */
    public Long getUserId() {
        return loginUser.getUserId();
    }

    /** 계약 C1: 로그인한 회원의 역할 */
    public Role getRole() {
        return loginUser.getRole();
    }

    /** 헤더·화면 표시용 사용자 정보 */
    public LoginUserDTO getLoginUser() {
        return loginUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("USER") 등은 "ROLE_" 접두사를 기대한다 (SecurityConfig의 hasRole과 짝)
        return List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole().name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginUser.getLoginId();
    }

    /**
     * 로그인 허용 여부 (AU-02): 이메일 인증 완료 && 활성 계정.
     * users.status가 ACTIVE가 되는 길은 이메일 인증(User.verifyEmail) 또는 관리자 발급뿐이라 ACTIVE 하나로 두 조건을 함께 본다.
     * false면 Security가 비밀번호 비교 전에 DisabledException을 던지고, AuthFailHandler가 미인증·비활성을 구분해 안내한다.
     */
    @Override
    public boolean isEnabled() {
        return loginUser.getStatus() == UserStatus.ACTIVE;
    }

    /**
     * 같은 회원(users.id)이면 같은 로그인 사용자로 본다 (#06 한 기기 로그인).
     * Security의 SessionRegistry는 principal을 Map 키로 써서 "이 회원의 세션 수"를 센다.
     * 로그인할 때마다 AuthDetails가 새로 만들어지므로, 이 비교가 없으면 매번 다른 사람으로 보여 maximumSessions(1)이 동작하지 않는다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthDetails other)) {
            return false;
        }
        return getUserId() != null && getUserId().equals(other.getUserId());
    }

    @Override
    public int hashCode() {
        return getUserId() == null ? 0 : getUserId().hashCode();
    }
}
