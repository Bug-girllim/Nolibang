package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] waitlist_entries 표 접근 — 강의별 단일 대기열(FIFO = id 순)의 1순위·순번·대기 인원
 * [담당] 팀원 2 · #09 (관리자 대기열 조회는 #10에서 추가, 본인 대기 목록·자동 확정 여부 조회는 #11에서 추가)
 * [연결]
 *  - 호출받음: WaitlistService(대기 등록·취소·순번·내 대기 목록), EnrollEligibilityPolicy(이미 대기 중인지), SeatService.fillVacancies(1순위)
 * [기술] 🟢 쿼리 메서드(findBy…·countBy…·existsBy…), 🟢 @Query(nativeQuery = true) + FOR UPDATE
 */
@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /** 학습자의 이 강의 대기 줄 (status = WAITING이면 UNIQUE라 최대 1건). 호출: EnrollEligibilityPolicy, EnrollmentService.getStatusForUser */
    Optional<WaitlistEntry> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, WaitlistStatus status);

    /** [성능 개선] 학습자의 여러 강의 대기 줄을 한 번에 (WAITING이면 강의마다 최대 1건). 호출: WaitlistService.findWaitingByCourseIds */
    List<WaitlistEntry> findByUserIdAndStatusAndCourseIdIn(Long userId, WaitlistStatus status, Collection<Long> courseIds);

    /** 강의에 대기자가 있는지 — 있으면 빈자리가 있어도 신규 신청은 대기열 끝으로 간다(D-52). 호출: WaitlistService.hasWaiting */
    boolean existsByCourseIdAndStatus(Long courseId, WaitlistStatus status);

    /** 내 순번 = 같은 강의에서 나보다 먼저(id가 작거나 같은) 대기 중인 줄 수. 호출: WaitlistService.getWaitingNumber */
    long countByCourseIdAndStatusAndIdLessThanEqual(Long courseId, WaitlistStatus status, Long id);

    /**
     * 대기 1순위 한 줄을 잠그며 가져온다(가장 작은 id). 호출: SeatService.fillVacancies
     * 잠금 읽기라 호출한 트랜잭션의 스냅숏과 관계없이 최신 대기열을 본다(EnrollmentRepository.countActiveBySectionIdForUpdate 주석 참고).
     */
    @Query(value = "SELECT * FROM waitlist_entries WHERE course_id = :courseId AND status = 'WAITING' "
            + "ORDER BY id LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<WaitlistEntry> findFirstWaitingForUpdate(@Param("courseId") Long courseId);

    /**
     * 대기 줄의 강의 id만 읽는다(엔티티를 불러오지 않음). 호출: WaitlistService.cancel — 강의 잠금을 먼저 잡기 위한 첫 조회.
     * 엔티티로 미리 불러 두면 잠금 뒤 다시 읽어도 JPA가 처음 읽은(오래된) 객체를 돌려주므로 값만 읽는다.
     * CAST: DB 칼럼이 BIGINT UNSIGNED라 그대로 읽으면 Long이 아닌 큰 수 타입으로 올 수 있어 부호 있는 정수로 바꾼다.
     */
    @Query(value = "SELECT CAST(course_id AS SIGNED) FROM waitlist_entries WHERE id = :id", nativeQuery = true)
    Optional<Long> findCourseIdById(@Param("id") Long id);

    /**
     * 대기 줄 하나를 잠그며 가져온다. 호출: WaitlistService.cancel
     * 대기 취소와 자동 확정이 같은 줄을 동시에 바꾸면 "확정됐는데 취소로 덮어쓰기"가 생길 수 있어, 최신 값을 잠근 뒤 판단한다.
     */
    @Query(value = "SELECT * FROM waitlist_entries WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<WaitlistEntry> findByIdForUpdate(@Param("id") Long id);

    /** 한 학습자의 특정 상태 대기 기록을 신청 순서(id)대로 (#11: WAITING만 조회). 호출: WaitlistService.getMyWaiting */
    List<WaitlistEntry> findByUserIdAndStatusOrderByIdAsc(Long userId, WaitlistStatus status);

    /** 이 수강 건들이 대기열을 거쳐 자동 확정된 것이면 그 대기 기록 (WaitlistEntry.enrollmentId로 이어진다). 호출: WaitlistService.getAppliedAtByEnrollmentIds (#11) */
    List<WaitlistEntry> findByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
