package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkLike;
import com.wanted.lmspractice.domain.work.entity.WorkLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_likes 표 접근 (Spring Data JPA, 복합 키 WorkLikeId)
 * [담당] 팀원 4 · #18
 * [연결] 호출받음: CommentService(좋아요 토글), WorkBoardService(상세의 좋아요 수·내가 눌렀는지)
 */
@Repository
public interface WorkLikeRepository extends JpaRepository<WorkLike, WorkLikeId> {

    long countByWorkId(Long workId);

    /** 작품의 좋아요 전부 삭제 (D-32 일반 작품 삭제 — 좋아요 취소와 같이 행을 지운다). 호출: WorkService.deleteGeneralWork */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkLike l WHERE l.workId = :workId")
    int deleteByWorkId(@Param("workId") Long workId);

    boolean existsByWorkIdAndUserId(Long workId, Long userId);

    /** 회원이 누른 좋아요 행 전부 삭제(회원 탈퇴 — 계정 연결 제거). 호출: CommentService.anonymizeLikesForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM work_likes WHERE user_id = :userId", nativeQuery = true)
    int deleteAllByUserId(@Param("userId") Long userId);

    /** 탈퇴자 작품에 달린 좋아요 전부 삭제(작품이 지워지므로). 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE l FROM work_likes l JOIN works w ON w.id = l.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    int deleteAllOnWorksOfAuthor(@Param("authorId") Long authorId);
}
