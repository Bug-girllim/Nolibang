package com.wanted.lmspractice.domain.auth.handler;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * [역할] 로그인 성공 후 역할·상태에 따라 이동할 곳을 정한다 (FR-003, AU-02)
 * [담당] 팀원 1 · #04 (강사 비밀번호 변경 이동은 #05 흐름과 연결)   [화면] C-02   [API] AU-02
 * [연결]
 *  - 호출받음: Spring Security(로그인 성공 직후), 연결 위치 SecurityConfig.filterChain(formLogin.successHandler)
 *  - 호출함:   UserService.recordFirstLogin(학습자 첫 로그인 기록)
 *  - 이동:     강사+임시 비밀번호 → /auth/instructor/password(C-09), 강사 → /instructor, 관리자 → /admin,
 *             학습자 첫 로그인 → /hobbies, 학습자 이후 → /
 * [기술] 🟢 SimpleUrlAuthenticationSuccessHandler 상속(Security 과목 방식)
 */
@Component
@RequiredArgsConstructor
public class AuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthDetails details = (AuthDetails) authentication.getPrincipal();
        clearAuthenticationAttributes(request);  // 이전 로그인 실패 때 세션에 남은 예외 정보 제거
        getRedirectStrategy().sendRedirect(request, response, targetUrl(details));
    }

    /** API AU-02의 이동 표. 학습자는 첫 로그인 여부를 확인하면서 동시에 기록한다(같은 트랜잭션 한 번). */
    private String targetUrl(AuthDetails details) {
        return switch (details.getRole()) {
            case INSTRUCTOR -> details.getLoginUser().isMustChangePassword()
                    ? "/auth/instructor/password"   // #05: 임시 비밀번호 상태는 변경 전까지 다른 화면 불가
                    : "/instructor";
            case ADMIN -> "/admin";
            case USER -> userService.recordFirstLogin(details.getUserId()) ? "/hobbies" : "/";  // FR-003
        };
    }
}
