package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
