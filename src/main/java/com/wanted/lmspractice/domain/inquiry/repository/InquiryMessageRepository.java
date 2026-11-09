package com.wanted.lmspractice.domain.inquiry.repository;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * InquiryMessage 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #21
 */
@Repository
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    /** 스레드의 메시지 — 오래된 순(대화 순서) */
    List<InquiryMessage> findByThreadIdOrderByIdAsc(Long threadId);
}
