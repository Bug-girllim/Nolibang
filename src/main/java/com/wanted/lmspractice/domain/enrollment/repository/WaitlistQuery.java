package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.dto.WaitlistDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * [역할] 관리자 대기열 조회 전용 — 대기 중 목록(FIFO), 최근 자동 확정 내역, 최근 7일 자동 확정 수
 * [담당] 팀원 2 · #10   [화면] A-S12   [API] WL-01 모델 waitlist·recentConfirmed
 * [연결]
 *  - 호출받음: WaitlistService(관리자 조회)
 *  - 호출함:   표 waitlist_entries·users·enrollments·course_sections (읽기만)
 *  - 회원 표(users)는 실명·아이디 표시용 읽기 전용 JOIN — 회원 Repository를 주입하지 않기 위함
 * [기술] 🟢 EntityManager native 쿼리 (InstructorStatQuery 방식)
 */
@Repository
public class WaitlistQuery {

    @PersistenceContext
    private EntityManager entityManager;

    /** 한 강의의 대기 중(WAITING) 목록 — 신청 순서(id) 그대로, 순번은 1부터 매긴다(D-53 FIFO) */
    public List<WaitlistDTO> findWaiting(Long courseId) {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT w.id, w.user_id, u.real_name, u.login_id, w.created_at "
                                + "FROM waitlist_entries w JOIN users u ON u.id = w.user_id "
                                + "WHERE w.course_id = :courseId AND w.status = 'WAITING' ORDER BY w.id")
                .setParameter("courseId", courseId)
                .getResultList();
        List<WaitlistDTO> result = new ArrayList<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            result.add(new WaitlistDTO(toLong(r[0]), result.size() + 1, toLong(r[1]),
                    (String) r[2], (String) r[3], toDateTime(r[4]), null, null));
        }
        return result;
    }

    /** 한 강의의 최근 자동 확정(CONFIRMED) 내역 — 확정 시각 최신순 limit건, 배정된 분반 이름 포함 */
    public List<WaitlistDTO> findRecentConfirmed(Long courseId, int limit) {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT w.id, w.user_id, u.real_name, u.login_id, w.created_at, w.confirmed_at, s.section_no, s.name "
                                + "FROM waitlist_entries w "
                                + "JOIN users u ON u.id = w.user_id "
                                + "JOIN enrollments e ON e.id = w.enrollment_id "
                                + "JOIN course_sections s ON s.id = e.section_id "
                                + "WHERE w.course_id = :courseId AND w.status = 'CONFIRMED' "
                                + "ORDER BY w.confirmed_at DESC, w.id DESC LIMIT :limit")
                .setParameter("courseId", courseId)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream()
                .map(row -> (Object[]) row)
                .map(r -> new WaitlistDTO(toLong(r[0]), 0, toLong(r[1]), (String) r[2], (String) r[3],
                        toDateTime(r[4]), toDateTime(r[5]),
                        SectionDTO.displayName(toLong(r[6]).intValue(), (String) r[7])))
                .toList();
    }

    /**
     * 한 강의에서 최근 days일 동안 자동 확정된 수.
     * 기준 시각은 DB의 UTC_TIMESTAMP — confirmed_at이 UTC로 저장되므로 같은 기준으로 비교한다(DB 시간 규칙).
     */
    public int countConfirmedWithinDays(Long courseId, int days) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM waitlist_entries "
                                + "WHERE course_id = :courseId AND status = 'CONFIRMED' "
                                + "AND confirmed_at >= UTC_TIMESTAMP(6) - INTERVAL :days DAY")
                .setParameter("courseId", courseId)
                .setParameter("days", days)
                .getSingleResult();
        return count.intValue();
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /**
     * DATETIME 칼럼 값 → LocalDateTime. Hibernate 버전에 따라 Timestamp 또는 LocalDateTime으로 오므로 둘 다 받는다.
     * hibernate.jdbc.time_zone=UTC 설정이 native 조회에도 적용되어 엔티티로 읽은 값과 같은 시각이 된다(테스트로 확인).
     */
    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }
}
