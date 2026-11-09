package com.wanted.lmspractice.domain.course.repository;

import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] courses 표 조회 (Spring Data JPA 쿼리 메서드) — 강의는 시드 데이터라 조회만 한다 (D-59)
 * [담당] 팀원 2 · #08
 * [연결]
 *  - 호출받음: CourseService(강의 목록·상세·관리자 집계)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드. level 정렬은 DB의 1·2·3 값 기준(입문→중급→고급)이다
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 한 세부 취미의 강의를 수준 순서(입문→고급)로 */
    List<Course> findByHobbyIdOrderByLevelAsc(Long hobbyId);

    /** 한 세부 취미의 특정 수준 강의 (수준 필터, CO-01) */
    List<Course> findByHobbyIdAndLevelOrderByLevelAsc(Long hobbyId, CourseLevel level);

    /** 한 세부 취미의 전체 강의 수 (관리자 A-S15) */
    long countByHobbyId(Long hobbyId);

    /** 한 세부 취미의 공개(OPEN) 강의 수 (관리자 A-S15) */
    long countByHobbyIdAndStatus(Long hobbyId, CourseStatus status);
}
