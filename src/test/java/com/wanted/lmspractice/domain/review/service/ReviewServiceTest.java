package com.wanted.lmspractice.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 평가 상태 전이(ReviewRound)·권한·승인/반려 후 수료·자리 반환 테스트 (이슈 #14 완료 조건·테스트 항목)
 * [담당] 팀원 3 · #14   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 각 테스트는 끝나면 롤백된다(@Transactional).
 * [시드] teacherA(6) = 분반 5 담당(강의 4) / teacherB(7) = 분반 9 담당(강의 7)
 *       r3 = e4(learner, 강의 7, 분반 9) WAITING / r4 = e5(learner2, 강의 4, 분반 5) IN_REVIEW + 초안, 마감 1일 남음
 *       강의 4는 정원 1 만석 + 대기 1번 learner3(w2) → 승인·만료 후 반려로 자리가 나면 w2가 자동 확정된다
 * [한계] 하나의 트랜잭션 안에서 도는 테스트라 "브라우저 2개로 동시에 승인"은 확인하지 못한다 → 연속 2회 승인으로 대신하고 PR에 수동 테스트 결과를 적는다.
 *       엔티티 create()가 #13 몫이라 엔티티 단독 테스트 대신 시드 행으로 상태 전이를 확인한다.
 *       알림 레코드 생성은 NotificationService(#17) 스텁이라 #17 merge 후 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class ReviewServiceTest {

    private static final long TEACHER_A = 6L, TEACHER_B = 7L;
    private static final long R3_WAITING = 3L, R4_IN_REVIEW = 4L;
    private static final long E4 = 4L, E5 = 5L, W2 = 2L;
    private static final String TEXT_50 = "가".repeat(50);
    private static final String TEXT_49 = "가".repeat(49);

    @Autowired private ReviewService reviewService;
    @Autowired private ReviewQueryService reviewQueryService;
    @Autowired private ReviewRoundRepository reviewRoundRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    /** 시드를 테스트 상황에 맞게 바꾼다(롤백됨). 엔티티를 불러오기 전에만 쓴다 */
    private void sql(String statement) {
        entityManager.createNativeQuery(statement).executeUpdate();
    }

    /** 변경을 DB에 보내 CHECK 제약을 통과하는지 확인하고, 다음 조회가 DB 최신 값을 읽게 한다 */
    private ReviewRound reload(long id) {
        entityManager.flush();
        entityManager.clear();
        return reviewRoundRepository.findById(id).orElseThrow();
    }

    /** r4를 "지금 기준 6일 전 제출 · 1일 전 시작 → 마감 1일 남음(임박)"으로 맞춘다 — 시드 적재 시점과 무관하게 같은 상황을 만든다 */
    private void makeR4DueSoon() {
        sql("UPDATE review_rounds SET submitted_at = UTC_TIMESTAMP(6) - INTERVAL 6 DAY, "
                + "due_at = UTC_TIMESTAMP(6) - INTERVAL 6 DAY + INTERVAL 7 DAY, started_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY, "
                + "draft_updated_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY WHERE id = 4");
    }

    private ReviewDecisionDTO decision(String content) {
        return new ReviewDecisionDTO(content);
    }

    // ---------------------------------------------------------------
    // 평가 시작·시작 취소·초안
    // ---------------------------------------------------------------

    @Test
    @DisplayName("평가 시작: WAITING → IN_REVIEW, 시작 강사·시각 기록, 여전히 평가 대기·진행 중(수강 취소 불가)")
    void startLocksSubmission() {
        reviewService.start(TEACHER_B, R3_WAITING);

        ReviewRound round = reload(R3_WAITING);
        assertThat(round.getStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(round.getStartedBy()).isEqualTo(TEACHER_B);
        assertThat(round.getStartedAt()).isNotNull();
        assertThat(reviewQueryService.hasPendingReview(E4)).isTrue();
    }

    @Test
    @DisplayName("다른 분반 강사는 열람·시작 불가(NOT_ASSIGNED_INSTRUCTOR), 없는 회차는 REVIEW_NOT_FOUND")
    void otherInstructorCannotReview() {
        assertThatThrownBy(() -> reviewService.start(TEACHER_A, R3_WAITING))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThatThrownBy(() -> reviewService.getDetail(TEACHER_A, R3_WAITING))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThatThrownBy(() -> reviewService.getDetail(TEACHER_A, 9999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 시작한 평가는 다시 시작할 수 없다(INVALID_REVIEW_STATE)")
    void cannotStartTwice() {
        assertThatThrownBy(() -> reviewService.start(TEACHER_A, R4_IN_REVIEW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    @Test
    @DisplayName("초안이 있으면 시작 취소 불가(CANNOT_CANCEL_START)")
    void cannotCancelStartWithDraft() {
        assertThatThrownBy(() -> reviewService.cancelStart(TEACHER_A, R4_IN_REVIEW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANNOT_CANCEL_START));
    }

    @Test
    @DisplayName("시작 취소: 초안이 없으면 WAITING으로 돌아가고 마감은 그대로(D-56)")
    void cancelStartKeepsDueAt() {
        LocalDateTime dueBefore = reviewRoundRepository.findById(R3_WAITING).orElseThrow().getDueAt();
        entityManager.clear();

        reviewService.start(TEACHER_B, R3_WAITING);
        reload(R3_WAITING);
        reviewService.cancelStart(TEACHER_B, R3_WAITING);

        ReviewRound round = reload(R3_WAITING);
        assertThat(round.getStatus()).isEqualTo(ReviewStatus.WAITING);
        assertThat(round.getStartedAt()).isNull();
        assertThat(round.getStartedBy()).isNull();
        assertThat(round.getDueAt()).isEqualTo(dueBefore);
    }

    @Test
    @DisplayName("초안 저장은 진행 중일 때만, 비운 초안은 '초안 없음'이라 다시 시작 취소 가능")
    void draftRules() {
        assertThatThrownBy(() -> reviewService.saveDraft(TEACHER_B, R3_WAITING, "초안"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));

        reviewService.saveDraft(TEACHER_A, R4_IN_REVIEW, "   ");
        assertThat(reload(R4_IN_REVIEW).hasDraft()).isFalse();

        reviewService.cancelStart(TEACHER_A, R4_IN_REVIEW);
        assertThat(reload(R4_IN_REVIEW).getStatus()).isEqualTo(ReviewStatus.WAITING);
    }

    // ---------------------------------------------------------------
    // 승인·반려
    // ---------------------------------------------------------------

    @Test
    @DisplayName("49자 피드백·반려 사유는 거부(FEEDBACK_TOO_SHORT), 상태는 진행 중 그대로")
    void feedbackTooShort() {
        assertThatThrownBy(() -> reviewService.approve(TEACHER_A, R4_IN_REVIEW, decision(TEXT_49)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FEEDBACK_TOO_SHORT));
        assertThatThrownBy(() -> reviewService.reject(TEACHER_A, R4_IN_REVIEW, decision(" " + TEXT_49 + " ")))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FEEDBACK_TOO_SHORT));
        assertThat(reload(R4_IN_REVIEW).getStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("X-01 5~6단계: 50자 승인 → 수료 + 자리 반환으로 대기 1순위 자동 확정, 재승인은 거부(수료 1회)")
    void approveCompletesAndReleasesSeat() {
        reviewService.approve(TEACHER_A, R4_IN_REVIEW, decision(TEXT_50));

        ReviewRound round = reload(R4_IN_REVIEW);
        assertThat(round.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(round.getReviewerId()).isEqualTo(TEACHER_A);
        assertThat(round.getFeedbackBody()).isEqualTo(TEXT_50);
        assertThat(enrollmentRepository.findById(E5).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(waitlistEntryRepository.findById(W2).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.CONFIRMED);
        assertThat(reviewQueryService.getLatestStatus(E5).getStatus()).isEqualTo(ReviewStatus.APPROVED);

        assertThatThrownBy(() -> reviewService.approve(TEACHER_A, R4_IN_REVIEW, decision(TEXT_50)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    @Test
    @DisplayName("수강 기간 안 반려: REJECTED, 수강은 ACTIVE 유지(재제출 가능), 평가 대기·진행 아님")
    void rejectWithinPeriodKeepsEnrollment() {
        reviewService.reject(TEACHER_A, R4_IN_REVIEW, decision(TEXT_50));

        assertThat(reload(R4_IN_REVIEW).getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(enrollmentRepository.findById(E5).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(waitlistEntryRepository.findById(W2).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(reviewQueryService.hasPendingReview(E5)).isFalse();
    }

    @Test
    @DisplayName("만료 후에도 평가 가능 — 만료 후 반려면 수강 종료(EXPIRED) + 자리 반환으로 대기 1순위 확정")
    void rejectAfterExpiryClosesEnrollment() {
        sql("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 31 DAY, "
                + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY WHERE id = 5");

        reviewService.reject(TEACHER_A, R4_IN_REVIEW, decision(TEXT_50));

        assertThat(reload(R4_IN_REVIEW).getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(enrollmentRepository.findById(E5).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(waitlistEntryRepository.findById(W2).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.CONFIRMED);
    }

    @Test
    @DisplayName("담당 변경: 이전 강사는 평가 불가, 새 강사가 초안·잠금·마감을 그대로 이어받아 승인")
    void newInstructorInheritsReview() {
        makeR4DueSoon();
        sql("UPDATE course_sections SET instructor_id = 7 WHERE id = 5");

        assertThatThrownBy(() -> reviewService.approve(TEACHER_A, R4_IN_REVIEW, decision(TEXT_50)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        entityManager.clear();

        InstructorReviewDetailDTO detail = reviewService.getDetail(TEACHER_B, R4_IN_REVIEW);
        assertThat(detail.getStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(detail.getDraftBody()).startsWith("레이어 구분이 깔끔합니다.");
        assertThat(detail.isDueSoon()).isTrue();

        reviewService.approve(TEACHER_B, R4_IN_REVIEW, decision(TEXT_50));
        assertThat(reload(R4_IN_REVIEW).getReviewerId()).isEqualTo(TEACHER_B);
    }

    // ---------------------------------------------------------------
    // 목록·계약 C11
    // ---------------------------------------------------------------

    @Test
    @DisplayName("목록: 지금 담당하는 분반의 평가만, 마감 임박(48h)·기한 초과 표시와 기한 필터")
    void listShowsDueWarnings() {
        makeR4DueSoon();
        sql("UPDATE review_rounds SET submitted_at = UTC_TIMESTAMP(6) - INTERVAL 9 DAY, "
                + "due_at = UTC_TIMESTAMP(6) - INTERVAL 2 DAY WHERE id = 3");

        ReviewListPageDTO teacherA = reviewService.getReviewPage(TEACHER_A, ReviewTab.IN_REVIEW, null, null);
        assertThat(teacherA.getReviews()).extracting(ReviewListDTO::getReviewRoundId).contains(R4_IN_REVIEW).doesNotContain(R3_WAITING);
        ReviewListDTO r4 = teacherA.getReviews().stream().filter(r -> r.getReviewRoundId() == R4_IN_REVIEW).findFirst().orElseThrow();
        assertThat(r4.isDueSoon()).isTrue();
        assertThat(r4.isOverdue()).isFalse();

        ReviewListPageDTO overdue = reviewService.getReviewPage(TEACHER_B, ReviewTab.WAITING, null, ReviewListPageDTO.DUE_OVERDUE);
        assertThat(overdue.getReviews()).extracting(ReviewListDTO::getReviewRoundId).containsExactly(R3_WAITING);
        assertThat(overdue.getReviews().get(0).getRemainingText()).startsWith("초과 2일");
        assertThat(reviewService.getReviewPage(TEACHER_B, ReviewTab.WAITING, null, ReviewListPageDTO.DUE_SOON).getReviews()).isEmpty();
    }

    @Test
    @DisplayName("계약 C11 hasPendingReview 연결: 평가 대기·진행 중 수강 취소는 거부(CANCEL_NOT_ALLOWED_DURING_REVIEW)")
    void cannotCancelEnrollmentDuringReview() {
        assertThatThrownBy(() -> enrollmentService.cancel(3L, E5))   // learner2의 e5 = r4 진행 중
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED_DURING_REVIEW));
        assertThatThrownBy(() -> enrollmentService.cancel(2L, E4))   // learner의 e4 = r3 대기
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED_DURING_REVIEW));
    }

    @Test
    @DisplayName("계약 C11 getLatestStatus: 제출 없으면 미제출, 있으면 최신 회차 상태와 최초 제출 시각")
    void latestStatus() {
        assertThat(reviewQueryService.getLatestStatus(6L).isSubmitted()).isFalse();   // e6 학습 시작 전
        assertThat(reviewQueryService.getLatestStatus(3L).getStatus()).isEqualTo(ReviewStatus.REJECTED);   // e3 1회차 반려
        assertThat(reviewQueryService.getLatestStatus(3L).getReviewRoundId()).isEqualTo(2L);
    }
}
