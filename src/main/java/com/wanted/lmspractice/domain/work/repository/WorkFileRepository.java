package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * WorkFile 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #16
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface WorkFileRepository extends JpaRepository<WorkFile, Long> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
