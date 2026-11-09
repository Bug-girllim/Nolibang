package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.Work;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] works 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16
 * [연결] 호출받음: WorkService
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    /** 수강당 최종 과제는 1개(works.enrollment_id UNIQUE) — 있으면 재제출, 없으면 최초 제출 */
    Optional<Work> findByEnrollmentId(Long enrollmentId);
}
