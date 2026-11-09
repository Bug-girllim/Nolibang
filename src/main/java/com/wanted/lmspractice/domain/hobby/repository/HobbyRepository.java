package com.wanted.lmspractice.domain.hobby.repository;

import com.wanted.lmspractice.domain.hobby.entity.Hobby;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] hobbies 표 조회 (Spring Data JPA 쿼리 메서드) — 세부 취미는 시드 데이터라 조회만 한다 (D-59)
 * [담당] 팀원 2 · #08
 * [연결]
 *  - 호출받음: HobbyService(세부 취미 목록·상세·노출 규칙)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드(정렬 포함)
 */
@Repository
public interface HobbyRepository extends JpaRepository<Hobby, Long> {

    /** 전체 세부 취미를 표시 순서대로 (대분류별로 묶는 일은 서비스가 한다) */
    List<Hobby> findAllByOrderBySortOrderAscIdAsc();
}
