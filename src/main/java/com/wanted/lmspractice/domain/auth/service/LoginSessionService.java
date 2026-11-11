package com.wanted.lmspractice.domain.auth.service;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/**
 * [역할] 한 회원이 이미 로그인해 둔 세션을 모두 끊는다 — 상태 검사(AuthDetails.isEnabled)는 로그인 시점에만 돌기 때문
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리 (InstructorAccountService 의 강사 비활성화 세션 만료를 함께 쓰도록 옮김)
 * [연결]
 *  - 호출받음: WithdrawalService.withdraw(탈퇴한 계정의 다른 기기 세션), InstructorAccountService.changeStatus(강사 비활성화)
 *  - 호출함:   SessionRegistry(SecurityConfig Bean — maximumSessions 와 같은 목록)
 *  - 연결됨:   만료된 세션의 다음 요청은 SessionExpiredHandler 가 받는다
 * [기술] 🟢 Spring Security (SessionRegistry 는 #06 maximumSessions 에서 이미 쓰는 Bean)
 */
@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private final SessionRegistry sessionRegistry;

    /** 회원의 로그인 세션을 모두 만료시킨다. 로그인한 세션이 없으면 아무것도 하지 않는다 */
    public void expireAll(Long userId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof AuthDetails details && userId.equals(details.getUserId()))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
