package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] ★ 자리 반환 → 대기 1순위 자동 확정 → 알림 (호출자 트랜잭션 안에서 실행) + 강의별 정원 현황 조회(#08)
 * [담당] 팀원 2 · #09 (getSeatSummary 조회 계약은 #08에서 추가)
 * 자리가 생기는 4가지(수료·수강 취소·만료·정원 증가/담당 지정)가 모두 fillVacancies로 모인다.
 * [연결]
 *  - 호출받음: EnrollmentService(#09), SectionService(#10) → fillVacancies / CourseCatalogService(#08) → getSeatSummary
 *  - 호출함:   DB 뷰 v_section_seats, 표 waitlist_entries (EntityManager native 조회 — 읽기 전용)
 * [기술] 🟢 EntityManager native 쿼리(InstructorStatQuery 방식)
 */
@Service
public class SeatService {

    @PersistenceContext
    private EntityManager entityManager;

    /** 계약 C4: 남은 자리가 있는 동안 대기 1순위부터 수강 확정, 확정 인원 수 반환 */
    public int fillVacancies(Long courseId) {
        // TODO #09: 구현 (현재는 스텁)
        return 0;
    }

    /**
     * 계약 C16(#08 추가): 한 강의의 정원 합계·남은 자리 합계·대기 인원 — 강의 목록·상세 화면용 조회.
     * 신청 가능한 분반(담당 강사가 있는 분반, v_section_seats.assignable)만 센다. 담당 강사가 없는 분반은 신청 대상이 아니다(D-52).
     * 호출: CourseCatalogService.getCourses·getCourseDetail
     * 읽기 전용이라 호출한 쪽 트랜잭션이 없어도 된다(정원 확정·대기 순번의 동시성 처리는 #09 신청 로직의 몫).
     */
    @Transactional(readOnly = true)
    public SeatSummaryDTO getSeatSummary(Long courseId) {
        Object[] seats = (Object[]) entityManager.createNativeQuery(
                        "SELECT COALESCE(SUM(capacity), 0), COALESCE(SUM(remaining), 0) "
                                + "FROM v_section_seats WHERE course_id = :courseId AND assignable = 1")
                .setParameter("courseId", courseId)
                .getSingleResult();
        Number waiting = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM waitlist_entries WHERE course_id = :courseId AND status = 'WAITING'")
                .setParameter("courseId", courseId)
                .getSingleResult();
        return new SeatSummaryDTO(((Number) seats[0]).intValue(), ((Number) seats[1]).intValue(), waiting.intValue());
    }
}
