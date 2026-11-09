package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] course_sections 표 접근 — 강의 단위 행 잠금(신청·취소·자리 채우기를 한 강의씩 줄 세우는 열쇠)
 * [담당] 팀원 2 · #09 (분반 생성·정원 변경 쿼리는 #10에서 추가)
 * [연결]
 *  - 호출받음: EnrollmentService(apply·cancel·complete·closeAfterRejection), SeatService.fillVacancies, EnrollmentService.getStatusForUser(분반 이름)
 * [기술] 🟢 JpaRepository, 🟢 @Query(nativeQuery = true) + SELECT … FOR UPDATE (@Lock은 배우지 않아 사용하지 않음)
 */
@Repository
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    /**
     * 한 강의의 분반 행을 모두 잠근다(트랜잭션이 끝날 때까지). 같은 강의를 건드리는 다른 신청·취소는 여기서 기다린다.
     * 호출: EnrollmentService.apply·cancel·complete·closeAfterRejection, SeatService.fillVacancies
     * 순서 규칙: 수강·대기 행을 바꾸기 "전에" 이 잠금을 가장 먼저 잡는다 — 모든 흐름이 같은 순서로 잠가야 교착(deadlock)이 생기지 않는다.
     * 같은 트랜잭션에서 다시 불러도 이미 가진 잠금이라 기다리지 않는다.
     */
    @Query(value = "SELECT * FROM course_sections WHERE course_id = :courseId ORDER BY section_no FOR UPDATE",
            nativeQuery = true)
    List<CourseSection> findByCourseIdForUpdate(@Param("courseId") Long courseId);
}
