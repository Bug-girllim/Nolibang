package com.wanted.lmspractice.domain.auth.handler;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.auth.service.AuthService;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

/**
 * [역할] 강제로 끝난 세션(SessionRegistry에서 만료 표시된 세션)의 다음 요청을 이유에 맞는 화면으로 보낸다
 * [담당] 팀원 1 · #05·#06   [화면] E-DUP(/error/401-duplicate), C-02(/auth/login?error=disabled)   [기획] FR-012, U-S17
 * [연결]
 *  - 호출받음: Spring Security ConcurrentSessionFilter (SecurityConfig sessionManagement.expiredSessionStrategy)
 *  - 호출함:   AuthService.loadUserByUsername(지금 계정 상태 다시 읽기)
 *  - 이유:     세션이 강제로 끝나는 경우는 두 가지다.
 *             ① 같은 계정이 다른 기기에서 로그인(maximumSessions 1) → "다른 기기에서 로그인되어 로그아웃되었습니다"(E-DUP)
 *             ② 관리자가 강사를 비활성화(InstructorAccountService.expireSessions) → "비활성화된 계정입니다"(로그인 화면)
 *             예전에는 ②도 ①의 화면으로 보내 "다른 기기 로그인"이라는 틀린 이유를 보여 줬다.
 *             만료 표시에는 이유를 담을 수 없어서, 계정 상태를 다시 읽어 비활성이면 ②로 본다.
 * [기술] 🟢 Spring Security 핸들러(AuthFailHandler와 같은 방식), 🟡 SessionInformationExpiredStrategy — expiredUrl(고정 주소) 대신
 *        이유에 따라 주소를 고르려고 쓰는 인터페이스. 메서드 1개만 구현한다.
 */
@Component
@RequiredArgsConstructor
public class SessionExpiredHandler implements SessionInformationExpiredStrategy {

    static final String DUPLICATE_LOGIN_URL = "/error/401-duplicate";
    static final String DISABLED_URL = "/auth/login?error=disabled";

    private final AuthService authService;

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        HttpServletRequest request = event.getRequest();
        event.getResponse().sendRedirect(request.getContextPath() + targetUrl(event.getSessionInformation().getPrincipal()));
    }

    /** 지금 비활성 계정이면 비활성 안내, 그 밖에는 다른 기기 로그인 안내 */
    String targetUrl(Object principal) {
        if (principal instanceof AuthDetails details) {
            try {
                UserDetails latest = authService.loadUserByUsername(details.getUsername());
                if (latest instanceof AuthDetails current && current.getLoginUser().getStatus() == UserStatus.INACTIVE) {
                    return DISABLED_URL;
                }
            } catch (UsernameNotFoundException e) {
                // 계정을 찾을 수 없으면(탈퇴 등) 기존 안내를 그대로 쓴다
            }
        }
        return DUPLICATE_LOGIN_URL;
    }
}
