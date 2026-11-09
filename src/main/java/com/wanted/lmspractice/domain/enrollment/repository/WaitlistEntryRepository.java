package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] waitlist_entries 표 조회·저장 (Spring Data JPA 쿼리 메서드)
 * [담당] 팀원 2 · #09 (#11에서 본인 대기 목록·대기 번호 조회 추가)
 * [연결]
 *  - 호출받음: WaitlistService(#09 대기 신청·취소, #11 getMyWaiting), EnrollmentService(#11 자동 확정 여부)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드
 */
@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /** 한 학습자의 특정 상태 대기 기록을 신청 순서(id)대로 (#11: WAITING만 조회) */
    List<WaitlistEntry> findByUserIdAndStatusOrderByIdAsc(Long userId, WaitlistStatus status);

    /**
     * 같은 강의에서 이 대기보다 먼저 신청한(id가 작은) 해당 상태 대기 수 — 대기 번호 = 이 값 + 1 (D-53: 신청 순서대로 FIFO).
     * 호출: WaitlistService.getMyWaiting
     */
    long countByCourseIdAndStatusAndIdLessThan(Long courseId, WaitlistStatus status, Long id);

    /** 이 수강 건들이 대기열을 거쳐 자동 확정된 것이면 그 대기 기록 (WaitlistEntry.enrollmentId로 이어진다) (#11) */
    List<WaitlistEntry> findByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
