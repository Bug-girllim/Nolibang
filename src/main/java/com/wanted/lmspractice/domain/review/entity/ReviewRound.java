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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 최종 과제 제출 1회 = 1행. 평가 상태 전이(대기 → 진행 → 승인/반려)와 마감 임박·초과 판단을 자기 칼럼만으로 한다.
 * [담당] 팀원 3 · #03(매핑) · #14(상태 메서드)   [기획] FR-023·030·031, D-56, D-37, O-04(반려 사유 50자)
 * [연결]
 *  - 호출받음: ReviewService(#14 시작·시작 취소·초안·승인·반려), ReviewService 화면 조립(임박·초과 표시)
 *  - DB 이중 방어: ck_reviews_shape(상태별 칼럼 모양·피드백 50자), uq_reviews_approved(수강 건당 승인 1회)
 * [기술] 🟢 Entity 상태 메서드(미니 프로젝트 Menu.stopSelling() 방식), public setter 없음
 * 칼럼은 resources/sql/schema.sql의 `review_rounds` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "review_rounds")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class ReviewRound {

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

    /** 평가 대기·진행 중(아직 확정 전)인지 — 마감 경고는 이때만 보인다 */
    public boolean isPending() {
        return status == ReviewStatus.WAITING || status == ReviewStatus.IN_REVIEW;
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
