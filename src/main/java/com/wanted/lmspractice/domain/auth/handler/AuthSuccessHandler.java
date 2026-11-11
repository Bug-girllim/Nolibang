package com.wanted.lmspractice.domain.auth.handler;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * [역할] 로그인 성공 후 이동할 곳을 정한다 — 로그인 전에 보려던 화면이 있으면 그 화면, 없으면 역할·상태별 화면 (FR-003, AU-02)
 * [담당] 팀원 1 · #04 (강사 비밀번호 변경 이동은 #05 흐름과 연결)   [화면] C-02, E-401   [API] AU-02
 * [연결]
 *  - 호출받음: Spring Security(로그인 성공 직후), 연결 위치 SecurityConfig.filterChain(formLogin.successHandler)
 *  - 호출함:   UserService.recordFirstLogin(학습자 첫 로그인 기록),
 *             RequestCache(SecurityConfig.requestCache — 로그인 필요 화면(E-401)으로 보내기 전에 저장해 둔 원래 요청),
 *             WebInvocationPrivilegeEvaluator(그 화면을 이 역할이 볼 수 있는지 SecurityConfig의 URL 규칙으로 확인)
 *  - 이동 순서:
 *      1) 강사+임시 비밀번호 → /auth/instructor/password(C-09) — 변경 전에는 다른 화면을 못 쓰므로 원래 화면보다 먼저
 *      2) 로그인 전에 보려던 화면이 있고 이 역할이 볼 수 있으면 → 그 화면 (E-401 "로그인하면 보던 화면으로 이어서 이동합니다")
 *      3) 그 밖에는 역할별 화면: 강사 → /instructor, 관리자 → /admin, 학습자 첫 로그인 → /hobbies, 학습자 이후 → /
 *     학습자의 첫 로그인 기록은 2)로 가더라도 항상 남긴다.
 * [기술] 🟢 SimpleUrlAuthenticationSuccessHandler 상속(Security 과목 방식)
 *        🟡 미학습 기술: RequestCache·WebInvocationPrivilegeEvaluator — Spring Security가 원래 요청을 저장·권한 확인하는 표준 도구.
 *           직접 세션에 주소를 넣고 URL 규칙을 다시 적으면 SecurityConfig와 규칙이 두 군데로 갈라져 어긋날 수 있어 표준 도구를 쓴다
 */
@Component
@RequiredArgsConstructor
public class AuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final RequestCache requestCache;
    // SecurityFilterChain 을 만든 뒤에 생기는 Bean 이라(이 핸들러는 그 체인을 만들 때 쓰인다) 쓸 때 꺼내 온다
    private final ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthDetails details = (AuthDetails) authentication.getPrincipal();
        clearAuthenticationAttributes(request);  // 이전 로그인 실패 때 세션에 남은 예외 정보 제거

        // 저장된 원래 요청은 쓰든 안 쓰든 한 번만 본다 — 남겨 두면 다음 로그인 때 엉뚱한 화면으로 갈 수 있다
        SavedRequest saved = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);

        String roleTarget = roleTargetUrl(details);
        if (details.getLoginUser().isMustChangePassword()) {
            getRedirectStrategy().sendRedirect(request, response, roleTarget);
            return;
        }
        String savedTarget = savedTargetUrl(saved, authentication);
        getRedirectStrategy().sendRedirect(request, response, savedTarget != null ? savedTarget : roleTarget);
    }

    /** API AU-02의 이동 표. 학습자는 첫 로그인 여부를 확인하면서 동시에 기록한다(같은 트랜잭션 한 번). */
    private String roleTargetUrl(AuthDetails details) {
        return switch (details.getRole()) {
            case INSTRUCTOR -> details.getLoginUser().isMustChangePassword()
                    ? "/auth/instructor/password"   // #05: 임시 비밀번호 상태는 변경 전까지 다른 화면 불가
                    : "/instructor";
            case ADMIN -> "/admin";
            case USER -> userService.recordFirstLogin(details.getUserId()) ? "/hobbies" : "/";  // FR-003
        };
    }

    /**
     * 로그인 전에 보려던 화면의 주소("/경로?쿼리", 컨텍스트 경로 제외). 없거나 이 역할이 볼 수 없는 화면이면 null.
     * 저장된 요청은 이 서버가 받은 요청 그대로라 다른 사이트 주소가 될 수 없다(오픈 리다이렉트 걱정 없음).
     */
    private String savedTargetUrl(SavedRequest savedRequest, Authentication authentication) {
        // HttpSessionRequestCache 가 저장하는 형태. 경로·쿼리를 따로 꺼낼 수 있다
        if (!(savedRequest instanceof DefaultSavedRequest saved) || !"GET".equalsIgnoreCase(saved.getMethod())) {
            return null;
        }
        String uri = saved.getRequestURI();
        String contextPath = saved.getContextPath() == null ? "" : saved.getContextPath();
        if (uri == null || !uri.startsWith(contextPath)) {
            return null;
        }
        String path = uri.substring(contextPath.length());
        String target = saved.getQueryString() == null ? path : path + "?" + saved.getQueryString();

        WebInvocationPrivilegeEvaluator evaluator = privilegeEvaluator.getIfAvailable();
        if (evaluator == null || !evaluator.isAllowed(contextPath, target, "GET", authentication)) {
            return null;  // 예: 학습자 화면을 보려다 강사로 로그인 → 403 대신 강사 첫 화면으로
        }
        return target;
    }
}
