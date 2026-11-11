package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_comments 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #18
 * [연결] 호출받음: CommentService, WorkBoardService
 */
@Repository
public interface WorkCommentRepository extends JpaRepository<WorkComment, Long> {

    /** 작품의 댓글·답글 전부 — 작성 순서(id)대로. 댓글 아래 답글 묶기는 서비스가 한다 */
    List<WorkComment> findByWorkIdOrderById(Long workId);

    /**
     * 회원이 남의 작품에 단 댓글·답글 전부(자발 삭제한 것 포함) — 회원 탈퇴 때 익명화할 대상.
     * 본인 작품의 댓글은 작품과 함께 지워지므로 뺀다. 호출: CommentService.anonymizeCommentsForWithdrawal
     */
    @Query(value = "SELECT c.* FROM work_comments c JOIN works w ON w.id = c.work_id"
            + " WHERE c.author_id = :userId AND w.author_id <> :userId ORDER BY c.id",
            nativeQuery = true)
    List<WorkComment> findAllOnOthersWorksByAuthor(@Param("userId") Long userId);

    /**
     * 탈퇴자 작품에 달린 댓글·답글 전부 삭제(작성자 무관 — 게시물이 지워지면 반응도 지운다, 02-workflow 198행).
     * 답글이 부모 댓글을 가리키므로(fk_comments_parent) 답글을 먼저 지우고 댓글을 지운다.
     * 호출: WorkService.deleteAllOfAuthorForWithdrawal
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE c FROM work_comments c JOIN works w ON w.id = c.work_id"
            + " WHERE w.author_id = :authorId AND c.parent_id IS NOT NULL",
            nativeQuery = true)
    int deleteRepliesOnWorksOfAuthor(@Param("authorId") Long authorId);

    /** deleteRepliesOnWorksOfAuthor 다음에 부르는 나머지(최상위 댓글) 삭제 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE c FROM work_comments c JOIN works w ON w.id = c.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    int deleteAllOnWorksOfAuthor(@Param("authorId") Long authorId);
}
