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
 * [역할] review_rounds 표 접근 (Spring Data JPA)
 * [담당] 팀원 3 · #13 (#15·#14에서 쿼리 메서드 추가)
 * [연결]
 *  - 호출받음: ReviewService(#14 잠금 조회), ReviewQueryService(#14 계약 C11), ReviewResultService(#15)
 * [기술] 🟢 쿼리 메서드, 🟢 @Query(nativeQuery = true) + SELECT … FOR UPDATE (@Lock은 배우지 않아 사용하지 않음)
 */
@Repository
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    /** [#15 추가] 한 수강 건의 평가 회차 전체 (회차 순) — 결과 화면의 제출·평가 이력, 최신 회차 판단 */
    List<ReviewRound> findByEnrollmentIdOrderByRoundNoAsc(Long enrollmentId);

    /**
     * [#14 추가] 평가 회차 1행을 잠그고 읽는다 — 시작·취소·초안·승인·반려가 같은 행을 동시에 바꾸지 못하게 한다.
     * 승인 2번 동시 클릭: 두 번째 요청은 첫 번째 커밋까지 기다린 뒤 APPROVED를 보고 INVALID_REVIEW_STATE가 된다(수료 1회).
     * 호출: ReviewService.lockRound
     */
    @Query(value = "SELECT * FROM review_rounds WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ReviewRound> findByIdForUpdate(@Param("id") Long id);

    /** [#14 추가] 계약 C11 hasPendingReview — 평가 대기·진행 중 회차가 있는지 */
    boolean existsByEnrollmentIdAndStatusIn(Long enrollmentId, Collection<ReviewStatus> statuses);

    /** [#14 추가] 계약 C11 getLatestStatus — 가장 최근 회차 */
    Optional<ReviewRound> findFirstByEnrollmentIdOrderByRoundNoDesc(Long enrollmentId);

    /** [#14 추가] 계약 C11 getLatestStatus — 1회차(최초 제출 시각) */
    Optional<ReviewRound> findFirstByEnrollmentIdOrderByRoundNoAsc(Long enrollmentId);
}
