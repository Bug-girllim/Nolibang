package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ReviewRound 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 3 · #13 (#15에서 쿼리 메서드 추가)
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    /** [#15 추가] 한 수강 건의 평가 회차 전체 (회차 순) — 결과 화면의 제출·평가 이력, 최신 회차 판단 */
    List<ReviewRound> findByEnrollmentIdOrderByRoundNoAsc(Long enrollmentId);
}
