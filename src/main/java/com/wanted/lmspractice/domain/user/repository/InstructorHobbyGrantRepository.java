package com.wanted.lmspractice.domain.user.repository;

import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrant;
import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * InstructorHobbyGrant 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 1 · #05
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 * 복합 기본키 → ID 타입은 InstructorHobbyGrantId (findById(new InstructorHobbyGrantId(…)) 로 조회)
 */
@Repository
public interface InstructorHobbyGrantRepository extends JpaRepository<InstructorHobbyGrant, InstructorHobbyGrantId> {

    // TODO: 쿼리 메서드는 담당 이슈에서 추가
}
