package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] course_sections 표 조회·저장 + 강의 단위 행 잠금
 * [담당] 팀원 2 · #09·#10
 * [연결]
 *  - 호출받음: SectionService(#10 생성·정원·담당), EnrollmentExpiryService(#10 만료), #09 신청·자동 확정
 *  - 잠금 규칙: 자리를 바꾸는 모든 처리(신청·취소·수료·만료·정원·담당)는 lockByCourseId로 "강의의 분반 행 전체"를 id 순서로 먼저 잠근다.
 *             잠그는 범위·순서가 같아야 스케줄러와 사용자 요청이 동시에 와도 서로 엇갈려 기다리는(교착) 일이 없다(SC-01 동시성).
 * [기술] 🟢 JpaRepository 쿼리 메서드, 🟢 @Query(nativeQuery) SELECT … FOR UPDATE (@Lock은 안 배움)
 */
@Repository
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    /** 한 강의의 분반 행을 id 순서로 잠근다(트랜잭션 끝까지). 호출한 쪽에 @Transactional이 있어야 한다 */
    @Query(value = "SELECT * FROM course_sections WHERE course_id = :courseId ORDER BY id FOR UPDATE", nativeQuery = true)
    List<CourseSection> lockByCourseId(@Param("courseId") Long courseId);

    /** 분반이 속한 강의 id — 잠금 전에 엔티티를 읽어 두지 않으려고 값만 조회한다 */
    @Query(value = "SELECT course_id FROM course_sections WHERE id = :sectionId", nativeQuery = true)
    Optional<Long> findCourseIdById(@Param("sectionId") Long sectionId);

    /** 강의 안 다음 분반 번호 (없으면 1) — 강의 분반 행을 잠근 뒤 불러야 번호가 겹치지 않는다 */
    @Query(value = "SELECT COALESCE(MAX(section_no), 0) + 1 FROM course_sections WHERE course_id = :courseId", nativeQuery = true)
    int findNextSectionNo(@Param("courseId") Long courseId);

    /** 강의 안 분반 이름 중복 여부 (DB UNIQUE uq_sections_name) */
    boolean existsByCourseIdAndName(Long courseId, String name);
}
