package com.wanted.lmspractice.domain.course.repository;

import com.wanted.lmspractice.domain.course.entity.Lesson;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] lessons 표 조회 (Spring Data JPA 쿼리 메서드) — 차시는 시드 데이터라 조회만 한다 (D-59)
 * [담당] 팀원 2 · #08
 * [연결]
 *  - 호출받음: CourseService(강의 상세의 차시 목록, 관리자 차시 수 집계)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드
 */
@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /** 한 강의의 차시를 1·2·3차시 순서로 */
    List<Lesson> findByCourseIdOrderByLessonNoAsc(Long courseId);

    /** [성능 개선] 여러 강의의 차시를 한 번에 — 강의별로 묶었을 때 차시 번호 순서가 되도록 정렬. 호출: CourseService.getLessonsOfCourses */
    List<Lesson> findByCourseIdInOrderByCourseIdAscLessonNoAsc(Collection<Long> courseIds);

    /** 여러 강의에 속한 차시 수 (관리자 A-S15의 세부 취미별 차시 수) */
    long countByCourseIdIn(Collection<Long> courseIds);

    /** 이 영상 파일을 쓰는 차시가 있는지 — 연결된 영상은 삭제하지 않는다 (LessonVideoService.delete) */
    boolean existsByVideoKey(String videoKey);
}
