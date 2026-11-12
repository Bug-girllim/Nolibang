package com.wanted.lmspractice.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.review.dto.DueFilter;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 평가 흐름 테스트 — 엔티티 상태 전이(시작·시작 취소·초안·승인·반려), 담당 강사 확인, 수료·자리 반환, 만료 후 반려, 담당 변경 승계,
 *        마감 임박·초과 표시, 계약 C11 (이슈 #14 테스트 항목)
 * [담당] 팀원 3 · #14   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 각 테스트는 끝나면 롤백된다(@Transactional).
 * [시드] 강사 6 teacherA(강의 4 A분반 = 분반 4 담당) · 7 teacherB(강의 4 B분반 = 분반 5, 강의 7~9 분반 9~11 담당)
 *       r2: e2(learnerB, 강의 4 분반 4) WAITING · r4: e4(learnerC, 강의 4 분반 5) 2회차 IN_REVIEW + teacherB 초안 있음
 *       r1: e3(learnerB, 강의 8 분반 10) APPROVED · r3: e4 1회차 REJECTED — r1·r3은 teacherB 분반
 *       강의 4는 분반 4·5 모두 정원 5(활성 1씩)에 대기자가 없다 → 자리 반환·대기 확정은 테스트 안에서 만석 + 대기자(learnerA)를 만든다
 *       e1(learnerB, 강의 1)은 과제 미제출(평가 회차 없음)
 * [한계] 한 트랜잭션 안에서 도는 테스트라 "승인 2번 동시 클릭"은 순차 재호출(두 번째 INVALID_REVIEW_STATE)로만 확인한다.
 *       실제 동시성은 회차 행 FOR UPDATE + DB uq_reviews_approved로 막으며, 브라우저 2개 수동 테스트 결과를 PR에 적는다.
 *       알림 레코드는 NotificationService(#17) 스텁이라 #17 merge 후 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class ReviewServiceTest {

    private static final long TEACHER_A = 6L, TEACHER_B = 7L, LEARNER_A = 2L;
    private static final long R_WAITING = 2L, R_IN_REVIEW = 4L, R_APPROVED = 1L;
    private static final long E_IN_REVIEW = 4L, E_APPROVED = 3L, E_NOT_SUBMITTED = 1L;
    private static final long COURSE_DIGITAL_BASIC = 4L;

    private static final String TEXT_50 = "가".repeat(ReviewRound.MIN_FEEDBACK_LENGTH);
    private static final String TEXT_49 = "가".repeat(ReviewRound.MIN_FEEDBACK_LENGTH - 1);

    @Autowired private ReviewService reviewService;
    @Autowired private ReviewQueryService reviewQueryService;
    @Autowired private ReviewRoundRepository reviewRoundRepository;
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

    /** 변경 감지 내용을 DB에 보내고 1차 캐시를 비워 DB 값으로 다시 읽게 한다 (DB CHECK도 이때 검사된다) */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ReviewRound round(long id) {
        return reviewRoundRepository.findById(id).orElseThrow();
    }

    /**
     * 강의 4를 만석(분반 4·5 모두 정원 1 = 활성 1)으로 만들고 learnerA를 대기 1번으로 세운다(롤백됨).
     * 시드 강의 4는 자리가 남고 대기자가 없어서, "자리 반환 → 대기 1순위 자동 확정"을 보려면 이 상태가 필요하다. 엔티티를 불러오기 전에만 쓴다
     */
    private void makeCourse4FullWithWaiter() {
        sql("UPDATE course_sections SET capacity = 1 WHERE course_id = " + COURSE_DIGITAL_BASIC);
        sql("INSERT INTO waitlist_entries (user_id, course_id, status) VALUES (" + LEARNER_A + ", " + COURSE_DIGITAL_BASIC + ", 'WAITING')");
    }

    // ───────────────────────── 시작 · 시작 취소 · 초안 ─────────────────────────

    @Test
    @DisplayName("평가 시작: 대기 → 진행 중, 시작 시각·시작 강사 기록 (학습자 수정 잠금)")
    void start_waitingToInReview() {
        reviewService.start(TEACHER_A, R_WAITING);
        flushAndClear();

        ReviewRound r = round(R_WAITING);
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(r.getStartedBy()).isEqualTo(TEACHER_A);
        assertThat(r.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("평가 시작: 다른 분반 강사는 NOT_ASSIGNED_INSTRUCTOR, 이미 진행 중이면 INVALID_REVIEW_STATE")
    void start_rejectsOtherInstructorAndWrongState() {
        assertThatThrownBy(() -> reviewService.start(TEACHER_B, R_WAITING))     // r2는 teacherA 분반
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThatThrownBy(() -> reviewService.start(TEACHER_B, R_IN_REVIEW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    @Test
    @DisplayName("없는 평가 회차는 REVIEW_NOT_FOUND")
    void notFound() {
        assertThatThrownBy(() -> reviewService.getReviewDetail(TEACHER_A, 9999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    @DisplayName("시작 취소: 초안이 있으면 CANNOT_CANCEL_START, 초안을 비우면 취소되고 마감은 그대로")
    void cancelStart_onlyWithoutDraft_keepsDue() {
        assertThatThrownBy(() -> reviewService.cancelStart(TEACHER_B, R_IN_REVIEW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANNOT_CANCEL_START));

        var dueBefore = round(R_IN_REVIEW).getDueAt();
        reviewService.saveDraft(TEACHER_B, R_IN_REVIEW, "   ");
        reviewService.cancelStart(TEACHER_B, R_IN_REVIEW);
        flushAndClear();

        ReviewRound r = round(R_IN_REVIEW);
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.WAITING);
        assertThat(r.getStartedAt()).isNull();
        assertThat(r.getDraftBody()).isNull();
        assertThat(r.getDueAt()).isEqualTo(dueBefore);   // D-56: 시작 취소로 마감이 바뀌지 않는다
    }

    @Test
    @DisplayName("초안 저장: 진행 중일 때만, 대기 상태면 INVALID_REVIEW_STATE")
    void saveDraft() {
        reviewService.saveDraft(TEACHER_B, R_IN_REVIEW, "명암 단계가 잘 보입니다.");
        flushAndClear();
        assertThat(round(R_IN_REVIEW).getDraftBody()).isEqualTo("명암 단계가 잘 보입니다.");
        assertThat(round(R_IN_REVIEW).getDraftAuthorId()).isEqualTo(TEACHER_B);

        assertThatThrownBy(() -> reviewService.saveDraft(TEACHER_A, R_WAITING, "초안"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    // ───────────────────────── 승인 · 반려 ─────────────────────────

    @Test
    @DisplayName("승인: 49자는 FEEDBACK_TOO_SHORT, 50자면 승인 → 수료 → 자리 반환으로 대기 1순위 자동 확정 (한 트랜잭션)")
    void approve_completesAndFillsVacancy() {
        makeCourse4FullWithWaiter();
        assertThatThrownBy(() -> reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_49)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FEEDBACK_TOO_SHORT));

        reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
        flushAndClear();

        ReviewRound r = round(R_IN_REVIEW);
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(r.getReviewerId()).isEqualTo(TEACHER_B);
        assertThat(r.getFeedbackBody()).isEqualTo(TEXT_50);
        assertThat(r.getFinalizedAt()).isNotNull();

        assertThat(enrollmentRepository.findById(E_IN_REVIEW).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        // 강의 4 B분반(정원 1)이 비어 대기 1번 learnerA가 자동 확정된다
        assertThat(waitlistEntryRepository.findByUserIdAndCourseIdAndStatus(LEARNER_A, COURSE_DIGITAL_BASIC, WaitlistStatus.CONFIRMED)).isPresent();
    }

    @Test
    @DisplayName("승인 두 번(이미 확정된 건 재승인)은 INVALID_REVIEW_STATE — 수료는 1회만")
    void approveTwice_secondFails() {
        reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
        flushAndClear();
        assertThatThrownBy(() -> reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
        assertThatThrownBy(() -> reviewService.reject(TEACHER_B, R_APPROVED, new ReviewDecisionDTO(TEXT_50)))  // r1도 teacherB 분반
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    @Test
    @DisplayName("반려(기간 안): 49자 사유는 FEEDBACK_TOO_SHORT, 50자면 반려되고 수강은 ACTIVE 유지(재제출 가능)")
    void reject_withinPeriod_keepsEnrollmentActive() {
        assertThatThrownBy(() -> reviewService.reject(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_49)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FEEDBACK_TOO_SHORT));

        reviewService.reject(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
        flushAndClear();

        assertThat(round(R_IN_REVIEW).getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(enrollmentRepository.findById(E_IN_REVIEW).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("수강 기간 만료 후에도 평가할 수 있고, 만료 후 반려는 수강 종료(EXPIRED)·자리 반환")
    void reject_afterExpiry_closesEnrollment() {
        makeCourse4FullWithWaiter();
        sql("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY,"
                + " expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = " + E_IN_REVIEW);

        reviewService.reject(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
        flushAndClear();

        Enrollment e = enrollmentRepository.findById(E_IN_REVIEW).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(waitlistEntryRepository.findByUserIdAndCourseIdAndStatus(LEARNER_A, COURSE_DIGITAL_BASIC, WaitlistStatus.CONFIRMED)).isPresent();
    }

    // ───────────────────────── 담당 변경 승계 ─────────────────────────

    @Test
    @DisplayName("담당 변경: 이전 강사는 평가 불가, 새 강사가 초안·잠금·마감을 그대로 이어받아 승인")
    void handover_newInstructorInheritsDraftAndLock() {
        // 분반 5(teacherB) → teacherA(취미 2 권한 있음)로 담당 변경
        sql("UPDATE course_sections SET instructor_id = " + TEACHER_A + " WHERE id = 5");
        ReviewRound before = round(R_IN_REVIEW);
        var dueBefore = before.getDueAt();
        var draftBefore = before.getDraftBody();
        entityManager.clear();

        assertThatThrownBy(() -> reviewService.getReviewDetail(TEACHER_B, R_IN_REVIEW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThatThrownBy(() -> reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));

        InstructorReviewDetailDTO detail = reviewService.getReviewDetail(TEACHER_A, R_IN_REVIEW);
        assertThat(detail.isInReview()).isTrue();          // 잠금 승계
        assertThat(detail.getDraftBody()).isEqualTo(draftBefore);   // 초안 승계
        assertThat(detail.getDueAt()).isEqualTo(dueBefore);         // 마감 승계

        reviewService.approve(TEACHER_A, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
        flushAndClear();
        assertThat(round(R_IN_REVIEW).getReviewerId()).isEqualTo(TEACHER_A);
    }

    // ───────────────────────── 목록 · 임박/초과 ─────────────────────────

    @Test
    @DisplayName("목록: 현재 담당 분반의 회차만, 탭별 건수")
    void list_onlyAssignedSections() {
        ReviewListPageDTO b = reviewService.getReviewList(TEACHER_B, ReviewTab.IN_REVIEW, null, null);
        assertThat(b.getReviews()).extracting(ReviewListDTO::getReviewRoundId).containsExactly(R_IN_REVIEW);
        assertThat(b.getWaitingCount()).isZero();      // r2는 teacherA 분반
        assertThat(b.getDoneCount()).isEqualTo(2);     // r1 승인(분반 10), r3 반려(분반 5)

        ReviewListPageDTO a = reviewService.getReviewList(TEACHER_A, ReviewTab.WAITING, null, null);
        assertThat(a.getReviews()).extracting(ReviewListDTO::getReviewRoundId).containsExactly(R_WAITING);
        assertThat(a.getReviews().get(0).getLearnerName()).isEqualTo("이배움");   // 강사 화면만 실명 (learnerB)
    }

    @Test
    @DisplayName("마감 임박(48시간 이하)·기한 초과 표시와 기한 필터")
    void dueSoonAndOverdue() {
        // r2(teacherA, 대기): 마감까지 47시간 남음(임박), r4(teacherB, 진행 중): 마감 1시간 지남(초과)
        sql("UPDATE review_rounds SET submitted_at = UTC_TIMESTAMP(6) - INTERVAL 121 HOUR,"
                + " due_at = UTC_TIMESTAMP(6) - INTERVAL 121 HOUR + INTERVAL 7 DAY WHERE id = " + R_WAITING);
        sql("UPDATE review_rounds SET submitted_at = UTC_TIMESTAMP(6) - INTERVAL 169 HOUR,"
                + " due_at = UTC_TIMESTAMP(6) - INTERVAL 169 HOUR + INTERVAL 7 DAY WHERE id = " + R_IN_REVIEW);

        ReviewListDTO soon = reviewService.getReviewList(TEACHER_A, ReviewTab.WAITING, null, DueFilter.SOON).getReviews().get(0);
        assertThat(soon.isDueSoon()).isTrue();
        assertThat(soon.isOverdue()).isFalse();

        ReviewListDTO over = reviewService.getReviewList(TEACHER_B, ReviewTab.IN_REVIEW, null, DueFilter.OVERDUE).getReviews().get(0);
        assertThat(over.isOverdue()).isTrue();
        assertThat(over.getRemainingText()).startsWith("초과");
        assertThat(reviewService.getReviewList(TEACHER_B, ReviewTab.IN_REVIEW, null, DueFilter.SOON).getReviews()).isEmpty();

        // 기한이 지나도 평가할 수 있다
        reviewService.approve(TEACHER_B, R_IN_REVIEW, new ReviewDecisionDTO(TEXT_50));
    }

    // ───────────────────────── 계약 C11 ─────────────────────────

    @Test
    @DisplayName("계약 C11: hasPendingReview·getLatestStatus")
    void contractC11() {
        assertThat(reviewQueryService.hasPendingReview(E_IN_REVIEW)).isTrue();
        assertThat(reviewQueryService.hasPendingReview(E_APPROVED)).isFalse();       // e3은 승인 완료
        assertThat(reviewQueryService.hasPendingReview(E_NOT_SUBMITTED)).isFalse();  // e1은 미제출

        ReviewStatusDTO latest = reviewQueryService.getLatestStatus(E_IN_REVIEW);
        assertThat(latest.getReviewRoundId()).isEqualTo(R_IN_REVIEW);   // e4: 1회차 r3 반려 → 2회차 r4가 최신
        assertThat(latest.getStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(latest.getFirstSubmittedAt()).isNotNull();
        assertThat(reviewQueryService.getLatestStatus(E_NOT_SUBMITTED).isSubmitted()).isFalse();
    }
}
