package com.wanted.lmspractice.domain.review.entity;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 평가 회차 한 행(제출 1회 = 1행)과, 자기 칼럼만으로 판단하는 상태 전이 규칙
 *        대기(WAITING) → 진행(IN_REVIEW) → 승인(APPROVED)/반려(REJECTED), 진행 → 대기(시작 취소, 초안이 비었을 때만)
 * [담당] 팀원 3 · #03(칼럼 매핑) · #14(상태 메서드)   [화면] T-S03·T-S04   [기획] FR-023·030·031, D-56, D-37
 * [연결]
 *  - 상태 변경: ReviewService(start·cancelStart·saveDraft·approve·reject) — 항상 FOR UPDATE로 잠근 행에서만 부른다
 *  - 읽는 곳:   ReviewQueryService(계약 C11 isPending), ReviewService(목록의 isDueSoon·isOverdue)
 *  - 만드는 곳: #13 AssignmentService(제출·정식 재제출 — create()는 #13에서 추가)
 * [DB] schema.sql `review_rounds` 표와 1:1. 아래 상태 메서드는 CHECK를 만족하도록 칼럼을 함께 채운다.
 *      ck_reviews_shape: WAITING이면 started_*·draft_body·결과 칼럼 모두 NULL / IN_REVIEW면 started_* 필수 /
 *                        APPROVED·REJECTED면 reviewer_id·finalized_at·feedback_body(50자 이상) 필수
 *      ck_reviews_due: due_at = submitted_at + 7일 — 이 클래스는 due_at을 바꾸는 메서드를 두지 않는다(시작 취소·강사 변경으로 재설정 안 됨)
 *      uq_reviews_approved: 수강 건당 승인 1회 — 동시 승인 이중 방어
 *      다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다(다른 도메인 엔티티 직접 참조 금지).
 * [기술] 🟢 @Entity, @Enumerated(STRING), 이름 있는 상태 메서드(미니 프로젝트 Menu.stopSelling() 방식, public setter 없음)
 */
