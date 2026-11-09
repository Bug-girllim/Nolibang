package com.wanted.lmspractice.domain.instructor.repository;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.instructor.dto.InstructorHomeDTO.OverdueReviewDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentProgressDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * [역할] 강사 홈·담당 학습자·강사 통계·관리자 홈이 쓰는 조회 전용 집계 쿼리 모음 (EntityManager native)
 * [담당] 팀원 1 · #07   [화면] T-S01·T-S02·T-S05, A-S01·A-S13   [기획] FR-051, D-58, D-59
 * [연결]
 *  - 호출받음: InstructorDashboardService, InstructorStatService, AdminDashboardService
 *  - 호출함:   DB(course_sections, courses, enrollments, review_rounds, lesson_progresses, lessons, users, waitlist_entries) — 읽기만 한다
 * [근거] 다른 팀원의 Repository 파일을 수정·주입하지 않기 위해 별도 클래스로 둔다(JPA chap03 방식).
 * [주의] 이 클래스는 다른 표의 상태값 글자(ACTIVE/COMPLETED/WAITING/IN_REVIEW/REJECTED …)에 의존한다.
 *        enum·CHECK 값이 바뀌면 여기 SQL도 함께 고친다(이슈 #07 주의 사항).
 * [기술] 🟢 EntityManager native 쿼리, 🟢 MySQL 서브쿼리·JOIN
 *        🟡 미학습 문법: Java 텍스트 블록(""") — 긴 SQL을 읽기 쉽게 쓰려고 사용(기능 추가 아님, 문자열 연결로 대체 가능)
 */
@Repository
public class InstructorStatQuery {

    /** 마감 임박 기준(시간). 시안 T-S01 "마감 임박 (48h 이하)". */
    public static final int IMMINENT_HOURS = 48;

    private static final String PENDING = "('WAITING','IN_REVIEW')";   // 평가 대기·진행 중인 회차 상태

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // 분반별 숫자 (T-S01 / T-S05 / A-S13 / A-S01)
    // ---------------------------------------------------------------

    /**
     * 강사 한 명이 맡은 분반별 현재 숫자.
     * 호출: InstructorStatService, InstructorDashboardService
     */
    public List<SectionStatDTO> findSectionStats(Long instructorId, LocalDateTime now) {
        return querySections("s.instructor_id = :instructorId", instructorId, now);
    }

    /**
     * 담당 강사가 없는 분반(담당 공백)의 현재 숫자. 호출: AdminDashboardService (A-S01)
     */
    public List<SectionStatDTO> findVacantSections(LocalDateTime now) {
        return querySections("s.instructor_id IS NULL", null, now);
    }

    private List<SectionStatDTO> querySections(String where, Long instructorId, LocalDateTime now) {
        String sql = """
                SELECT s.id, c.title, c.level, s.name, s.capacity,
                  (SELECT COUNT(*) FROM enrollments e
                    WHERE e.section_id = s.id AND e.status = 'ACTIVE') AS active_cnt,
                  (SELECT COUNT(*) FROM enrollments e
                    WHERE e.section_id = s.id AND e.status = 'ACTIVE'
                      AND NOT EXISTS (SELECT 1 FROM review_rounds r
                                       WHERE r.enrollment_id = e.id AND r.status IN %1$s)) AS learning_cnt,
                  (SELECT COUNT(*) FROM enrollments e
                    WHERE e.section_id = s.id AND e.status = 'COMPLETED') AS completed_cnt,
                  (SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id
                    WHERE e.section_id = s.id AND r.status = 'REJECTED') AS rejected_cnt,
                  (SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id
                    WHERE e.section_id = s.id AND r.status IN %1$s) AS pending_cnt,
                  (SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id
                    WHERE e.section_id = s.id AND r.status IN %1$s
                      AND r.due_at >= :now AND r.due_at <= :soon) AS imminent_cnt,
                  (SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id
                    WHERE e.section_id = s.id AND r.status IN %1$s
                      AND r.due_at < :now) AS overdue_cnt
                FROM course_sections s
                JOIN courses c ON c.id = s.course_id
                WHERE %2$s
                ORDER BY s.course_id, s.section_no
                """.formatted(PENDING, where);

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("now", now);
        q.setParameter("soon", now.plusHours(IMMINENT_HOURS));
        if (instructorId != null) {
            q.setParameter("instructorId", instructorId);
        }

        List<SectionStatDTO> result = new ArrayList<>();
        for (Object[] r : rows(q)) {
            result.add(new SectionStatDTO(
                    num(r[0]), str(r[1]), levelLabel(intVal(r[2])), str(r[3]), intVal(r[4]),
                    intVal(r[5]), intVal(r[6]), intVal(r[7]), intVal(r[8]), intVal(r[9]), intVal(r[10]), intVal(r[11])));
        }
        return result;
    }

    // ---------------------------------------------------------------
    // 담당 학습자 (T-S02)
    // ---------------------------------------------------------------

    /**
     * 강사 담당 분반의 학습자(취소 제외) + 차시 진행 + 최신 평가 회차.
     * @param sectionId null이면 담당 분반 전체. 담당 분반인지 확인은 호출한 Service가 한다.
     * @param name      null이면 전체, 아니면 실명 부분 일치
     * 호출: InstructorDashboardService.getStudents
     */
    public List<StudentProgressDTO> findStudents(Long instructorId, Long sectionId, String name) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, s.id, u.real_name, c.title, s.name, e.confirmed_at, e.expires_at, e.status,
                  (SELECT COUNT(*) FROM lesson_progresses p
                    WHERE p.enrollment_id = e.id AND p.completed_at IS NOT NULL) AS done_lessons,
                  (SELECT COUNT(*) FROM lessons l WHERE l.course_id = e.course_id) AS total_lessons,
                  r.id, r.status, r.due_at
                FROM enrollments e
                JOIN course_sections s ON s.id = e.section_id
                JOIN courses c ON c.id = e.course_id
                JOIN users u ON u.id = e.user_id
                LEFT JOIN review_rounds r ON r.enrollment_id = e.id
                  AND r.round_no = (SELECT MAX(r2.round_no) FROM review_rounds r2 WHERE r2.enrollment_id = e.id)
                WHERE s.instructor_id = :instructorId AND e.status <> 'CANCELLED'
                """);
        if (sectionId != null) {
            sql.append(" AND s.id = :sectionId");
        }
        if (name != null) {
            sql.append(" AND u.real_name LIKE CONCAT('%', :name, '%')");
        }
        sql.append(" ORDER BY e.confirmed_at DESC, e.id DESC");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("instructorId", instructorId);
        if (sectionId != null) {
            q.setParameter("sectionId", sectionId);
        }
        if (name != null) {
            q.setParameter("name", name);
        }

        List<StudentProgressDTO> result = new ArrayList<>();
        for (Object[] r : rows(q)) {
            String enrollStatus = str(r[7]);
            String reviewStatus = str(r[11]);
            StudentStatus status = StudentStatus.of(enrollStatus, reviewStatus);
            boolean pending = status == StudentStatus.WAITING || status == StudentStatus.IN_REVIEW;
            StudentProgressDTO dto = new StudentProgressDTO();
            dto.setEnrollmentId(num(r[0]));
            dto.setSectionId(num(r[1]));
            dto.setRealName(str(r[2]));
            dto.setCourseTitle(str(r[3]));
            dto.setSectionName(str(r[4]));
            dto.setConfirmedAt(time(r[5]));
            dto.setExpiresAt(time(r[6]));
            dto.setCompletedLessons(intVal(r[8]));
            dto.setTotalLessons(intVal(r[9]));
            dto.setLatestReviewId(num(r[10]));
            dto.setReviewDueAt(pending ? time(r[12]) : null);
            dto.setStatus(status);
            result.add(dto);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // 기한 초과 평가 (T-S01 경고, A-S01 운영 항목)
    // ---------------------------------------------------------------

    /**
     * 마감이 지난 평가 대기·진행 회차. 호출: InstructorDashboardService(강사 본인), AdminDashboardService(instructorId=null → 전체)
     */
    public List<OverdueReviewDTO> findOverdueReviews(Long instructorId, LocalDateTime now) {
        String sql = """
                SELECT r.id, c.title, c.level, u.real_name, COALESCE(i.nickname, i.real_name), r.due_at
                FROM review_rounds r
                JOIN enrollments e ON e.id = r.enrollment_id
                JOIN course_sections s ON s.id = e.section_id
                JOIN courses c ON c.id = e.course_id
                JOIN users u ON u.id = e.user_id
                LEFT JOIN users i ON i.id = s.instructor_id
                WHERE r.status IN %s AND r.due_at < :now
                """.formatted(PENDING)
                + (instructorId != null ? " AND s.instructor_id = :instructorId" : "")
                + " ORDER BY r.due_at";
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("now", now);
        if (instructorId != null) {
            q.setParameter("instructorId", instructorId);
        }
        List<OverdueReviewDTO> result = new ArrayList<>();
        for (Object[] r : rows(q)) {
            result.add(new OverdueReviewDTO(num(r[0]), str(r[1]), levelLabel(intVal(r[2])), str(r[3]), str(r[4]), time(r[5])));
        }
        return result;
    }

    // ---------------------------------------------------------------
    // 관리자 홈 KPI (A-S01)
    // ---------------------------------------------------------------

    public int countCourses() {
        return count("SELECT COUNT(*) FROM courses");
    }

    public int countLessons() {
        return count("SELECT COUNT(*) FROM lessons");
    }

    public int countActiveEnrollments() {
        return count("SELECT COUNT(*) FROM enrollments WHERE status = 'ACTIVE'");
    }

    public int countPendingReviews() {
        return count("SELECT COUNT(*) FROM review_rounds WHERE status IN " + PENDING);
    }

    public int countWaitingUsers() {
        return count("SELECT COUNT(*) FROM waitlist_entries WHERE status = 'WAITING'");
    }

    // ---------------------------------------------------------------
    // 관리자 강사 통계 (A-S13)
    // ---------------------------------------------------------------

    /** 강사 계정 한 줄 (이름은 닉네임 우선, 없으면 실명). */
    @Getter
    @AllArgsConstructor
    public static class InstructorRow {
        private final Long id;
        private final String name;
        private final String status;
        private final boolean firstLoginDone;
    }

    /** 강사 계정 목록. 호출: InstructorStatService.getInstructorStats */
    public List<InstructorRow> findInstructors() {
        Query q = entityManager.createNativeQuery("""
                SELECT u.id, COALESCE(u.nickname, u.real_name), u.status, u.first_login_at
                FROM users u WHERE u.role = 'INSTRUCTOR' ORDER BY u.id
                """);
        List<InstructorRow> result = new ArrayList<>();
        for (Object[] r : rows(q)) {
            result.add(new InstructorRow(num(r[0]), str(r[1]), str(r[2]), r[3] != null));
        }
        return result;
    }

    // ---------------------------------------------------------------
    // native 결과 변환 도우미 (드라이버가 BigInteger/Long/Timestamp 등으로 돌려줘도 같게 다룬다)
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query q) {
        return q.getResultList();
    }

    private int count(String sql) {
        return intVal(entityManager.createNativeQuery(sql).getSingleResult());
    }

    private static Long num(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static int intVal(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDateTime time(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime t) {
            return t;
        }
        return ((Timestamp) o).toLocalDateTime();
    }

    /** courses.level 숫자(1·2·3) → 입문/중급/고급. 표기는 CourseLevel 한곳에서만 정한다. */
    private static String levelLabel(int level) {
        return CourseLevel.fromCode(level).getLabel();
    }
}
