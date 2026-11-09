package com.wanted.lmspractice.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.user.dto.SignupDTO;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 이메일 인증 토큰 규칙 테스트 — 인증 성공, 1회 사용, 만료, 목적 불일치, 재발송 시 이전 링크 무효화
 * [담당] 팀원 1 · #04   [전제] UserServiceTest와 같음(로컬 MySQL에 시드 적재, 테스트는 롤백)
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class EmailVerificationServiceTest {

    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    private Long signupUser() {
        SignupDTO dto = new SignupDTO();
        dto.setLoginId("verifytest1");
        dto.setPassword("Passw0rd!");
        dto.setPasswordConfirm("Passw0rd!");
        dto.setName("김인증");
        dto.setEmail("verifytest1@example.com");
        return userService.signup(dto);
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("올바른 토큰으로 인증하면 ACTIVE가 되고, 같은 토큰은 다시 쓸 수 없다")
    void verifyOnceOnly() {
        Long userId = signupUser();
        String token = emailVerificationService.issueToken(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(1));

        emailVerificationService.verify(token);

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("없는 토큰·빈 토큰은 INVALID_TOKEN")
    void unknownToken() {
        assertThatThrownBy(() -> emailVerificationService.verify("no-such-token"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
        assertThatThrownBy(() -> emailVerificationService.verify(null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("만료된 토큰은 TOKEN_EXPIRED")
    void expiredToken() throws InterruptedException {
        Long userId = signupUser();
        // DB CHECK(expires_at > created_at) 때문에 이미 지난 시각으로는 저장할 수 없어서 1초짜리를 만들고 기다린다
        String token = emailVerificationService.issueToken(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofSeconds(1));
        Thread.sleep(1500);

        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("다른 목적(비밀번호 재설정)의 토큰으로는 이메일 인증을 못 한다")
    void wrongPurpose() {
        Long userId = signupUser();
        String resetToken = emailVerificationService.issueToken(userId, TokenPurpose.RESET_PASSWORD, Duration.ofHours(1));

        assertThatThrownBy(() -> emailVerificationService.verify(resetToken))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("재발송하면 이전 링크는 무효가 되고 새 링크만 쓸 수 있다")
    void resendInvalidatesOldToken() {
        Long userId = signupUser();
        String oldToken = emailVerificationService.issueToken(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(1));
        String newToken = emailVerificationService.issueToken(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(1));

        assertThatThrownBy(() -> emailVerificationService.verify(oldToken))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
        emailVerificationService.verify(newToken);
        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("없는 이메일·이미 인증된 이메일로 재발송해도 예외 없이 끝난다(계정 존재 여부를 숨김)")
    void resendHidesAccountExistence() {
        emailVerificationService.resend("nobody-here@example.com");
        emailVerificationService.resend("learner@nolibang.local");  // 시드: 이미 인증된 계정
        emailVerificationService.resend("");
    }

    @Test
    @DisplayName("이메일은 일부만 가려서 보여 준다")
    void maskEmail() {
        assertThat(EmailVerificationService.maskEmail("abcdef@gmail.com")).isEqualTo("ab***@gmail.com");
        assertThat(EmailVerificationService.maskEmail("a@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(EmailVerificationService.maskEmail(null)).isNull();
    }
}
