package com.wanted.lmspractice.domain.enrollment.entity;

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
 * [역할] 강의별 단일 대기열의 한 줄 — 순번은 같은 강의 WAITING 행의 id 순서(FIFO)로 정한다
 * [담당] 팀원 2 · #03(칼럼 매핑) · #09(create·confirm·cancel)   [기획] FR-053, D-52(대기자 우선) · 자동 확정은 수락 단계 없음(U-S05)
 * [연결]
 *  - 만든 곳:   WaitlistService.join (EN-01에서 대기로 갈 때)
 *  - 상태 변경: SeatService.fillVacancies → confirm(자동 확정), WaitlistService.cancel → cancel(EN-02)
 * [DB] schema.sql `waitlist_entries` 표와 1:1.
 *      CHECK ck_wait_shape: WAITING(나머지 비움) / CONFIRMED(enrollment_id·confirmed_at 필수) / CANCELLED(cancelled_at 필수)
 *      UNIQUE(course_id, waiting_user_id) — 같은 강의에 WAITING은 한 사람당 하나. 취소 후 다시 대기하면 새 행(끝순번)이 생긴다.
 * [기술] 🟢 @Entity, @Enumerated(STRING), 정적 팩토리 create(), 이름 있는 상태 메서드(public setter 없음)
 */
@Entity
@Table(name = "waitlist_entries")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status = WaitlistStatus.WAITING;  // DB 기본값과 같은 초기값

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // waiting_user_id: 진행 중 대기 중복 방지용 DB 생성 칼럼 — 엔티티에 매핑하지 않음 (DB가 자동 계산)

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록 = 대기 신청 시각

    /**
     * 대기열 끝에 새 줄을 만든다(WAITING). 순번은 저장 후 id로 정해지므로 따로 저장하지 않는다.
     * 호출: WaitlistService.join
     */
    public static WaitlistEntry create(Long userId, Long courseId) {
        WaitlistEntry entry = new WaitlistEntry();
        entry.userId = userId;
        entry.courseId = courseId;
        entry.status = WaitlistStatus.WAITING;
        return entry;
    }

    /** 아직 대기 중인지 */
    public boolean isWaiting() {
        return status == WaitlistStatus.WAITING;
    }

    /** 이 학습자의 대기인지 — 다른 사람 대기는 NOT_FOUND로 숨긴다(API EN-02) */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 자리가 나서 자동 확정 → CONFIRMED. 수락 단계 없이 바로 확정한다(U-S05 안내).
     * 호출: SeatService.fillVacancies — enrollmentId는 방금 만든 수강 건(FK: 같은 학습자·같은 강의여야 한다)
     */
    public void confirm(Long enrollmentId, LocalDateTime now) {
        this.status = WaitlistStatus.CONFIRMED;
        this.enrollmentId = enrollmentId;
        this.confirmedAt = now;
    }

    /**
     * 본인 대기 취소 → CANCELLED. 다시 대기하면 새 줄이라 끝순번이 된다(FR-053).
     * 호출: WaitlistService.cancel — WAITING인지는 서비스가 행 잠금 후 확인한다.
     */
    public void cancel(LocalDateTime now) {
        this.status = WaitlistStatus.CANCELLED;
        this.cancelledAt = now;
    }
}
