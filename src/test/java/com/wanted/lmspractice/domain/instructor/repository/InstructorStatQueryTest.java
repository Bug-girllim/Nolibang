package com.wanted.lmspractice.domain.instructor.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentProgressDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentStatus;
import com.wanted.lmspractice.domain.instructor.service.InstructorDashboardService;
import com.wanted.lmspractice.domain.instructor.service.InstructorStatService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] InstructorStatQuery 집계 숫자가 DB 상태와 일치하는지, 담당 분반 차단·권한 규칙이 지켜지는지 확인한다.
 * [담당] 팀원 1 · #07 (이슈 완료 조건 "InstructorStatQuery 집계 테스트")
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. 조회만 하므로 데이터는 바뀌지 않는다.
 *       기대값은 시드 숫자를 외우지 않고, 같은 DB를 단순 COUNT 쿼리로 다시 세어 비교한다(시드가 바뀌어도 깨지지 않게).
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class InstructorStatQueryTest {

    @Autowired
    private InstructorStatQuery query;
    @Autowired
    private InstructorStatService statService;
    @Autowired
    private InstructorDashboardService dashboardService;
    @PersistenceContext
    private EntityManager em;

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    /** 시드에서 담당 분반이 있는 강사 id 한 명 */
    private Long anyInstructorWithSection() {
        return ((Number) em.createNativeQuery(
                "SELECT instructor_id FROM course_sections WHERE instructor_id IS NOT NULL ORDER BY id LIMIT 1")
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("강사별 분반 숫자의 합이 DB의 같은 조건 COUNT와 같다")
    void sectionStatsMatchDb() {
        Long instructorId = anyInstructorWithSection();
        SectionStatDTO total = SectionStatDTO.total(query.findSectionStats(instructorId, LocalDateTime.now()));

        long active = count("SELECT COUNT(*) FROM enrollments e JOIN course_sections s ON s.id = e.section_id "
                + "WHERE s.instructor_id = " + instructorId + " AND e.status = 'ACTIVE'");
        long completed = count("SELECT COUNT(*) FROM enrollments e JOIN course_sections s ON s.id = e.section_id "
                + "WHERE s.instructor_id = " + instructorId + " AND e.status = 'COMPLETED'");
        long rejected = count("SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id "
                + "JOIN course_sections s ON s.id = e.section_id "
                + "WHERE s.instructor_id = " + instructorId + " AND r.status = 'REJECTED'");
        long pending = count("SELECT COUNT(*) FROM review_rounds r JOIN enrollments e ON e.id = r.enrollment_id "
                + "JOIN course_sections s ON s.id = e.section_id "
                + "WHERE s.instructor_id = " + instructorId + " AND r.status IN ('WAITING','IN_REVIEW')");

        assertThat(total.getActiveCount()).isEqualTo((int) active);
        assertThat(total.getCompletedCount()).isEqualTo((int) completed);
        assertThat(total.getRejectedCount()).isEqualTo((int) rejected);
        assertThat(total.getPendingCount()).isEqualTo((int) pending);
        // 수강 중(학습 중)은 활성 수강 안에 들어간다
        assertThat(total.getLearningCount()).isLessThanOrEqualTo(total.getActiveCount());
        // 임박·초과는 평가 대기·진행의 부분집합이다
        assertThat(total.getImminentCount() + total.getOverdueCount()).isLessThanOrEqualTo(total.getPendingCount());
    }

    @Test
    @DisplayName("담당 공백 분반 수가 DB의 instructor_id IS NULL 개수와 같다")
    void vacantSectionsMatchDb() {
        long vacant = count("SELECT COUNT(*) FROM course_sections WHERE instructor_id IS NULL");
        assertThat(query.findVacantSections(LocalDateTime.now())).hasSize((int) vacant);
    }

    @Test
    @DisplayName("담당 분반이 없는 강사(존재하지 않는 id)는 빈 목록이다")
    void noSectionsReturnsEmpty() {
        assertThat(statService.getSectionStats(-1L)).isEmpty();
        assertThat(dashboardService.getHome(-1L).getSections()).isEmpty();
    }

    @Test
    @DisplayName("다른 강사의 통계를 요청하면 ACCESS_DENIED, 관리자는 허용")
    void statsAccessRule() {
        Long instructorId = anyInstructorWithSection();
        Long other = instructorId + 1000;

        assertThatThrownBy(() -> statService.getSectionStats(other, Role.INSTRUCTOR, instructorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(statService.getSectionStats(other, Role.ADMIN, instructorId)).isNotNull();
        assertThat(statService.getSectionStats(instructorId, Role.INSTRUCTOR, instructorId)).isNotNull();
    }

    @Test
    @DisplayName("담당이 아닌 분반 id로 학습자를 조회하면 SECTION_NOT_FOUND")
    void otherSectionBlocked() {
        Long instructorId = anyInstructorWithSection();
        Long foreignSectionId = ((Number) em.createNativeQuery(
                "SELECT id FROM course_sections WHERE instructor_id IS NULL OR instructor_id <> " + instructorId
                        + " ORDER BY id LIMIT 1").getSingleResult()).longValue();

        assertThatThrownBy(() -> dashboardService.getStudents(instructorId, foreignSectionId, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("담당 학습자 목록은 담당 분반 학습자만, 취소 제외, 상태 필터가 적용된다")
    void studentsListAndFilter() {
        Long instructorId = anyInstructorWithSection();
        List<StudentProgressDTO> all = dashboardService.getStudents(instructorId, null, null, null);

        long expected = count("SELECT COUNT(*) FROM enrollments e JOIN course_sections s ON s.id = e.section_id "
                + "WHERE s.instructor_id = " + instructorId + " AND e.status <> 'CANCELLED'");
        assertThat(all).hasSize((int) expected);

        List<StudentProgressDTO> waiting = dashboardService.getStudents(instructorId, null, StudentStatus.WAITING, null);
        assertThat(waiting).allMatch(s -> s.getStatus() == StudentStatus.WAITING);
    }

    @Test
    @DisplayName("StudentStatus 판정: 수강 상태가 우선, 그 다음 최신 평가 회차 상태")
    void studentStatusRule() {
        assertThat(StudentStatus.of("COMPLETED", "APPROVED")).isEqualTo(StudentStatus.APPROVED);
        assertThat(StudentStatus.of("EXPIRED", null)).isEqualTo(StudentStatus.EXPIRED);
        assertThat(StudentStatus.of("ACTIVE", "WAITING")).isEqualTo(StudentStatus.WAITING);
        assertThat(StudentStatus.of("ACTIVE", "IN_REVIEW")).isEqualTo(StudentStatus.IN_REVIEW);
        assertThat(StudentStatus.of("ACTIVE", "REJECTED")).isEqualTo(StudentStatus.REJECTED);
        assertThat(StudentStatus.of("ACTIVE", null)).isEqualTo(StudentStatus.LEARNING);
    }
}
