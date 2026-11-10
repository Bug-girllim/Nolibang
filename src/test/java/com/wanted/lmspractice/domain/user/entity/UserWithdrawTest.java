package com.wanted.lmspractice.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] User.withdraw·releaseEmailIfExpired 상태 전이 테스트 (DB 없이 엔티티만 확인)
 * [담당] 팀원 1 · #06
 */
class UserWithdrawTest {

    private User activeLearner() {
        User user = User.createLearner("learner1", "hash", "김학습", "learner@example.com", "닉네임");
        user.verifyEmail(LocalDateTime.now());
        return user;
    }

    @Test
    @DisplayName("탈퇴하면 WITHDRAWN이 되고 아이디·실명·닉네임·비밀번호가 바뀐다. 이메일은 재가입 제한 확인용으로 남는다")
    void withdraw_anonymizes() {
        User user = activeLearner();
        LocalDateTime now = LocalDateTime.now();

        user.withdraw(now, "unusable");

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isEqualTo(now);
        assertThat(user.getRealName()).isEqualTo("탈퇴회원");
        assertThat(user.getLoginId()).isNotEqualTo("learner1");
        assertThat(user.getNickname()).startsWith("withdrawn_");
        assertThat(user.getPasswordHash()).isEqualTo("unusable");
        assertThat(user.getEmail()).isEqualTo("learner@example.com");
    }

    @Test
    @DisplayName("이미 탈퇴했거나 미인증인 계정은 탈퇴 처리할 수 없다")
    void withdraw_rejectsInvalidState() {
        User pending = User.createLearner("l2", "hash", "이름", "l2@example.com", "닉2");
        assertThatThrownBy(() -> pending.withdraw(LocalDateTime.now(), "x")).isInstanceOf(BusinessException.class);

        User user = activeLearner();
        user.withdraw(LocalDateTime.now(), "x");
        assertThatThrownBy(() -> user.withdraw(LocalDateTime.now(), "x")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("강사 계정은 탈퇴할 수 없다")
    void withdraw_rejectsInstructor() {
        User instructor = User.createInstructor("t1", "hash", "강사", "t1@example.com");
        assertThatThrownBy(() -> instructor.withdraw(LocalDateTime.now(), "x")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("탈퇴 30일 전에는 이메일을 풀지 않고, 30일이 지나면 푼다")
    void releaseEmail_after30Days() {
        User user = activeLearner();
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 10, 1, 0, 0);
        user.withdraw(withdrawnAt, "x");

        assertThat(user.releaseEmailIfExpired(withdrawnAt.plusDays(29))).isFalse();
        assertThat(user.getEmail()).isEqualTo("learner@example.com");

        assertThat(user.releaseEmailIfExpired(withdrawnAt.plusDays(30))).isTrue();
        assertThat(user.getEmail()).endsWith("@withdrawn.invalid");
    }
}
