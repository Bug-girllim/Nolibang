package com.wanted.lmspractice.domain.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 평가 회차 한 행 = 제출 1회 (제출 시각·마감 = 제출 + 7일·평가 시작·초안·결과). 자기 상태로 판단하는 규칙을 가진다.
 * [담당] 팀원 3 · #03(칼럼 매핑) · #13(create·상태 확인 메서드) · #14(평가 시작·승인·반려 메서드 추가 예정)
 *       [화면] U-S07   [기획] FR-021·022·026, D-41
 * [연결]
 *  - 만든 곳: AssignmentService.submit(1회차)·resubmit(반려 뒤 round_no + 1 새 행 — DB 확정 11-06)
 *  - 읽는 곳: SubmissionPolicy(수정 잠금·재제출 가능), AssignmentService(화면 이력), #14 ReviewService(평가 진행)
 * [DB] schema.sql `review_rounds` 표와 1:1(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 *      CHECK: due_at = submitted_at + 7일 / WAITING이면 시작·결과 칼럼이 모두 비어 있어야 한다(ck_reviews_shape).
 *      UNIQUE: (enrollment_id, round_no), work_version_id, 진행 중(WAITING·IN_REVIEW) 회차는 수강당 1건(uq_reviews_active).
 *      다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다(다른 도메인 엔티티 직접 참조 금지).
 * [기술] 🟢 @Entity, @Enumerated(STRING), 정적 팩토리 create(), 이름 있는 상태 메서드(public setter 없음)
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
}
