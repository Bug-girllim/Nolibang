package com.wanted.lmspractice.domain.enrollment.entity;

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
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 수강 건 한 행 — 확정 시각·확정+30일 만료 시각·상태(ACTIVE = 정원 점유)와, 자기 상태만으로 판단하는 전이 규칙
 * [담당] 팀원 2 · #03(칼럼 매핑) · #09(create·cancel·complete·closeAfterRejection)   [기획] FR-052·054·055, D-52~D-55
 * [연결]
 *  - 만든 곳:   EnrollmentService.apply(즉시 확정), SeatService.fillVacancies(대기 → 자동 확정)
 *  - 상태 변경: EnrollmentService.cancel(본인 취소), complete·closeAfterRejection(계약 C5 — 팀원 3 ReviewService가 호출)
 *  - 읽는 곳:   계약 C6 isWithinPeriod(팀원 3 VideoAccessPolicy·SubmissionPolicy 등)
 * [DB] schema.sql `enrollments` 표와 1:1. UNIQUE(user_id, course_id) — 한 강의에는 수강 건이 평생 하나뿐이다(D-55 이중 방어).
 *      CHECK: expires_at = confirmed_at + 30일 / COMPLETED ↔ completed_at / CANCELLED ↔ cancelled_at / ACTIVE가 아니면 ended_at 필수.
 *      아래 상태 메서드는 이 CHECK를 만족하도록 칼럼을 함께 채운다.
 *      다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다(다른 도메인 엔티티 직접 참조 금지).
 * [기술] 🟢 @Entity, @Enumerated(STRING), 정적 팩토리 create(), 이름 있는 상태 메서드(public setter 없음)
 */
@Entity
@Table(name = "enrollments")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Enrollment {

    /** 수강 기간(일) — 수강 확정 시각부터 센다. DB CHECK(expires_at = confirmed_at + 30일)와 같은 값 */
    public static final int ENROLLMENT_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;  // DB 기본값과 같은 초기값

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 수강 확정 행을 만든다 — 즉시 확정(EN-01)과 대기 1순위 자동 확정(fillVacancies)이 모두 이 메서드만 쓴다.
     * 호출: EnrollmentService.apply, SeatService.fillVacancies
     * @param confirmedAt 확정 시각(서버 현재 시각). 만료 시각은 여기서 +30일로 계산한다.
     */
    public static Enrollment create(Long userId, Long courseId, Long sectionId, LocalDateTime confirmedAt) {
        // DB가 DATETIME(6)이라 마이크로초 아래를 미리 잘라 둔다 — 두 시각이 같은 소수점으로 저장되어야 CHECK(+30일)를 통과한다
        LocalDateTime confirmed = confirmedAt.truncatedTo(ChronoUnit.MICROS);
        Enrollment enrollment = new Enrollment();
        enrollment.userId = userId;
        enrollment.courseId = courseId;
        enrollment.sectionId = sectionId;
        enrollment.status = EnrollmentStatus.ACTIVE;
        enrollment.confirmedAt = confirmed;
        enrollment.expiresAt = confirmed.plusDays(ENROLLMENT_DAYS);
        return enrollment;
    }

    /**
     * 계약 C6: now가 수강 기간(수강 확정 ~ 확정+30일) 안인지 자기 시각 칼럼만으로 판단한다.
     * 호출: 팀원 3 VideoAccessPolicy·SubmissionPolicy 등 (영상 접근·과제 제출 기간 확인), closeAfterRejection
     * 상태(수료·취소)는 보지 않는다 — "수료자는 기간과 무관하게 시청 가능"(D-35 등)은 호출한 Policy가 따로 판단한다.
     */
    public boolean isWithinPeriod(LocalDateTime now) {
        // ❓ 임시값: 만료 시각 당일 정각까지 포함(<=). 경계 포함 여부는 #01 합의 필요 (기획은 "30일 이내"만 명시)
        return !now.isBefore(confirmedAt) && !now.isAfter(expiresAt);
    }

    /** 정원을 차지하는 상태인지 — 학습 중·평가 대기/진행(만료 후 포함)·기간 내 반려 (CONVENTIONS §2) */
    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    /** 이 학습자의 수강 건인지 — 다른 사람 수강 건은 존재를 숨기려고 ENROLLMENT_NOT_FOUND로 처리한다(API §1-6) */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 본인 수강 취소 → CANCELLED (D-54). 이후 같은 강의는 다시 신청할 수 없다(D-55).
     * 호출: EnrollmentService.cancel — 평가 대기·진행 여부는 다른 표(review_rounds)라 서비스가 먼저 확인한다.
     * @throws BusinessException INVALID_REVIEW_STATE — 이미 수료·취소·만료한 수강 건 (API EN-03 💡)
     */
    public void cancel(LocalDateTime now) {
        requireActive();
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
        this.endedAt = now;
    }

    /**
     * 수료 승인 → COMPLETED (계약 C5). 자리를 반환하므로 호출한 서비스가 이어서 fillVacancies를 부른다.
     * 호출: EnrollmentService.complete ← 팀원 3 ReviewService(#14 승인)
     * @throws BusinessException INVALID_REVIEW_STATE — ACTIVE가 아닌 수강 건
     */
    public void complete(LocalDateTime now) {
        requireActive();
        this.status = EnrollmentStatus.COMPLETED;
        this.completedAt = now;
        this.endedAt = now;
    }

    /**
     * 수강 기간 만료 후 반려 확정 → EXPIRED (계약 C5, 05 §4-2 "만료 후 반려는 종료").
     * 기간 안의 반려는 재제출할 수 있어 ACTIVE를 유지해야 하므로, 아직 기간 안이면 거부한다.
     * 호출: EnrollmentService.closeAfterRejection ← 팀원 3 ReviewService(#14 반려)
     * @throws BusinessException INVALID_REVIEW_STATE — ACTIVE가 아니거나 아직 수강 기간 안
     */
    public void closeAfterRejection(LocalDateTime now) {
        requireActive();
        if (isWithinPeriod(now)) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
        this.status = EnrollmentStatus.EXPIRED;
        this.endedAt = now;
    }

    /** 상태 전이는 ACTIVE에서만 — 수료·취소·만료는 끝난 상태라 되돌리지 않는다 */
    private void requireActive() {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
    }
}
