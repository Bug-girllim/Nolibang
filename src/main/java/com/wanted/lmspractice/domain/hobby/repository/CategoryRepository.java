package com.wanted.lmspractice.domain.hobby.repository;

import com.wanted.lmspractice.domain.hobby.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] categories 표 조회 (Spring Data JPA 쿼리 메서드) — 대분류는 시드 데이터라 조회만 한다 (D-59)
 * [담당] 팀원 2 · #08
 * [연결]
 *  - 호출받음: HobbyService(대분류 목록·노출 규칙)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드(정렬 포함)
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 표시 순서(sort_order)대로 전체 대분류. 같으면 id 순 */
    List<Category> findAllByOrderBySortOrderAscIdAsc();

    /** 대분류 추가 시 code 중복 확인 */
    boolean existsByCode(String code);

    /** 대분류 추가 시 이름 중복 확인 (A-S02) */
    boolean existsByName(String name);

    /** 새 대분류를 맨 뒤에 두기 위한 현재 가장 큰 정렬 순서 */
    Optional<Category> findTopByOrderBySortOrderDesc();

    /** 대분류 편집 시 이름 중복 확인 — 자기 자신은 빼고 본다 (A-S02) */
    boolean existsByNameAndIdNot(String name, Long id);
}
