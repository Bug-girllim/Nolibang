package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ReviewRound 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 3 · #13
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
