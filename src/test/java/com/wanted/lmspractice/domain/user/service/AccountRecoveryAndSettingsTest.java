package com.wanted.lmspractice.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.auth.service.EmailVerificationService;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.repository.AuthTokenRepository;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] #06 계정 복구(아이디 찾기·비밀번호 재설정)·계정 설정(닉네임·비밀번호 변경) 규칙 테스트
 * [담당] 팀원 1 · #06 (이슈 테스트 항목: 정상 3종 + 예외 4종)
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재(시드 learner·learner2·newbie 사용, 비밀번호 1234). 각 테스트는 롤백된다.
 *       SMTP가 없으면 메일 대신 로그만 남으므로 테스트 중 메일이 나가지 않는다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class AccountRecoveryAndSettingsTest {

    private static final String SEED_PASSWORD = "1234";
    private static final String NEW_PASSWORD = "NewPassw0rd!";

    @Autowired
    private UserService userService;
    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthTokenRepository authTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User seedUser(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    private boolean hasUnusedToken(Long userId, TokenPurpose purpose) {
        return !authTokenRepository.findByUserIdAndPurposeAndConsumedAtIsNull(userId, purpose).isEmpty();
    }

    // ───────────── 계정 복구 ─────────────

    @Test
    @DisplayName("아이디 찾기: 가입 이메일로 요청하면 토큰이 발급되고, 링크 토큰으로 아이디를 한 번만 확인할 수 있다")
    void recoverLoginId() {
        User learner = seedUser("learner");
        emailVerificationService.sendRecovery("LEARNER@nolibang.local", TokenPurpose.RECOVER_LOGIN_ID);  // 대소문자 무시
        assertThat(hasUnusedToken(learner.getId(), TokenPurpose.RECOVER_LOGIN_ID)).isTrue();

        String token = emailVerificationService.issueToken(learner.getId(), TokenPurpose.RECOVER_LOGIN_ID, Duration.ofHours(1));
        assertThat(emailVerificationService.recoverLoginId(token)).isEqualTo("learner");

        assertThatThrownBy(() -> emailVerificationService.recoverLoginId(token))  // 재사용 토큰
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("미가입·미인증 이메일은 예외 없이 끝나고(존재 숨김) 토큰을 만들지 않는다")
    void recoveryForUnknownOrUnverifiedEmail() {
        emailVerificationService.sendRecovery("nobody@nolibang.local", TokenPurpose.RESET_PASSWORD);

        User newbie = seedUser("newbie");  // PENDING_EMAIL — 소유 이메일 인증을 안 한 계정
        emailVerificationService.sendRecovery(newbie.getEmail(), TokenPurpose.RESET_PASSWORD);
        assertThat(hasUnusedToken(newbie.getId(), TokenPurpose.RESET_PASSWORD)).isFalse();
    }

    @Test
    @DisplayName("복구 화면에서는 가입 인증(VERIFY_EMAIL) 토큰을 만들 수 없다")
    void recoveryRejectsVerifyPurpose() {
        assertThatThrownBy(() -> emailVerificationService.sendRecovery("learner@nolibang.local", TokenPurpose.VERIFY_EMAIL))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("비밀번호 재설정 후 새 비밀번호로만 로그인할 수 있고, 같은 링크는 다시 쓸 수 없다")
    void resetPassword() {
        User learner = seedUser("learner");
        String token = emailVerificationService.issueToken(learner.getId(), TokenPurpose.RESET_PASSWORD, Duration.ofHours(1));
        emailVerificationService.checkToken(token, TokenPurpose.RESET_PASSWORD);  // 폼 열기만으로는 소모되지 않는다

        userService.resetPassword(token, NEW_PASSWORD);

        String hash = seedUser("learner").getPasswordHash();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
        assertThat(passwordEncoder.matches(SEED_PASSWORD, hash)).isFalse();
        assertThatThrownBy(() -> userService.resetPassword(token, "Another1!"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("가입 인증 토큰과 재설정·아이디 찾기 토큰은 서로 쓸 수 없다 (DEV_REVIEW §2-12)")
    void tokenPurposesAreSeparated() {
        Long userId = seedUser("learner").getId();
        String verifyToken = emailVerificationService.issueToken(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(1));
        String resetToken = emailVerificationService.issueToken(userId, TokenPurpose.RESET_PASSWORD, Duration.ofHours(1));

        assertThatThrownBy(() -> userService.resetPassword(verifyToken, NEW_PASSWORD))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
        assertThatThrownBy(() -> emailVerificationService.recoverLoginId(resetToken))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
        assertThatThrownBy(() -> emailVerificationService.verify(resetToken))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("만료된 재설정 토큰은 TOKEN_EXPIRED")
    void expiredResetToken() throws InterruptedException {
        Long userId = seedUser("learner").getId();
        // DB CHECK(expires_at > created_at) 때문에 지난 시각으로 저장할 수 없어 1초짜리를 만들고 기다린다(#04 테스트와 같은 방식)
        String token = emailVerificationService.issueToken(userId, TokenPurpose.RESET_PASSWORD, Duration.ofSeconds(1));
        Thread.sleep(1500);

        assertThatThrownBy(() -> userService.resetPassword(token, NEW_PASSWORD))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    // ───────────── 계정 설정 ─────────────

    @Test
    @DisplayName("닉네임 변경: 앞뒤 공백을 지워 저장하고, 다른 회원 닉네임이면 DUPLICATE_NICKNAME")
    void changeNickname() {
        Long userId = seedUser("learner").getId();

        assertThat(userService.changeNickname(userId, "  새닉네임 ")).isEqualTo("새닉네임");
        assertThat(seedUser("learner").getNickname()).isEqualTo("새닉네임");

        String otherNickname = seedUser("learner2").getNickname();
        assertThatThrownBy(() -> userService.changeNickname(userId, otherNickname))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DUPLICATE_NICKNAME));
        assertThat(userService.isNicknameAvailableFor(userId, "새닉네임")).isTrue();  // 내 닉네임은 사용 가능으로 본다
    }

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호가 틀리면 PASSWORD_MISMATCH, 현재와 같으면 INVALID_NEW_PASSWORD")
    void changePassword() {
        Long userId = seedUser("learner").getId();

        assertThatThrownBy(() -> userService.changePassword(userId, "wrong", NEW_PASSWORD))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_MISMATCH));
        assertThatThrownBy(() -> userService.changePassword(userId, SEED_PASSWORD, SEED_PASSWORD))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_NEW_PASSWORD));

        userService.changePassword(userId, SEED_PASSWORD, NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, seedUser("learner").getPasswordHash())).isTrue();
    }
}
