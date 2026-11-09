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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 수강 건 (확정 시각, 확정+30일 만료 시각, 상태)
 * [담당] 팀원 2 · #03(매핑), #10(isPastExpiry·isActive·expire)   [기획] FR-054, 05 §4-2
 * [연결] 호출받음: 팀원 3 Policy(isWithinPeriod, C6), EnrollmentExpiryService(#10 만료 처리)
 * 칼럼은 resources/sql/schema.sql의 `enrollments` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "enrollments")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Enrollment {

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
     * 계약 C6: now가 수강 기간(수강 확정 ~ 확정+30일) 안인지 자기 시각 칼럼만으로 판단한다.
     * 호출: 팀원 3 VideoAccessPolicy·SubmissionPolicy 등 (영상 접근·과제 제출 기간 확인)
     * 상태(수료·취소)는 보지 않는다 — "수료자는 기간과 무관하게 시청 가능"(D-35 등)은 호출한 Policy가 따로 판단한다.
     */
    public boolean isWithinPeriod(LocalDateTime now) {
        // ❓ 임시값: 만료 시각 당일 정각까지 포함(<=). 경계 포함 여부는 #01 합의 필요 (기획은 "30일 이내"만 명시)
        return !now.isBefore(confirmedAt) && !now.isAfter(expiresAt);
    }

    /**
     * 만료 시각이 지났는지 (isWithinPeriod와 같은 경계 — 만료 시각 정각까지는 기간 안).
     * 호출: EnrollmentExpiryService.expireOne (#10 만료 처리)
     */
    public boolean isPastExpiry(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    /** 정원을 점유하는 상태인지 (ACTIVE) */
    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    /**
     * 30일 만료로 수강을 끝낸다 — ACTIVE → EXPIRED, 종료 시각 기록(DB CHECK ck_enroll_end: ACTIVE가 아니면 ended_at 필수).
     * 평가할 과제가 남았는지는 다른 도메인(#14) 정보라 호출한 EnrollmentExpiryService가 먼저 확인한다(05 §4-2).
     * 호출: EnrollmentExpiryService.expireOne (SC-01)
     *
     * @throws BusinessException INVALID_REVIEW_STATE — 이미 수료·취소·만료된 수강 건
     */
    public void expire(LocalDateTime now) {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
        this.status = EnrollmentStatus.EXPIRED;
        this.endedAt = now;
    }
}
