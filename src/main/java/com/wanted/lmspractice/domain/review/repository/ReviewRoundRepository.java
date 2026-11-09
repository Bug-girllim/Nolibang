package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] review_rounds 표 조회·저장 (Spring Data JPA)
 * [담당] 팀원 3 · #13(제출) · #14(평가 쿼리 추가)
 * [연결]
 *  - 호출받음: ReviewService(#14 잠금 조회·이력), ReviewQueryService(계약 C11), #13 AssignmentService
 * [기술] 🟢 쿼리 메서드(existsBy…·findBy…OrderBy…), 🟢 @Query(nativeQuery = true) SELECT … FOR UPDATE (@Lock은 안 배움)
 */
@Repository
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    /**
     * 평가 회차 한 행을 잠그고 최신 값으로 읽는다 — 상태를 바꾸는 모든 평가 요청(시작·취소·초안·승인·반려)이 맨 먼저 부른다.
     * 동시에 승인 2번: 두 번째 요청은 첫 번째 커밋까지 기다린 뒤 APPROVED를 보고 INVALID_REVIEW_STATE로 끝난다(수료 1회).
     * 호출: ReviewService.lockRound
     */
    @Query(value = "SELECT * FROM review_rounds WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ReviewRound> findByIdForUpdate(@Param("id") Long id);

    /** 수강 건에 평가 대기·진행 중인 회차가 있는지 — 계약 C11 hasPendingReview (uq_reviews_active로 최대 1건) */
    boolean existsByEnrollmentIdAndStatusIn(Long enrollmentId, Collection<ReviewStatus> statuses);

    /** 수강 건의 모든 회차(1회차부터) — 평가 상세의 이전 회차 이력, 계약 C11 getLatestStatus(최초 제출 시각·최신 회차) */
    List<ReviewRound> findByEnrollmentIdOrderByRoundNoAsc(Long enrollmentId);
}
