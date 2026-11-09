package com.wanted.lmspractice.domain.learning.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] VideoAccessPolicy 테스트 — 기간 내 수강·수료자 허용, 미수료 만료자·취소·다른 강의 차시 거부 (D-42)
 * [담당] 팀원 3 · #12
 */
class VideoAccessPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);
    private final VideoAccessPolicy policy = new VideoAccessPolicy();

    private Enrollment enrollment(EnrollmentStatus status, LocalDateTime confirmedAt, LocalDateTime expiresAt) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        ReflectionTestUtils.setField(e, "confirmedAt", confirmedAt);
        ReflectionTestUtils.setField(e, "expiresAt", expiresAt);
        return e;
    }

    @Test
    @DisplayName("수강 기간 안의 학습 중 수강은 영상을 볼 수 있다")
    void activeWithinPeriodAllowed() {
        Enrollment e = enrollment(EnrollmentStatus.ACTIVE, NOW.minusDays(5), NOW.plusDays(25));
        assertThatCode(() -> policy.checkVideoAccess(e, 1L, NOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수료자는 수강 기간이 끝나도 영상을 볼 수 있다")
    void completedAllowedAfterPeriod() {
        Enrollment e = enrollment(EnrollmentStatus.COMPLETED, NOW.minusDays(60), NOW.minusDays(30));
        assertThatCode(() -> policy.checkVideoAccess(e, 1L, NOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기간이 끝난 미수료자(평가 중이라 ACTIVE로 남은 경우 포함)는 거부된다")
    void expiredNotCompletedDenied() {
        Enrollment active = enrollment(EnrollmentStatus.ACTIVE, NOW.minusDays(40), NOW.minusDays(10));
        Enrollment expired = enrollment(EnrollmentStatus.EXPIRED, NOW.minusDays(40), NOW.minusDays(10));
        assertThatThrownBy(() -> policy.checkVideoAccess(active, 1L, NOW)).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.VIDEO_ACCESS_DENIED);
        assertThatThrownBy(() -> policy.checkVideoAccess(expired, 1L, NOW)).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.VIDEO_ACCESS_DENIED);
    }

    @Test
    @DisplayName("취소한 수강은 기간 안이어도 거부된다")
    void cancelledDenied() {
        Enrollment e = enrollment(EnrollmentStatus.CANCELLED, NOW.minusDays(5), NOW.plusDays(25));
        assertThatThrownBy(() -> policy.checkVideoAccess(e, 1L, NOW)).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.VIDEO_ACCESS_DENIED);
    }

    @Test
    @DisplayName("수강한 강의가 아닌 차시는 존재를 숨기고 LESSON_NOT_FOUND")
    void otherCourseLessonHidden() {
        Enrollment e = enrollment(EnrollmentStatus.ACTIVE, NOW.minusDays(5), NOW.plusDays(25));
        assertThatThrownBy(() -> policy.checkVideoAccess(e, 2L, NOW)).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.LESSON_NOT_FOUND);
    }
}
