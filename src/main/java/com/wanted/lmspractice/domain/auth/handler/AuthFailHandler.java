package com.wanted.lmspractice.domain.auth.handler;

import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * [역할] 로그인 실패 원인을 구분해 로그인 화면에 알맞은 안내를 보여 주도록 이동시킨다 (AU-02)
 * [담당] 팀원 1 · #04   [화면] C-02   [API] AU-02
 * [연결]
 *  - 호출받음: Spring Security(로그인 실패 직후), 연결 위치 SecurityConfig.filterChain(formLogin.failureHandler)
 *  - 호출함:   UserService.findStatusByLoginId (DisabledException일 때 미인증/비활성 구분)
 *  - 이동:     /auth/login?error (아이디·비밀번호 불일치), ?error=unverified (이메일 미인증), ?error=disabled (비활성)
 *  - 문구:     AuthController.loginForm이 쿼리 값으로 메시지를 정한다
 * [기술] 🟢 SimpleUrlAuthenticationFailureHandler 상속, 🟢 DisabledException·BadCredentialsException 구분
 */
@Component
@RequiredArgsConstructor
public class AuthFailHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        getRedirectStrategy().sendRedirect(request, response, targetUrl(request, exception));
    }

    /**
     * 없는 아이디·틀린 비밀번호는 모두 ?error 하나로 합쳐 계정 존재 여부를 숨긴다(BadCredentialsException).
     * 로그인이 막힌 계정(DisabledException)만 이유를 구분한다 — 이메일 미인증은 재발송 안내가 필요해서(API AU-02).
     */
    private String targetUrl(HttpServletRequest request, AuthenticationException exception) {
        if (exception instanceof DisabledException) {
            // 폼 입력 이름은 SecurityConfig의 usernameParameter("loginId")와 같다
            return userService.findStatusByLoginId(request.getParameter("loginId"))
                    .map(status -> switch (status) {
                        case PENDING_EMAIL -> "/auth/login?error=unverified";
                        case WITHDRAWN -> "/auth/login?error";   // 탈퇴 여부는 드러내지 않는다(아이디·비밀번호 불일치와 같은 문구)
                        default -> "/auth/login?error=disabled";
                    })
                    .orElse("/auth/login?error");
        }
        return "/auth/login?error";
    }
}
