package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.Work;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] works 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16 (#19 타임라인 조회 추가)
 * [연결] 호출받음: WorkService, GrowthService(#19)
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    /** 수강당 최종 과제는 1개(works.enrollment_id UNIQUE) — 있으면 재제출, 없으면 최초 제출 */
    Optional<Work> findByEnrollmentId(Long enrollmentId);

    /**
     * 성장 타임라인(#19, U-S12): 본인의 삭제되지 않은 작품(최종 과제 + 일반 작품)을 등록일 최신순으로.
     * created_at 은 DB가 처음 넣을 때만 기록(updatable = false)하므로 작품을 수정해도 순서가 바뀌지 않는다.
     * 같은 시각이면 id 가 큰(나중에 넣은) 작품이 위.
     */
    List<Work> findByAuthorIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long authorId);
}
