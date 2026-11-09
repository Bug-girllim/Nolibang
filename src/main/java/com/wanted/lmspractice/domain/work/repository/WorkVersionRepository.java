package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 작품 버전 조회·저장
 * 담당: 팀원 4 · #16
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface WorkVersionRepository extends JpaRepository<WorkVersion, Long> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
