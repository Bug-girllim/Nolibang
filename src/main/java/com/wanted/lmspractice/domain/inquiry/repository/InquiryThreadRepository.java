package com.wanted.lmspractice.domain.inquiry.repository;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryThread;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * InquiryThread 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #21
 */
@Repository
public interface InquiryThreadRepository extends JpaRepository<InquiryThread, Long> {

    /** 수강 건의 스레드 (수강 건 하나에 스레드 하나로 쓴다) */
    Optional<InquiryThread> findFirstByEnrollmentIdOrderByIdAsc(Long enrollmentId);

    /** 학습자 본인의 스레드 전체 — 최근 메시지 순 */
    List<InquiryThread> findByLearnerIdOrderByLastMessageAtDescIdDesc(Long learnerId);
}
