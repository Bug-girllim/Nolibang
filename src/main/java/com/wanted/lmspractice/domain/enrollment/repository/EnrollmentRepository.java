package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] enrollments 표 조회·저장 (Spring Data JPA 쿼리 메서드)
 * [담당] 팀원 2 · #09 (#11에서 본인 수강 목록 조회 추가)
 * [연결]
 *  - 호출받음: EnrollmentService(#09 신청·취소·수료, #11 getMyEnrollments)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 한 학습자의 모든 수강 건(진행·수료·취소·만료)을 최근 확정 순으로. 본인 것만 조회하려고 항상 userId로 찾는다 (#11) */
    List<Enrollment> findByUserIdOrderByConfirmedAtDesc(Long userId);
}
