package com.wanted.lmspractice.domain.inquiry.repository;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * InquiryMessage 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #21
 */
@Repository
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    /** 스레드의 메시지 — 오래된 순(대화 순서) */
    List<InquiryMessage> findByThreadIdOrderByIdAsc(Long threadId);

    /** 학습자 스레드의 메시지 전부 삭제(강사 답변 포함) — 회원 탈퇴 시 스레드 통째 삭제(U-15 11번). 호출: InquiryService.deleteAllOfLearnerForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE m FROM inquiry_messages m JOIN inquiry_threads t ON t.id = m.thread_id WHERE t.learner_id = :learnerId",
            nativeQuery = true)
    int deleteAllOfLearner(@Param("learnerId") Long learnerId);
}
