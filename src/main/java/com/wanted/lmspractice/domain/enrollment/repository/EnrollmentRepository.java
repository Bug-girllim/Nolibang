package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] enrollments 표 접근 — 학습자의 강의별 수강 건 조회, 분반별 정원 점유(ACTIVE) 수, 만료 대상 조회
 * [담당] 팀원 2 · #09 · #10(countBySectionIdAndStatus·findByStatusAndExpiresAtBefore) · #11(본인 수강 목록 조회)
 * [연결]
 *  - 호출받음: EnrollEligibilityPolicy(중복·재신청 확인), SectionAssignPolicy(남은 자리 계산), EnrollmentService, SeatService,
 *             SectionService(#10 정원 축소 검사·C6 현재 담당), EnrollmentExpiryService(#10 만료 대상)
 * [기술] 🟢 쿼리 메서드(findBy…·countBy…), 🟢 @Query(nativeQuery = true) + FOR UPDATE
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * 학습자의 이 강의 수강 건 — UNIQUE(user_id, course_id)라 상태와 관계없이 최대 1건이다.
     * 호출: EnrollEligibilityPolicy.checkNoHistory, EnrollmentService.getStatusForUser
     */
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    /** [성능 개선] 학습자의 여러 강의 수강 건을 한 번에(강의마다 1건 이하). 호출: EnrollmentService.getStatusesForUser */
    List<Enrollment> findByUserIdAndCourseIdIn(Long userId, Collection<Long> courseIds);

    /**
     * 분반의 정원 점유(ACTIVE) 수 — 잠금 읽기(FOR UPDATE)로 센다.
     * 호출: SectionAssignPolicy.pickSection, SectionService.changeCapacity(#10 — 잠금 후 축소 검사)
     * 왜 잠금 읽기인가: MySQL(REPEATABLE READ)의 일반 SELECT는 트랜잭션의 첫 조회 시점 사진(스냅숏)을 본다.
     * 수강 취소처럼 분반 잠금 전에 다른 조회를 한 트랜잭션은, 잠금을 기다리는 사이 다른 쪽이 확정한 수강 건을 못 보고
     * 자리를 한 번 더 내줄 수 있다. 잠금 읽기는 항상 최신 확정 값을 읽어 이 문제를 막는다(이슈 #09 시나리오 33).
     */
    @Query(value = "SELECT COUNT(*) FROM enrollments WHERE section_id = :sectionId AND status = 'ACTIVE' FOR UPDATE",
            nativeQuery = true)
    long countActiveBySectionIdForUpdate(@Param("sectionId") Long sectionId);

    /** 분반의 상태별 수강 수 — 정원 변경 전 화면 안내용 확인(#10 SectionService.checkCapacity, 잠금 없음) */
    long countBySectionIdAndStatus(Long sectionId, EnrollmentStatus status);

    /** 만료 시각이 지난 수강 건 (#10 SC-01 대상 후보, DB 인덱스 idx_enroll_expiry) */
    List<Enrollment> findByStatusAndExpiresAtBefore(EnrollmentStatus status, LocalDateTime now);

    /**
     * 수강 건의 강의 id만 읽는다(엔티티를 불러오지 않음). 호출: EnrollmentService.lockEnrollment, EnrollmentExpiryService.expireOne(#10)
     * — 강의 잠금을 먼저 잡기 위한 첫 조회. CAST 이유는 WaitlistEntryRepository.findCourseIdById 주석과 같다.
     */
    @Query(value = "SELECT CAST(course_id AS SIGNED) FROM enrollments WHERE id = :id", nativeQuery = true)
    Optional<Long> findCourseIdById(@Param("id") Long id);

    /**
     * 수강 건 하나를 잠그며 최신 값으로 가져온다. 호출: EnrollmentService.lockEnrollment(취소·수료·만료 후 반려), EnrollmentExpiryService(#10)
     * 분반 잠금을 잡은 "뒤에" 부른다 — 같은 수강 건을 동시에 수료·취소하는 경우 늦게 온 쪽이 바뀐 상태를 보고 거부되게 한다.
     */
    @Query(value = "SELECT * FROM enrollments WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Enrollment> findByIdForUpdate(@Param("id") Long id);

    /** [성능 개선] 학습자의 이 상태 수강 건 강의 id만 (배지 계산은 수료한 강의 id만 필요). 호출: EnrollmentService.getCompletedCourseIds */
    @Query("SELECT e.courseId FROM Enrollment e WHERE e.userId = :userId AND e.status = :status")
    List<Long> findCourseIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") EnrollmentStatus status);

    /** 학습자에게 이 상태의 수강 건이 있는지 — 회원 탈퇴 전 진행 중(ACTIVE) 수강 확인용. 호출: EnrollmentService.hasActiveEnrollment */
    boolean existsByUserIdAndStatus(Long userId, EnrollmentStatus status);

    /** 한 학습자의 모든 수강 건(진행·수료·취소·만료)을 최근 확정 순으로. 본인 것만 조회하려고 항상 userId로 찾는다. 호출: EnrollmentService.getMyEnrollments (#11) */
    List<Enrollment> findByUserIdOrderByConfirmedAtDesc(Long userId);
}
