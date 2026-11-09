package com.wanted.lmspractice.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [역할] Spring MVC 추가 설정 — 인터셉터 등록
 * [담당] 팀원 1 · #02 (#05 인터셉터 등록)   [API] §1-4
 * [연결]
 *  - 호출받음: Spring MVC 시작 시 1회
 *  - 호출함:   PasswordChangeInterceptor(#05)
 * [기술] 🟢 WebMvcConfigurer.addInterceptors (chap05)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PasswordChangeInterceptor passwordChangeInterceptor;

    /**
     * 임시 비밀번호 강사 차단 인터셉터를 모든 요청에 건다.
     * 제외 경로를 빠뜨리면 C-09 화면·CSS까지 C-09로 보내 무한 redirect가 된다(#05 주의 사항):
     * C-09 자신, 로그아웃(Security 필터가 먼저 처리하지만 안전하게 제외), 정적 리소스, 오류 화면.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        PasswordChangeInterceptor.PASSWORD_CHANGE_URL,
                        "/auth/logout",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/error", "/error/**");
    }
}
