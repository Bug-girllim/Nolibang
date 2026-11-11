package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.Work;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * [역할] works 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16 (#19 타임라인 조회 추가)
 * [연결] 호출받음: WorkService, GrowthService(#19)
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    /** 수강당 최종 과제는 1개(works.enrollment_id UNIQUE) — 있으면 재제출, 없으면 최초 제출 */
    Optional<Work> findByEnrollmentId(Long enrollmentId);

    /**
     * 성장 타임라인(#19, U-S12): 본인의 삭제되지 않은 작품(최종 과제 + 일반 작품)을 등록일 최신순으로.
     * created_at 은 DB가 처음 넣을 때만 기록(updatable = false)하므로 작품을 수정해도 순서가 바뀌지 않는다.
     * 같은 시각이면 id 가 큰(나중에 넣은) 작품이 위.
     */
    List<Work> findByAuthorIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long authorId);

    /**
     * 회원 탈퇴 1단계 — 탈퇴자 작품이 가리키는 평가(reference_review_id)·표시 버전(display_version_id) 연결을 끊는다.
     * 평가 회차·버전을 지우기 전에 불러야 FK(fk_works_reference·fk_works_display)가 깨지지 않는다.
     * 호출: WorkService.detachAllOfAuthorForWithdrawal
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE works SET reference_review_id = NULL, display_version_id = NULL WHERE author_id = :authorId",
            nativeQuery = true)
    int detachAllOfAuthor(@Param("authorId") Long authorId);

    /**
     * 탈퇴자가 남의 작품에 누른 좋아요를 그 작품의 익명 좋아요 수로 옮긴다(U-15 8번 — 집계는 유지, 계정 연결은 제거).
     * 이 다음에 WorkLikeRepository.deleteAllByUserId 로 좋아요 행을 지운다. 호출: CommentService.anonymizeLikesForWithdrawal
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE works w JOIN work_likes l ON l.work_id = w.id"
            + " SET w.anonymous_like_count = w.anonymous_like_count + 1"
            + " WHERE l.user_id = :userId AND w.author_id <> :userId",
            nativeQuery = true)
    int addAnonymousLikesOf(@Param("userId") Long userId);

    /** 회원 탈퇴 마지막 단계 — 탈퇴자 작품 행 삭제(일반·최종 과제 모두). 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM works WHERE author_id = :authorId", nativeQuery = true)
    int deleteAllOfAuthor(@Param("authorId") Long authorId);
}
