package com.wanted.lmspractice.domain.learning.repository;

import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import com.wanted.lmspractice.domain.learning.entity.LessonProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * LessonProgress 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 3 · #12
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 * 복합 기본키 → ID 타입은 LessonProgressId (findById(new LessonProgressId(…)) 로 조회)
 */
@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgressId> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
