package com.wanted.lmspractice.global.config;

import com.wanted.lmspractice.domain.auth.handler.AuthFailHandler;
import com.wanted.lmspractice.domain.auth.handler.AuthSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * [역할] URL별 접근 권한, formLogin, logout, BCrypt PasswordEncoder 등록
 * [담당] 팀원 1 · #02   [API] §1-3, AU-02, AU-03
 * [연결]
 *  - 호출받음: Spring Security 필터 체인(모든 요청)
 *  - 호출함:   AuthService(UserDetailsService Bean을 자동 사용), 오류 화면 /error/403(MainController)
 *  - 연결됨:   #04 AuthSuccessHandler·AuthFailHandler (formLogin)
 *  - 확장 예정: #06 maximumSessions(1)
 *  - 규칙:     다른 팀원은 이 파일을 직접 고치지 않고 팀원 1에게 요청한다
 * [기술] 🟢 SecurityFilterChain, formLogin, BCrypt
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
                                           AuthFailHandler authFailHandler) throws Exception {
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
                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
                // ❓ API 명세 §1-5: CSRF 결정 전까지 수업 방식(disable). 켜면 fetch(JSON) 요청에 토큰 헤더 필요
                .csrf(csrf -> csrf.disable());
        // TODO #06: sessionManagement().maximumSessions(1)
        return http.build();
    }
}
