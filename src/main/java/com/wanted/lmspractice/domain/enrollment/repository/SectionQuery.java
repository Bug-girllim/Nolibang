package com.wanted.lmspractice.domain.enrollment.repository;

import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * [역할] 분반 현황 조회 전용 — DB VIEW v_section_seats(정원·활성 수강·남은 자리)에 강의 제목·담당 강사 실명을 붙여 읽는다
 * [담당] 팀원 2 · #10   [화면] A-S05, A-S12   [API] SE-01·WL-01 모델 sections, 계약 C6 getSectionsByInstructor
 * [연결]
 *  - 호출받음: SectionService
 *  - 호출함:   VIEW v_section_seats, 표 course_sections·courses·users (읽기만)
 *  - 다른 도메인 표(courses·users)를 읽기 전용 JOIN으로만 쓴다 — 강의·회원 Repository를 주입하지 않고 화면 한 줄을 한 번에 만들기 위함
 * [기술] 🟢 EntityManager native 쿼리 (InstructorStatQuery 방식)
 */
@Repository
public class SectionQuery {

    private static final String SELECT_SECTIONS =
            "SELECT v.section_id, v.course_id, c.title, v.section_no, s.name, v.instructor_id, u.real_name, "
                    + "       v.capacity, v.active_count, v.remaining "
                    + "FROM v_section_seats v "
                    + "JOIN course_sections s ON s.id = v.section_id "
                    + "JOIN courses c ON c.id = v.course_id "
                    + "LEFT JOIN users u ON u.id = v.instructor_id ";

    @PersistenceContext
    private EntityManager entityManager;

    /** 한 강의의 분반 목록 (분반 번호 순) */
    public List<SectionDTO> findByCourseId(Long courseId) {
        return toDtos(entityManager.createNativeQuery(SELECT_SECTIONS
                        + "WHERE v.course_id = :courseId ORDER BY v.section_no")
                .setParameter("courseId", courseId)
                .getResultList());
    }

    /** 한 강사가 담당하는 분반 목록 (강의 id·분반 번호 순) */
    public List<SectionDTO> findByInstructorId(Long instructorId) {
        return toDtos(entityManager.createNativeQuery(SELECT_SECTIONS
                        + "WHERE v.instructor_id = :instructorId ORDER BY v.course_id, v.section_no")
                .setParameter("instructorId", instructorId)
                .getResultList());
    }

    /** 분반 한 개 (없으면 null) */
    public SectionDTO findById(Long sectionId) {
        List<SectionDTO> rows = toDtos(entityManager.createNativeQuery(SELECT_SECTIONS + "WHERE v.section_id = :sectionId")
                .setParameter("sectionId", sectionId)
                .getResultList());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * native 결과 행 → DTO. MySQL 드라이버는 BIGINT UNSIGNED를 BigInteger, COUNT를 Long, TINYINT를 Integer 등으로 돌려주므로
     * 모두 Number로 받아 변환한다.
     */
    private List<SectionDTO> toDtos(List<?> rows) {
        return rows.stream()
                .map(row -> (Object[]) row)
                .map(r -> new SectionDTO(
                        toLong(r[0]),
                        toLong(r[1]),
                        (String) r[2],
                        toLong(r[3]).intValue(),
                        (String) r[4],
                        toLong(r[5]),
                        (String) r[6],
                        ((Number) r[7]).intValue(),
                        ((Number) r[8]).intValue(),
                        ((Number) r[9]).intValue(),
                        false))
                .toList();
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