@Entity
@Table(name = "review_rounds")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class ReviewRound {

    /** 공식 피드백·반려 사유 최소 글자 수 — DB CHECK(CHAR_LENGTH(feedback_body) >= 50), DB 추적표 O-04 종결값 */
    public static final int MIN_FEEDBACK_LENGTH = 50;
    /** 평가 마감 = 제출(또는 정식 재제출) + 7일 — DB CHECK(ck_reviews_due)와 같은 값 */
    public static final int REVIEW_DUE_DAYS = 7;
    /** 마감 임박 기준(시간) — 마감까지 48시간 이하 (API RV-01 dueSoon, InstructorStatQuery.IMMINENT_HOURS와 같은 값) */
    public static final int DUE_SOON_HOURS = 48;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "work_version_id")
    private Long workVersionId;

    @Column(name = "submission_deleted_at")
    private LocalDateTime submissionDeletedAt;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.WAITING;  // DB 기본값과 같은 초기값

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "started_by")
    private Long startedBy;

    @Column(name = "draft_body")
    private String draftBody;

    @Column(name = "draft_author_id")
    private Long draftAuthorId;

    @Column(name = "draft_updated_at")
    private LocalDateTime draftUpdatedAt;

    @Column(name = "feedback_body")
    private String feedbackBody;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    // active_enrollment_id: 진행 중 회차 1건 제한용 DB 생성 칼럼 — 엔티티에 매핑하지 않음 (DB가 자동 계산)

    // approved_enrollment_id: 승인 1회 제한용 DB 생성 칼럼 — 엔티티에 매핑하지 않음 (DB가 자동 계산)

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    // ---------------------------------------------------------------
    // 상태 전이 (#14)
    // ---------------------------------------------------------------

    /**
     * 평가 시작 — WAITING → IN_REVIEW. 이 순간부터 학습자 과제 수정이 잠긴다(#13 SubmissionPolicy가 status로 판단).
     * 호출: ReviewService.start (POST /instructor/reviews/{id}/start, API RV-03)
     * @throws BusinessException INVALID_REVIEW_STATE — WAITING이 아님(이미 시작했거나 확정됨)
     */
    public void start(Long instructorId, LocalDateTime now) {
        requireStatus(ReviewStatus.WAITING);
        this.status = ReviewStatus.IN_REVIEW;
        this.startedAt = now;
        this.startedBy = instructorId;
    }

    /**
     * 평가 시작 취소 — IN_REVIEW → WAITING. 초안이 비어 있을 때만 되고, 마감(due_at)은 그대로 둔다(D-56).
     * 수강 기간 안이면 학습자 수정이 다시 허용된다 — 기간 판단은 #13 SubmissionPolicy 몫.
     * 호출: ReviewService.cancelStart (POST /instructor/reviews/{id}/cancel-start, API RV-04)
     * @throws BusinessException INVALID_REVIEW_STATE(IN_REVIEW 아님), CANNOT_CANCEL_START(초안 작성됨)
     */
    public void cancelStart() {
        requireStatus(ReviewStatus.IN_REVIEW);
        if (hasDraft()) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_START);
        }
        // ck_reviews_shape: WAITING은 started_*·draft_body가 모두 NULL이어야 한다
        this.status = ReviewStatus.WAITING;
        this.startedAt = null;
        this.startedBy = null;
        this.draftBody = null;
        this.draftAuthorId = null;
        this.draftUpdatedAt = null;
    }

    /**
     * 피드백 초안 저장 — IN_REVIEW일 때만. 공백만 있으면 "초안 없음"으로 저장해 시작 취소가 다시 가능하게 한다.
     * 초안은 회차 행에 있으므로 담당 강사가 바뀌면 새 담당이 그대로 이어서 쓴다(API RV-05 승계).
     * 호출: ReviewService.saveDraft (POST /instructor/reviews/{id}/draft, API RV-05)
     * @throws BusinessException INVALID_REVIEW_STATE — IN_REVIEW가 아님
     */
    public void saveDraft(Long instructorId, String draft, LocalDateTime now) {
        requireStatus(ReviewStatus.IN_REVIEW);
        this.draftBody = (draft == null || draft.isBlank()) ? null : draft;
        this.draftAuthorId = instructorId;
        this.draftUpdatedAt = now;
    }

    /**
     * 수료 승인 확정 — IN_REVIEW → APPROVED. 공식 피드백 50자 이상(DTO @Size에 이은 이중 방어).
     * 호출: ReviewService.approve (API RV-06) — 이어서 같은 트랜잭션에서 수료·자리 반환·알림이 실행된다
     * @throws BusinessException INVALID_REVIEW_STATE(IN_REVIEW 아님 — 이미 확정된 건 재승인 포함), FEEDBACK_TOO_SHORT
     */
    public void approve(Long reviewerId, String feedback, LocalDateTime now) {
        finalizeWith(ReviewStatus.APPROVED, reviewerId, feedback, now);
    }

    /**
     * 반려 확정 — IN_REVIEW → REJECTED. 반려 사유 50자 이상(O-04 종결값).
     * 호출: ReviewService.reject (API RV-07)
     * @throws BusinessException INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT
     */
    public void reject(Long reviewerId, String reason, LocalDateTime now) {
        finalizeWith(ReviewStatus.REJECTED, reviewerId, reason, now);
    }

    // ---------------------------------------------------------------
    // 자기 칼럼만으로 하는 판단
    // ---------------------------------------------------------------

    /** 평가가 끝나지 않은 회차인지(대기·진행) — 정원 점유·수강 취소 불가 판단(계약 C11 hasPendingReview)과 같은 기준 */
    public boolean isPending() {
        return status == ReviewStatus.WAITING || status == ReviewStatus.IN_REVIEW;
    }

    /** 작성 중인 초안이 있는지 — 있으면 시작 취소 불가(RV-04) */
    public boolean hasDraft() {
        return draftBody != null && !draftBody.isBlank();
    }

    /** 마감 초과 — 평가가 끝나지 않았고 마감이 지났다. 기한이 지나도 평가는 계속할 수 있다(경고만, API RV-01) */
    public boolean isOverdue(LocalDateTime now) {
        return isPending() && now.isAfter(dueAt);
    }

    /** 마감 임박 — 평가가 끝나지 않았고 마감까지 48시간 이하(이미 지난 건은 초과로 따로 표시) */
    public boolean isDueSoon(LocalDateTime now) {
        return isPending() && !isOverdue(now) && !dueAt.isAfter(now.plusHours(DUE_SOON_HOURS));
    }

    /** 승인·반려 공통: 진행 중인지 → 글자 수 → 결과 칼럼 채우기 (ck_reviews_shape의 확정 상태 모양) */
    private void finalizeWith(ReviewStatus result, Long reviewerId, String content, LocalDateTime now) {
        requireStatus(ReviewStatus.IN_REVIEW);
        String body = content == null ? "" : content.strip();
        // DB CHAR_LENGTH는 글자(코드 포인트) 수라 length() 대신 codePointCount로 센다 — 이모지 1개를 2자로 세지 않도록
        if (body.codePointCount(0, body.length()) < MIN_FEEDBACK_LENGTH) {
            throw new BusinessException(ErrorCode.FEEDBACK_TOO_SHORT);
        }
        this.status = result;
        this.feedbackBody = body;
        this.reviewerId = reviewerId;
        this.finalizedAt = now;
    }

    /** 지금 상태가 기대한 상태가 아니면 거부 — 상태 전이 가능 여부는 모두 여기서 검사한다 */
    private void requireStatus(ReviewStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
    }
}
