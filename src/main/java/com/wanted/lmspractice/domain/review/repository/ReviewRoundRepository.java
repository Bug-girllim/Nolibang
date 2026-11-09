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
 * [역할] review_rounds 표 접근 — 수강 건의 제출 회차 이력, 제출·재제출·수정·평가 전 잠금 읽기, 계약 C11 조회
 * [담당] 팀원 3 · #13 (#15 결과 화면 조회 추가, #14 평가 잠금·C11 조회 추가)
 * [연결]
 *  - 호출받음: AssignmentService(#13 화면 이력·제출·재제출·수정), ReviewResultService(#15 결과 화면),
 *             ReviewService(#14 평가 시작·취소·초안·승인·반려 잠금), ReviewQueryService(#14 계약 C11)
 * [기술] 🟢 쿼리 메서드(findBy…OrderBy…·existsBy…), 🟢 @Query(nativeQuery = true) + FOR UPDATE (@Lock은 배우지 않아 사용하지 않음)
 */
@Repository
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    /**
     * 수강 건의 제출 회차를 최근 회차부터. 화면 이력·현재 상태 판단용(잠그지 않는 읽기).
     * 호출: AssignmentService.getAssignmentView
     */
    List<ReviewRound> findByEnrollmentIdOrderByRoundNoDesc(Long enrollmentId);

    /** [#15 추가] 한 수강 건의 평가 회차 전체 (회차 순) — 결과 화면의 제출·평가 이력, 최신 회차 판단 */
    List<ReviewRound> findByEnrollmentIdOrderByRoundNoAsc(Long enrollmentId);

    /**
     * 수강 건의 제출 회차를 최근 회차부터 잠그며 최신 값으로 읽는다.
     * 호출: AssignmentService.submit·resubmit — 수강 건 잠금(EnrollmentService.lockOwnedEnrollment)을 잡은 "뒤에" 부른다.
     * 왜 잠금 읽기인가: MySQL(REPEATABLE READ)의 일반 SELECT는 트랜잭션 첫 조회 시점 사진을 본다. 제출 버튼을 연달아 누르면
     * 뒤 요청은 앞 요청이 끝날 때까지 수강 건 잠금에서 기다리는데, 일반 SELECT라면 앞 요청이 만든 평가 대기 회차를 못 보고 한 번 더 저장하려 한다.
     * 잠금 읽기는 항상 최신 확정 값을 읽으므로 뒤 요청이 ALREADY_SUBMITTED로 막힌다(DB uq_reviews_active는 마지막 방어선).
     */
    @Query(value = "SELECT * FROM review_rounds WHERE enrollment_id = :enrollmentId ORDER BY round_no DESC FOR UPDATE",
            nativeQuery = true)
    List<ReviewRound> findByEnrollmentIdForUpdate(@Param("enrollmentId") Long enrollmentId);

    /**
     * 회차 한 건을 잠그며 최신 값으로 읽는다.
     * 호출: AssignmentService.edit(#13) — 강사가 같은 순간 평가를 시작해도 늦게 온 쪽이 바뀐 상태를 보게 해서 평가가 시작된 제출물 수정을 막는다(SUBMISSION_LOCKED).
     *       ReviewService.lockAssignedRound(#14) — 시작·취소·초안·승인·반려가 같은 행을 동시에 바꾸지 못하게 한다.
     *       승인 2번 동시 클릭: 두 번째 요청은 첫 번째 커밋까지 기다린 뒤 APPROVED를 보고 INVALID_REVIEW_STATE가 된다(수료 1회).
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
