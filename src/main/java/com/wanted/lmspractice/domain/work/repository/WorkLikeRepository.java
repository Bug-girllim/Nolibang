package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkLike;
import com.wanted.lmspractice.domain.work.entity.WorkLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_likes 표 접근 (Spring Data JPA, 복합 키 WorkLikeId)
 * [담당] 팀원 4 · #18
 * [연결] 호출받음: CommentService(좋아요 토글), WorkBoardService(상세의 좋아요 수·내가 눌렀는지)
 */
@Repository
public interface WorkLikeRepository extends JpaRepository<WorkLike, WorkLikeId> {

    long countByWorkId(Long workId);

    boolean existsByWorkIdAndUserId(Long workId, Long userId);
}
