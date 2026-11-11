package com.wanted.lmspractice.global.config;

import com.wanted.lmspractice.domain.auth.handler.AuthFailHandler;
import com.wanted.lmspractice.domain.auth.handler.AuthSuccessHandler;
import com.wanted.lmspractice.domain.auth.handler.SessionExpiredHandler;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * [역할] URL별 접근 권한, formLogin, logout, BCrypt PasswordEncoder 등록, 한 기기 로그인(#06)
 * [담당] 팀원 1 · #02, #06(sessionManagement)   [API] §1-3, AU-02, AU-03
 * [연결]
 *  - 호출받음: Spring Security 필터 체인(모든 요청)
 *  - 호출함:   AuthService(UserDetailsService Bean을 자동 사용), 오류 화면 /error/403·/error/401·/error/401-expired·/error/401-duplicate(MainController)
 *  - 연결됨:   #04 AuthSuccessHandler·AuthFailHandler (formLogin)
 *  - 연결됨:   #06 maximumSessions(1) → 다른 기기 로그인으로 끝난 세션은 /error/401-duplicate (E-DUP),
 *             관리자가 비활성화해 끝난 강사 세션은 /auth/login?error=disabled (SessionExpiredHandler가 구분)
 *  - 연결됨:   로그인 필요 → /error/401 (E-401), 세션 시간 만료 → /error/401-expired (E-SESSION)
 *  - 연결됨:   requestCache — E-401로 보내기 전 원래 화면을 저장하고, 로그인 성공 후 AuthSuccessHandler가 그 화면으로 보낸다
 *  - 규칙:     다른 팀원은 이 파일을 직접 고치지 않고 팀원 1에게 요청한다
 * [기술] 🟢 SecurityFilterChain, formLogin, BCrypt, 🟢 sessionManagement(Security 과목)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 비밀번호 암호화·비교 (#04 가입, #05 강사 계정 생성, #06 재설정에서도 사용) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * URL 권한 규칙 (API 명세 §1-3). 위에 쓴 규칙이 먼저 적용되므로 좁은 패턴을 앞에 둔다.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthSuccessHandler authSuccessHandler,
                                           AuthFailHandler authFailHandler,
                                           SessionRegistry sessionRegistry,
                                           RequestCache requestCache,
                                           SessionExpiredHandler sessionExpiredHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 강사 첫 로그인 비밀번호 변경은 /auth/** 공개 규칙보다 먼저 막아야 한다
                        .requestMatchers("/auth/instructor/**").hasRole("INSTRUCTOR")
                        // 공개: 홈·탐색·인증·가입·정적 리소스·오류 화면
                        .requestMatchers("/", "/privacy", "/hobbies/**", "/courses/**",
                                "/auth/**", "/signup/**",
                                "/css/**", "/js/**", "/images/**", "/error", "/error/**", "/favicon.ico").permitAll()
                        // 학습자
                        .requestMatchers("/my/account/**").authenticated()   // 계정 설정은 세 역할 공용(US-05·07)
                        .requestMatchers("/my/**", "/enrollments/**", "/lessons/**", "/videos/**",
                                "/assignments/**", "/inquiries/**").hasRole("USER")
                        // 로그인 사용자 전체 (세부 권한은 각 Policy에서)
                        .requestMatchers("/works/**", "/comments/**", "/files/works/**", "/notifications/**").authenticated()
                        .requestMatchers("/instructor/**").hasRole("INSTRUCTOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 그 밖의 URL은 로그인 필요 (비로그인 → 로그인 화면)
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .usernameParameter("loginId")   // templates/auth/login.html의 input name과 일치
                        .passwordParameter("password")
                        .successHandler(authSuccessHandler)  // #04: 역할별·첫 로그인 여부별 이동 (FR-003)
                        .failureHandler(authFailHandler)     // #04: 미인증·비활성·불일치 원인별 안내
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")       // POST만 허용
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                // 권한 없음 → 403 오류 화면 (필터에서 나는 예외는 @ControllerAdvice가 못 잡는다)
                // 비로그인 접근 → 로그인 필요 안내 화면 /error/401 (시안 E-401, "로그인하기" 버튼이 /auth/login으로 안내)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect(request.getContextPath() + "/error/401"))
                        .accessDeniedPage("/error/403"))
                // 로그인 필요 화면(E-401)으로 보내기 직전에 원래 요청을 세션에 저장한다 → 로그인 성공 후 AuthSuccessHandler가 그 화면으로 보낸다
                .requestCache(cache -> cache.requestCache(requestCache))
                // CSRF 보호는 Spring Security 기본값(켜짐). th:action 폼은 토큰이 자동 삽입되고,
                // fetch·sendBeacon(작품 좋아요, 재생 진도 보고)은 페이지 meta(_csrf)의 토큰을 보낸다.
                // #06 한 기기 로그인(U-S17·C-06): 같은 계정이 새로 로그인하면 기존 세션을 만료시킨다.
                // 기존 브라우저는 다음 요청에서 "다른 기기에서 로그인되어 로그아웃되었습니다" 안내 화면(E-DUP)으로 이동한다.
                // 무요청 1시간 후 만료는 application.yaml의 server.servlet.session.timeout: 60m가 맡는다.
                .sessionManagement(session -> session
                        // 시간 만료·서버 재시작 등으로 세션 쿠키가 더는 유효하지 않을 때 → 세션 만료 안내 화면(E-SESSION)
                        .invalidSessionUrl("/error/401-expired")
                        .maximumSessions(1)                       // 회원당 세션 1개 (AuthDetails.equals가 users.id로 비교)
                        .sessionRegistry(sessionRegistry)         // 강사 비활성화 때 이미 로그인한 세션을 끊으려고 같은 Registry를 공유
                        .maxSessionsPreventsLogin(false)          // 새 로그인을 막지 않고 이전 세션을 만료
                        // 끝난 세션의 다음 요청: 다른 기기 로그인 → E-DUP, 관리자가 비활성화한 강사 → 로그인 화면 "비활성화된 계정입니다"
                        .expiredSessionStrategy(sessionExpiredHandler));
        return http.build();
    }

    /**
     * 로그인 전에 보려던 화면 저장소 (E-401 "로그인하면 보던 화면으로 이어서 이동합니다").
     * 브라우저가 화면을 열 때(GET + Accept 에 text/html)만 저장한다 — 이미지·영상·fetch 요청까지 저장하면
     * 로그인 직후 그림 파일이나 JSON 주소로 이동해 버린다. Accept 가 "모든 형식"(MediaType.ALL)뿐인 요청도 화면 요청으로 보지 않는다.
     * 호출받음: filterChain(requestCache), AuthSuccessHandler(저장된 요청 꺼내기)
     */
    @Bean
    public RequestCache requestCache() {
        MediaTypeRequestMatcher htmlPage = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlPage.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(
                request -> "GET".equals(request.getMethod()),
                htmlPage));
        return cache;
    }

    /** 로그인 중인 세션 목록. maximumSessions와 InstructorAccountService(비활성화 시 세션 만료)가 함께 쓴다 */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 세션이 끝날 때(로그아웃·시간 만료) Security의 SessionRegistry에 알려 줘서 끝난 세션이 "로그인 중"으로 남지 않게 한다.
     * maximumSessions를 쓸 때 함께 등록하는 Bean이다(#06).
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
