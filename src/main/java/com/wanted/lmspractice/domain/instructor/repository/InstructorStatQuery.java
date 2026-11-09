package com.wanted.lmspractice.domain.instructor.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * 분반별 통계 집계 전용 조회 (EntityManager native GROUP BY)
 * 담당: 팀원 1 · #07
 * 다른 팀원의 Repository 파일을 수정하지 않기 위해 별도 클래스로 둔다 (JPA chap03 방식).
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Repository
public class InstructorStatQuery {

    @PersistenceContext
    private EntityManager entityManager;

    // TODO #07: 분반별 수강 중·수료·반려·평가 대기 수 집계 쿼리
}
