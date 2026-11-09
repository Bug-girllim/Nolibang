package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_files 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16
 * [연결] 호출받음: WorkService
 */
@Repository
public interface WorkFileRepository extends JpaRepository<WorkFile, Long> {

    /** 한 버전의 이미지를 보여 줄 순서대로 */
    List<WorkFile> findByWorkVersionIdOrderBySortOrder(Long workVersionId);

    /** 이미지 교체 때 이전 행 삭제 — 호출 뒤 flush 해야 같은 sort_order 새 행을 넣을 수 있다 */
    void deleteByWorkVersionId(Long workVersionId);
}
