package com.wanted.lmspractice.domain.auth.service;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 로그인 시 아이디로 회원을 찾아 AuthDetails로 돌려준다.
 * [담당] 팀원 1 · #02   [화면] C-02   [API] AU-02
 * [연결]
 *  - 호출받음: Spring Security(DaoAuthenticationProvider)가 POST /auth/login 처리 중 호출
 *  - 호출함:   UserRepository.findByLoginId, AuthDetails
 *  - 비고:     같은 팀원 1 소유인 user 도메인 Repository를 직접 사용한다(작업분배 #02·#04 설계 그대로, 다른 팀원 도메인 아님)
 * [기술] 🟢 UserDetailsService
 */
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * 로그인 아이디로 회원 조회.
     * 호출: Spring Security (POST /auth/login, API AU-02)
     * @throws UsernameNotFoundException 없는 아이디 — Security가 BadCredentialsException으로 바꿔 계정 존재 여부를 숨긴다
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userRepository.findByLoginId(loginId)
                .map(AuthDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));  // 로그에 아이디 원문을 남기지 않는다
    }
}
