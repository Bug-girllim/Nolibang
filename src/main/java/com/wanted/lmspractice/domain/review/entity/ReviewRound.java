package com.wanted.lmspractice.domain.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 평가 회차 한 행 = 제출 1회 (제출 시각·마감 = 제출 + 7일·평가 시작·초안·결과). 자기 상태로 판단하는 규칙을 가진다.
 *        평가 상태 전이(대기 → 진행 → 승인/반려)와 마감 임박·초과 판단도 자기 칼럼만으로 한다.
 * [담당] 팀원 3 · #03(칼럼 매핑) · #13(create·상태 확인 메서드) · #14(평가 시작·시작 취소·초안·승인·반려, 임박·초과)
 *       [화면] U-S07, T-S03, T-S04   [기획] FR-021·022·023·026·030·031, D-41, D-56, D-37, O-04(반려 사유 50자)
 * [연결]
 *  - 만든 곳: AssignmentService.submit(1회차)·resubmit(반려 뒤 round_no + 1 새 행 — DB 확정 11-06)
 *  - 읽는 곳: SubmissionPolicy(수정 잠금·재제출 가능), AssignmentService(화면 이력)
 *  - 바꾸는 곳: ReviewService(#14 시작·시작 취소·초안·승인·반려), 화면 조립(임박·초과 표시)
 * [DB] schema.sql `review_rounds` 표와 1:1(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 *      CHECK: due_at = submitted_at + 7일 / 상태별 칼럼 모양·피드백 50자 이상(ck_reviews_shape).
 *      UNIQUE: (enrollment_id, round_no), work_version_id, 진행 중 회차는 수강당 1건(uq_reviews_active), 승인 1회(uq_reviews_approved).
 *      다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다(다른 도메인 엔티티 직접 참조 금지).
 * [기술] 🟢 @Entity, @Enumerated(STRING), 정적 팩토리 create(), 이름 있는 상태 메서드(미니 프로젝트 Menu.stopSelling() 방식, public setter 없음)
 */
@Entity
@Table(name = "review_rounds")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class ReviewRound {

    /** 평가 마감 일수 — 제출(재제출 포함) 시각부터 센다. DB CHECK(due_at = submitted_at + 7일)와 같은 값 (FR-026) */
    public static final int REVIEW_DUE_DAYS = 7;
    /** 승인 피드백·반려 사유 최소 글자 수 — DB ck_reviews_shape(CHAR_LENGTH >= 50)와 같다(O-04 종결값) */
    public static final int MIN_FEEDBACK_LENGTH = 50;
    /** 마감 임박 기준(시간) — 마감까지 48시간 이하면 경고(API RV-01 dueSoon) */
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

    // ───────────────────────── 제출 (#13) ─────────────────────────

    /**
     * 새 제출 회차를 "평가 대기"로 만든다 — 최초 제출(1회차)과 반려 뒤 정식 재제출(직전 + 1회차)이 모두 이 메서드만 쓴다.
     * 마감은 여기서만 계산한다(제출 시각 + 7일). 평가 전 수정은 이 행을 건드리지 않으므로 마감이 바뀌지 않는다.
     * 호출: AssignmentService.submit·resubmit
     *
     * @param workVersionId WorkService.saveFinalWorkVersion이 돌려준 새 버전 id (회차마다 다른 버전 — UNIQUE)
     * @param submittedAt   제출 시각(서버 현재 시각)
     */
    public static ReviewRound create(Long enrollmentId, Long workVersionId, int roundNo, LocalDateTime submittedAt) {
        // DB가 DATETIME(6)이라 마이크로초 아래를 미리 잘라 둔다 — 두 시각이 같은 소수점으로 저장되어야 CHECK(+7일)를 통과한다
        LocalDateTime submitted = submittedAt.truncatedTo(ChronoUnit.MICROS);
        ReviewRound round = new ReviewRound();
        round.enrollmentId = enrollmentId;
        round.workVersionId = workVersionId;
        round.roundNo = roundNo;
        round.status = ReviewStatus.WAITING;
        round.submittedAt = submitted;
        round.dueAt = submitted.plusDays(REVIEW_DUE_DAYS);
        return round;
    }

    /** 진행 중(평가 대기·평가 중) 회차인지 — 수강당 1건만 허용(DB uq_reviews_active와 같은 기준) */
    public boolean isActive() {
        return status == ReviewStatus.WAITING || status == ReviewStatus.IN_REVIEW;
    }

    /** 평가 대기 — 학습자가 아직 수정할 수 있는 상태 */
    public boolean isWaiting() {
        return status == ReviewStatus.WAITING;
    }

    /** 평가 중 — 강사가 평가를 시작해 학습자 수정이 잠긴 상태 (E-MODALS "평가 중 과제 잠금") */
    public boolean isInReview() {
        return status == ReviewStatus.IN_REVIEW;
    }

    /** 반려 확정 — 수강 기간 안이면 정식 재제출할 수 있다 */
    public boolean isRejected() {
        return status == ReviewStatus.REJECTED;
    }

    /** 이 회차 다음에 재제출할 때 쓸 회차 번호 (횟수 제한 없음 — FR-026) */
    public int nextRoundNo() {
        return roundNo + 1;
    }

    // ───────────────────────── 상태 전이 (#14) ─────────────────────────

    /**
     * 평가 시작 — WAITING → IN_REVIEW. 이 순간부터 학습자 과제 수정이 잠긴다(#13 SubmissionPolicy가 상태로 판단).
     * 단순 열람은 잠금이 아니다(RV-02) — 이 메서드는 「평가 시작」 버튼에서만 불린다.
     * 호출: ReviewService.start
     * @throws BusinessException INVALID_REVIEW_STATE — 평가 대기가 아님(이미 시작·확정)
     */
    public void start(Long instructorId, LocalDateTime now) {
        requireStatus(ReviewStatus.WAITING);
        this.status = ReviewStatus.IN_REVIEW;
        this.startedAt = now;
        this.startedBy = instructorId;
    }

    /**
     * 평가 시작 취소 — 초안이 비어 있을 때만 IN_REVIEW → WAITING. 마감(dueAt)은 그대로 둔다(D-56).
     * 호출: ReviewService.cancelStart
     * @throws BusinessException INVALID_REVIEW_STATE(진행 중 아님), CANNOT_CANCEL_START(초안 작성됨)
     */
    public void cancelStart() {
        requireStatus(ReviewStatus.IN_REVIEW);
        if (hasDraft()) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_START);
        }
        // ck_reviews_shape: WAITING이면 시작·초안 칼럼이 모두 비어 있어야 한다
        this.status = ReviewStatus.WAITING;
        this.startedAt = null;
        this.startedBy = null;
        this.draftBody = null;
        this.draftAuthorId = null;
        this.draftUpdatedAt = null;
    }

    /**
     * 피드백 초안 저장 — 진행 중일 때만. 담당이 바뀌면 새 담당 강사가 같은 행의 초안을 이어 쓴다(승계).
     * 빈 글은 "초안 없음"으로 저장해 시작 취소가 다시 가능해진다.
     * 호출: ReviewService.saveDraft
     * @throws BusinessException INVALID_REVIEW_STATE — 진행 중 아님
     */
    public void saveDraft(String text, Long authorId, LocalDateTime now) {
        requireStatus(ReviewStatus.IN_REVIEW);
        boolean empty = text == null || text.isBlank();
        this.draftBody = empty ? null : text;
        this.draftAuthorId = empty ? null : authorId;
        this.draftUpdatedAt = empty ? null : now;
    }

    /**
     * 수료 승인 확정 — IN_REVIEW → APPROVED, 공식 피드백 50자 이상. 확정 후 정정 없음(D-37).
     * 호출: ReviewService.approve (이어서 EnrollmentService.complete를 같은 트랜잭션에서 부른다)
     * @throws BusinessException INVALID_REVIEW_STATE(진행 중 아님 — 이미 확정 포함), FEEDBACK_TOO_SHORT
     */
    public void approve(Long reviewerId, String feedback, LocalDateTime now) {
        finalizeAs(ReviewStatus.APPROVED, reviewerId, feedback, now);
    }

    /**
     * 반려 확정 — IN_REVIEW → REJECTED, 반려 사유 50자 이상(O-04).
     * 호출: ReviewService.reject
     * @throws BusinessException INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT
     */
    public void reject(Long reviewerId, String reason, LocalDateTime now) {
        finalizeAs(ReviewStatus.REJECTED, reviewerId, reason, now);
    }

    // 상태를 먼저 본다: 이미 확정된 건을 다시 누르면 글자 수와 상관없이 INVALID_REVIEW_STATE
    private void finalizeAs(ReviewStatus result, Long reviewerId, String text, LocalDateTime now) {
        requireStatus(ReviewStatus.IN_REVIEW);
        String body = text == null ? "" : text.strip();
        // DTO @Size(min = 50)와 이중 방어 — 앞뒤 공백으로 채운 글은 여기서 걸러진다
        if (body.length() < MIN_FEEDBACK_LENGTH) {
            throw new BusinessException(ErrorCode.FEEDBACK_TOO_SHORT);
        }
        this.status = result;
        this.feedbackBody = body;
        this.reviewerId = reviewerId;
        this.finalizedAt = now;
    }

    private void requireStatus(ReviewStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
    }

    // ───────────────────────── 판단 (#14) ─────────────────────────

    /** 평가 대기·진행 중(아직 확정 전)인지 — 마감 경고는 이때만 보인다. 기준은 isActive(#13)와 같다 */
    public boolean isPending() {
        return isActive();
    }

    /** 작성 중인 초안이 있는지 — 있으면 시작 취소 불가(RV-04) */
    public boolean hasDraft() {
        return draftBody != null && !draftBody.isBlank();
    }

    /** 마감 지남 — 경고만 하고 평가는 계속할 수 있다(API RV-01 overdue, 만료 후에도 평가 가능) */
    public boolean isOverdue(LocalDateTime now) {
        return isPending() && now.isAfter(dueAt);
    }

    /** 마감까지 48시간 이하이고 아직 지나지 않음 — "마감 임박" 경고(RV-01 dueSoon) */
    public boolean isDueSoon(LocalDateTime now) {
        return isPending() && !isOverdue(now) && !dueAt.isAfter(now.plusHours(DUE_SOON_HOURS));
    }
}
