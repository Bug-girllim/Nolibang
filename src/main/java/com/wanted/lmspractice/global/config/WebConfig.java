package com.wanted.lmspractice.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 추가 설정: 인터셉터 등록, 정적 리소스
 * 담당: 팀원 1 · #02 (#05 인터셉터 등록)
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // TODO #05: addInterceptors() — PasswordChangeInterceptor 등록 (C-09·로그아웃·정적 리소스 제외)
}
