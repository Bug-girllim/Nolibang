package com.wanted.lmspractice.global.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 임시 비밀번호 강사(mustChangePassword)는 비밀번호 변경 화면(C-09) 외 접근 차단 (FR-012)
 * 담당: 팀원 1 · #05
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    // TODO #05: 구현
}
