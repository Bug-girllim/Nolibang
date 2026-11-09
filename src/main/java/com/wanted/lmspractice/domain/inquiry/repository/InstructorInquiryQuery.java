package com.wanted.lmspractice.domain.inquiry.repository;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Repository;

/**
 * [역할] 강사가 볼 수 있는 문의 스레드 — "현재 담당 분반 학습자의 스레드만"을 한 번의 조인으로 가려낸다.
 * [담당] 팀원 4 · #21   [화면] T-S06   [API] II-01~03   [기획] D-08(담당 변경 시 새 담당이 스레드를 이어받음)
 * [연결]
 *  - 호출받음: InquiryService (강사 문의 목록·스레드 접근 확인)
 *  - 읽는 표: inquiry_threads ⨝ enrollments ⨝ course_sections(instructor_id) ⨝ users(학습자 이름). 쓰지는 않는다.
 * 담당 강사를 스레드에 저장하지 않고 분반의 현재 담당(course_sections.instructor_id)으로 조회하므로, 담당이 바뀌면 새 담당이 자동으로 이어받는다.
 * [기술] 🟢 EntityManager native 쿼리 (#07 InstructorStatQuery 방식).
 *  이슈는 SectionService 계약을 권했지만 "강사 → 담당 분반 학습자의 스레드" 목록을 주는 계약이 없어(getSectionsByInstructor는 #10 스텁) 이 조회로 대신했다.
 */
@Repository
public class InstructorInquiryQuery {

    /** 목록 한 줄 (강사 화면용) */
    @Getter
    @AllArgsConstructor
    public static class Row {
        private Long threadId;
        private Long courseId;
        private String title;
        private InquiryStatus status;
        private LocalDateTime lastMessageAt;
        private String learnerName;
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** 이 강사가 현재 담당하는 분반 학습자의 스레드 전체 — 최근 메시지 순. 학습자 실명은 담당 강사에게만 보인다(NFR-04) */
    public List<Row> findThreads(Long instructorId) {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT t.id, t.course_id, t.title, t.status, t.last_message_at, u.real_name "
                                + "FROM inquiry_threads t "
                                + "JOIN enrollments e ON e.id = t.enrollment_id "
                                + "JOIN course_sections s ON s.id = e.section_id "
                                + "JOIN users u ON u.id = t.learner_id "
                                + "WHERE s.instructor_id = :instructorId "
                                + "ORDER BY t.last_message_at DESC, t.id DESC")
                .setParameter("instructorId", instructorId)
                .getResultList();
        return rows.stream().map(r -> {
            Object[] c = (Object[]) r;
            return new Row(((Number) c[0]).longValue(), ((Number) c[1]).longValue(), (String) c[2],
                    InquiryStatus.valueOf((String) c[3]), toLocal(c[4]), (String) c[5]);
        }).toList();
    }

    /** 이 스레드가 이 강사의 현재 담당 분반 학습자 것인지 */
    public boolean isAssigned(Long threadId, Long instructorId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM inquiry_threads t "
                                + "JOIN enrollments e ON e.id = t.enrollment_id "
                                + "JOIN course_sections s ON s.id = e.section_id "
                                + "WHERE t.id = :threadId AND s.instructor_id = :instructorId")
                .setParameter("threadId", threadId)
                .setParameter("instructorId", instructorId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private LocalDateTime toLocal(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return value == null ? null : ((Timestamp) value).toLocalDateTime();
    }
}
