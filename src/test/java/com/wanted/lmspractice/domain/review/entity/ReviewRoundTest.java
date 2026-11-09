package com.wanted.lmspractice.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] ReviewRound.create 테스트 — 평가 대기로 시작, 마감 = 제출 + 7일(DB CHECK), 다음 회차 번호
 * [담당] 팀원 3 · #13
 */
class ReviewRoundTest {

    @Test
    @DisplayName("새 회차는 평가 대기로 시작하고 마감은 제출 시각 + 7일이다")
    void createWaitingWithSevenDayDue() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 11, 9, 14, 0, 0, 123_456_789);

        ReviewRound round = ReviewRound.create(10L, 100L, 1, submittedAt);

        assertThat(round.getStatus()).isEqualTo(ReviewStatus.WAITING);
        assertThat(round.isActive()).isTrue();
        assertThat(round.getRoundNo()).isEqualTo(1);
        assertThat(round.getWorkVersionId()).isEqualTo(100L);
        // DATETIME(6)에 맞춰 마이크로초까지만 남긴다 — 제출·마감이 같은 소수점이어야 DB CHECK를 통과한다
        assertThat(round.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 14, 0, 0, 123_456_000));
        assertThat(round.getDueAt()).isEqualTo(round.getSubmittedAt().plusDays(ReviewRound.REVIEW_DUE_DAYS));
    }

    @Test
    @DisplayName("재제출 회차 번호는 직전 회차 + 1이다")
    void nextRoundNo() {
        ReviewRound round = ReviewRound.create(10L, 100L, 2, LocalDateTime.now());
        assertThat(round.nextRoundNo()).isEqualTo(3);
    }
}
