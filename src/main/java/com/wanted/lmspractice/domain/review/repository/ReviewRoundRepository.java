package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] review_rounds 표 접근 — 수강 건의 제출 회차 이력, 제출·재제출·수정 전 잠금 읽기
 * [담당] 팀원 3 · #13 (#15 결과 화면 조회 추가, 평가 목록·통계용 조회는 #14에서 추가)
 * [연결]
 *  - 호출받음: AssignmentService(#13 화면 이력·제출·재제출·수정), ReviewResultService(#15 결과 화면)
 * [기술] 🟢 쿼리 메서드(findBy…OrderBy…), 🟢 @Query(nativeQuery = true) + FOR UPDATE
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
     * 호출: AssignmentService.edit — 강사가 같은 순간 평가를 시작(#14, WAITING → IN_REVIEW)해도
     * 둘 중 늦게 온 쪽이 바뀐 상태를 보게 해서, 평가가 시작된 제출물이 고쳐지는 일을 막는다(SUBMISSION_LOCKED).
     */
    @Query(value = "SELECT * FROM review_rounds WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ReviewRound> findByIdForUpdate(@Param("id") Long id);
}
