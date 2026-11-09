package com.wanted.lmspractice.domain.user.repository;

import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrant;
import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrantId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] instructor_hobby_grants 표 조회·저장 (강사의 세부 취미 활동 권한)
 * [담당] 팀원 1 · #05   [API] AI-01·05·06, 계약 C3
 * [연결]
 *  - 호출받음: InstructorAccountService(목록 표시, 부여·회수, hasHobbyGrant)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드
 * 복합 기본키 → ID 타입은 InstructorHobbyGrantId (findById(new InstructorHobbyGrantId(…)) 로 조회)
 * 회수한 권한은 행을 지우지 않고 is_active = false로 남기므로, 유효한 권한만 볼 때는 IsActiveTrue 조건을 붙인다.
 */
@Repository
public interface InstructorHobbyGrantRepository extends JpaRepository<InstructorHobbyGrant, InstructorHobbyGrantId> {

    /** 여러 강사의 유효한 권한을 한 번에 조회 (강사 목록 화면 — 강사마다 따로 조회하지 않으려고) */
    List<InstructorHobbyGrant> findByInstructorIdInAndIsActiveTrue(Collection<Long> instructorIds);

    /** 계약 C3 hasHobbyGrant의 실제 조회 */
    boolean existsByInstructorIdAndHobbyIdAndIsActiveTrue(Long instructorId, Long hobbyId);
}
