package com.wanted.lmspractice.domain.review.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.review.dto.AssignmentState;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] SubmissionPolicy 테스트 — 3차시 완료·수강 기간·진행 중 평가 1건·평가 중 수정 잠금·재제출 조건·이미지 1~5장,
 *        그리고 같은 기준으로 정하는 화면 상태(AssignmentState.decide)
 * [담당] 팀원 3 · #13
 * [전제] 3차시 완료(C10)는 Mockito 가짜 ProgressService로 대신한다.
 */
class SubmissionPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

    private ProgressService progressService;
    private SubmissionPolicy policy;

    @BeforeEach
    void setUp() {
        progressService = mock(ProgressService.class);
        policy = new SubmissionPolicy(progressService);
        when(progressService.isAllCompleted(10L)).thenReturn(true);
    }

    private Enrollment enrollment(EnrollmentStatus status, LocalDateTime expiresAt) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "userId", 2L);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        ReflectionTestUtils.setField(e, "confirmedAt", expiresAt.minusDays(30));
        ReflectionTestUtils.setField(e, "expiresAt", expiresAt);
        return e;
    }

    private Enrollment open() {
        return enrollment(EnrollmentStatus.ACTIVE, NOW.plusDays(10));
    }

    private ReviewRound round(ReviewStatus status, int roundNo) {
        ReviewRound r = ReviewRound.create(10L, 100L + roundNo, roundNo, NOW.minusDays(3));
        ReflectionTestUtils.setField(r, "id", (long) roundNo);
        ReflectionTestUtils.setField(r, "status", status);
        return r;
    }

    private void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("3차시 완료·기간 안·이미지 1~5장이면 최초 제출할 수 있다")
    void submitAllowed() {
        assertThatCode(() -> policy.checkCanSubmit(open(), List.of(), 1, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkCanSubmit(open(), List.of(), 5, NOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("3차시를 다 끝내지 않으면 제출을 막는다")
    void submitBlockedWhenLessonsNotCompleted() {
        when(progressService.isAllCompleted(10L)).thenReturn(false);
        assertError(() -> policy.checkCanSubmit(open(), List.of(), 1, NOW), ErrorCode.LESSONS_NOT_COMPLETED);
    }

    @Test
    @DisplayName("이미지 0장·6장은 INVALID_IMAGE_COUNT")
    void submitImageCount() {
        assertError(() -> policy.checkCanSubmit(open(), List.of(), 0, NOW), ErrorCode.INVALID_IMAGE_COUNT);
        assertError(() -> policy.checkCanSubmit(open(), List.of(), 6, NOW), ErrorCode.INVALID_IMAGE_COUNT);
    }

    @Test
    @DisplayName("수강 기간이 끝났거나 수강 중이 아니면 제출할 수 없다")
    void submitBlockedWhenExpired() {
        Enrollment expired = enrollment(EnrollmentStatus.ACTIVE, NOW.minusSeconds(1));
        Enrollment cancelled = enrollment(EnrollmentStatus.CANCELLED, NOW.plusDays(10));
        assertError(() -> policy.checkCanSubmit(expired, List.of(), 1, NOW), ErrorCode.ENROLLMENT_EXPIRED);
        assertError(() -> policy.checkCanSubmit(cancelled, List.of(), 1, NOW), ErrorCode.ENROLLMENT_EXPIRED);
    }

    @Test
    @DisplayName("평가 대기·진행 중 회차가 있으면 다시 제출할 수 없다(연타 두 번째 요청 포함)")
    void submitBlockedWhenActiveRoundExists() {
        assertError(() -> policy.checkCanSubmit(open(), List.of(round(ReviewStatus.WAITING, 1)), 1, NOW),
                ErrorCode.ALREADY_SUBMITTED);
        assertError(() -> policy.checkCanSubmit(open(), List.of(round(ReviewStatus.IN_REVIEW, 1)), 1, NOW),
                ErrorCode.ALREADY_SUBMITTED);
    }

    @Test
    @DisplayName("반려 뒤에는 최초 제출이 아니라 재제출로만 낼 수 있다")
    void submitBlockedAfterRejection() {
        assertError(() -> policy.checkCanSubmit(open(), List.of(round(ReviewStatus.REJECTED, 1)), 1, NOW),
                ErrorCode.INVALID_REVIEW_STATE);
    }

    @Test
    @DisplayName("직전 회차가 반려이고 기간 안이면 재제출할 수 있다 — 횟수 제한 없음")
    void resubmitAllowed() {
        List<ReviewRound> rounds = List.of(round(ReviewStatus.REJECTED, 3), round(ReviewStatus.REJECTED, 2),
                round(ReviewStatus.REJECTED, 1));
        assertThatCode(() -> policy.checkCanResubmit(open(), rounds, 2, NOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("직전 회차가 반려가 아니면 RESUBMIT_NOT_ALLOWED, 진행 중이면 ALREADY_SUBMITTED")
    void resubmitBlocked() {
        assertError(() -> policy.checkCanResubmit(open(), List.of(), 1, NOW), ErrorCode.RESUBMIT_NOT_ALLOWED);
        assertError(() -> policy.checkCanResubmit(open(), List.of(round(ReviewStatus.APPROVED, 1)), 1, NOW),
                ErrorCode.RESUBMIT_NOT_ALLOWED);
        assertError(() -> policy.checkCanResubmit(open(), List.of(round(ReviewStatus.WAITING, 2),
                round(ReviewStatus.REJECTED, 1)), 1, NOW), ErrorCode.ALREADY_SUBMITTED);
    }

    @Test
    @DisplayName("기간이 끝난 뒤 반려는 재제출할 수 없다")
    void resubmitBlockedWhenExpired() {
        Enrollment expired = enrollment(EnrollmentStatus.ACTIVE, NOW.minusDays(1));
        assertError(() -> policy.checkCanResubmit(expired, List.of(round(ReviewStatus.REJECTED, 1)), 1, NOW),
                ErrorCode.ENROLLMENT_EXPIRED);
    }

    @Test
    @DisplayName("평가 대기면 수정할 수 있고, 이미지 0장은 기존 유지로 허용한다")
    void editAllowedWhileWaiting() {
        assertThatCode(() -> policy.checkCanEdit(open(), round(ReviewStatus.WAITING, 1), 0, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkCanEdit(open(), round(ReviewStatus.WAITING, 1), 3, NOW)).doesNotThrowAnyException();
        assertError(() -> policy.checkCanEdit(open(), round(ReviewStatus.WAITING, 1), 6, NOW), ErrorCode.INVALID_IMAGE_COUNT);
    }

    @Test
    @DisplayName("평가가 시작되면 수정이 잠긴다(SUBMISSION_LOCKED), 결과가 확정된 회차는 INVALID_REVIEW_STATE")
    void editLocked() {
        assertError(() -> policy.checkCanEdit(open(), round(ReviewStatus.IN_REVIEW, 1), 0, NOW), ErrorCode.SUBMISSION_LOCKED);
        assertError(() -> policy.checkCanEdit(open(), round(ReviewStatus.APPROVED, 1), 0, NOW), ErrorCode.INVALID_REVIEW_STATE);
    }

    @Test
    @DisplayName("화면 상태: 평가가 걸린 회차는 기간과 관계없이 그 상태, 기간이 닫히면 미제출·반려는 제출 기간 종료")
    void decideState() {
        assertThat(AssignmentState.decide(null, true, false)).isEqualTo(AssignmentState.LOCKED_LESSONS);
        assertThat(AssignmentState.decide(null, true, true)).isEqualTo(AssignmentState.NOT_SUBMITTED);
        assertThat(AssignmentState.decide(ReviewStatus.WAITING, false, true)).isEqualTo(AssignmentState.WAITING);
        assertThat(AssignmentState.decide(ReviewStatus.IN_REVIEW, true, true)).isEqualTo(AssignmentState.IN_REVIEW);
        assertThat(AssignmentState.decide(ReviewStatus.APPROVED, false, true)).isEqualTo(AssignmentState.APPROVED);
        assertThat(AssignmentState.decide(ReviewStatus.REJECTED, true, true)).isEqualTo(AssignmentState.REJECTED);
        assertThat(AssignmentState.decide(ReviewStatus.REJECTED, false, true)).isEqualTo(AssignmentState.EXPIRED);
        assertThat(AssignmentState.decide(null, false, true)).isEqualTo(AssignmentState.EXPIRED);
    }
}
