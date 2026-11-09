package com.wanted.lmspractice.domain.inquiry.repository;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * InquiryThread 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #21
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface InquiryThreadRepository extends JpaRepository<InquiryThread, Long> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
