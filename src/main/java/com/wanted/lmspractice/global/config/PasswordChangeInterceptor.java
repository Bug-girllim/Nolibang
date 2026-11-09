package com.wanted.lmspractice.global.config;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * [역할] 임시 비밀번호 상태(mustChangePassword)인 로그인 사용자는 비밀번호 변경 화면(C-09) 외 모든 요청을 C-09로 보낸다 (FR-012)
 * [담당] 팀원 1 · #05   [화면] C-09   [API] §1-4, AU-13·14, ErrorCode PASSWORD_CHANGE_REQUIRED
 * [연결]
 *  - 호출받음: Spring MVC (컨트롤러 실행 직전), 등록 위치 WebConfig.addInterceptors — 제외 경로(C-09·로그아웃·정적 리소스·오류)도 거기서 정한다
 *  - 호출함:   SecurityContextHolder(세션의 AuthDetails → LoginUserDTO.mustChangePassword)
 *  - 이동:     화면 요청 → redirect /auth/instructor/password, JSON 요청 → 403 PASSWORD_CHANGE_REQUIRED(GlobalExceptionHandler)
 *  - 비고:     세션 값으로만 판단해 요청마다 DB를 읽지 않는다. 변경 후에는 InstructorSetupController가 세션 값을 갱신한다.
 * [기술] 🟢 HandlerInterceptor + WebMvcConfigurer (chap05)
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    /** C-09 주소 — WebConfig 제외 경로·redirect 대상으로 같이 쓴다 (다르면 무한 redirect) */
    public static final String PASSWORD_CHANGE_URL = "/auth/instructor/password";
    /** C-09 뷰 이름 (InstructorSetupController가 반환) */
    public static final String PASSWORD_CHANGE_VIEW = "auth/instructor-password";

    /**
     * 임시 비밀번호 상태가 아니면 통과. 상태면 화면 요청은 C-09로 보내고, JSON 요청은 403으로 끊는다(💡 API ErrorCode 표).
     * 비로그인 요청은 그대로 통과 — 로그인 필요 여부는 SecurityConfig가 이미 판단했다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!isPasswordChangeRequired()) {
            return true;
        }
        if (wantsJson(request)) {
            // 인터셉터에서 던진 예외도 @ControllerAdvice(GlobalExceptionHandler)가 받아 { code, message }로 응답한다
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        response.sendRedirect(request.getContextPath() + PASSWORD_CHANGE_URL);
        return false;
    }

    /** 로그인 사용자의 세션 정보에 임시 비밀번호 표시가 있는지 */
    private boolean isPasswordChangeRequired() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthDetails details
                && details.getLoginUser().isMustChangePassword();
    }

    /** GlobalExceptionHandler와 같은 판별 방식(❓ API §1-7: fetch 요청은 Accept에 application/json) */
    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }
}
