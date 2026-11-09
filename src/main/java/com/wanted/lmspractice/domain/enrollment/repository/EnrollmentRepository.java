package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] enrollments 표 조회·저장
 * [담당] 팀원 2 · #09·#10
 * [연결]
 *  - 호출받음: SectionService(#10 활성 수강 수·C6 현재 담당), EnrollmentExpiryService(#10 만료 대상), #09 신청·취소
 * [기술] 🟢 JpaRepository 쿼리 메서드, 🟢 @Query(nativeQuery)
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 분반의 상태별 수강 수 — 정원 축소 검사에 ACTIVE 수를 쓴다(SE-03) */
    long countBySectionIdAndStatus(Long sectionId, EnrollmentStatus status);

    /** 만료 시각이 지난 수강 건 (SC-01 대상 후보, DB 인덱스 idx_enroll_expiry) */
    List<Enrollment> findByStatusAndExpiresAtBefore(EnrollmentStatus status, LocalDateTime now);

    /** 수강 건의 강의 id — 강의 분반 행을 잠그기 전에 엔티티를 읽어 두지 않으려고 값만 조회한다 */
    @Query(value = "SELECT course_id FROM enrollments WHERE id = :enrollmentId", nativeQuery = true)
    Optional<Long> findCourseIdById(@Param("enrollmentId") Long enrollmentId);
}
