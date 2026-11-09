package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] course_sections 표 접근 — 강의 단위 행 잠금(신청·취소·자리 채우기·정원·담당·만료를 한 강의씩 줄 세우는 열쇠) + 분반 생성용 조회
 * [담당] 팀원 2 · #09(잠금) · #10(분반 id → 강의 id, 다음 번호, 이름 중복)
 * [연결]
 *  - 호출받음: EnrollmentService(apply·cancel·complete·closeAfterRejection), SeatService.fillVacancies,
 *             SectionService(#10 생성·정원·담당), EnrollmentExpiryService(#10 만료)
 * [기술] 🟢 JpaRepository 쿼리 메서드, 🟢 @Query(nativeQuery = true) + SELECT … FOR UPDATE (@Lock은 배우지 않아 사용하지 않음)
 */
@Repository
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    /**
     * 한 강의의 분반 행을 모두 잠근다(트랜잭션이 끝날 때까지). 같은 강의를 건드리는 다른 신청·취소는 여기서 기다린다.
     * 호출: EnrollmentService.apply·cancel·complete·closeAfterRejection, SeatService.fillVacancies,
     *      SectionService.create·changeCapacity·changeInstructor(#10), EnrollmentExpiryService.expireOne(#10)
     * 순서 규칙: 수강·대기 행을 바꾸기 "전에" 이 잠금을 가장 먼저 잡는다 — 모든 흐름이 같은 순서로 잠가야 교착(deadlock)이 생기지 않는다.
     * 같은 트랜잭션에서 다시 불러도 이미 가진 잠금이라 기다리지 않는다.
     */
    @Query(value = "SELECT * FROM course_sections WHERE course_id = :courseId ORDER BY section_no FOR UPDATE",
            nativeQuery = true)
    List<CourseSection> findByCourseIdForUpdate(@Param("courseId") Long courseId);

    /**
     * 분반이 속한 강의 id만 읽는다 — 잠금 전에 엔티티를 읽어 두면 잠근 뒤에도 옛 값이 남기 때문(#10 정원·담당 변경).
     * CAST 이유는 WaitlistEntryRepository.findCourseIdById 주석과 같다(BIGINT UNSIGNED → Long).
     */
    @Query(value = "SELECT CAST(course_id AS SIGNED) FROM course_sections WHERE id = :sectionId", nativeQuery = true)
    Optional<Long> findCourseIdById(@Param("sectionId") Long sectionId);

    /** 강의 안 다음 분반 번호 (없으면 1) — 강의 분반 행을 잠근 뒤 불러야 번호가 겹치지 않는다(#10 SE-02) */
    @Query(value = "SELECT CAST(COALESCE(MAX(section_no), 0) + 1 AS SIGNED) FROM course_sections WHERE course_id = :courseId",
            nativeQuery = true)
    int findNextSectionNo(@Param("courseId") Long courseId);

    /** 강의 안 분반 이름 중복 여부 (DB UNIQUE uq_sections_name, #10 SE-02) */
    boolean existsByCourseIdAndName(Long courseId, String name);
}
