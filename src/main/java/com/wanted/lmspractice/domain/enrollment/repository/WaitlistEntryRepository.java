package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * WaitlistEntry 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 2 · #09
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
