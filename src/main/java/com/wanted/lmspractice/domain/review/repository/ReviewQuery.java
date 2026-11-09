package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO.SectionOption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Repository;

/**
 * [역할] 강사 평가 화면(T-S03·T-S04)과 평가 권한 확인에 필요한 "다른 표" 값을 읽는 조회 전용 쿼리 (EntityManager native)
 *        수강 건 → 학습자 실명·강의·분반·현재 담당 강사·수강 만료 시각, 제출 작품 버전 → 제목·설명·이미지 id
 * [담당] 팀원 3 · #14   [화면] T-S03·T-S04   [기획] FR-023·030·031
 * [연결]
 *  - 호출받음: ReviewService(목록·상세·권한 확인·알림 문구·만료 후 반려 판단)
 *  - 호출함:   DB(review_rounds, enrollments, course_sections, courses, users, work_versions, work_files) — 읽기만 한다
 * [근거] 다른 팀원의 Repository를 주입하지 않기 위해 별도 조회 클래스로 둔다(#07 InstructorStatQuery와 같은 방식, 작업분배 §9-2 4번).
 *  ❓ 임시: 담당 강사 확인은 계약 C6 SectionService.getCurrentInstructorId가 맞지만 #10이 revert되어 아직 스텁(예외)이라
 *          같은 값(enrollments.section_id → course_sections.instructor_id)을 여기서 읽는다. #10 merge 후 C6로 바꿀지 팀 결정 필요.
 *  ❓ 임시: 제출물(작품 버전 제목·설명·이미지 id)은 #16에 읽기 계약이 없어 여기서 읽는다. 이미지는 계약 C9 URL(/files/works/{fileId})로만 보여 준다.
 * [주의] "현재 담당" 기준: 분반의 지금 instructor_id. 담당이 바뀌면 이전 강사는 목록·상세·처리 모두 불가, 새 강사가 초안·잠금·마감을 그대로 이어받는다(D-59).
 * [기술] 🟢 EntityManager native 쿼리, 🟢 JOIN
 *        🟡 미학습 문법: Java 텍스트 블록(""") — 긴 SQL을 읽기 쉽게 쓰려고 사용(#07과 같음, 문자열 연결로 대체 가능)
 */
@Repository
public class ReviewQuery {

    /** 수강 건 기준 공통 SELECT — 강의·분반·학습자 정보와 현재 담당 강사 */
    private static final String CONTEXT_SELECT = """
            SELECT r.id, e.id, e.user_id, u.real_name, c.title, c.level, c.assignment_title,
                   s.id, s.name, s.section_no, s.instructor_id, e.expires_at
              FROM review_rounds r
              JOIN enrollments e     ON e.id = r.enrollment_id
              JOIN course_sections s ON s.id = e.section_id
              JOIN courses c         ON c.id = e.course_id
              JOIN users u           ON u.id = e.user_id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 평가 회차 한 건의 수강·강의·분반 정보와 현재 담당 강사.
     * 호출: ReviewService(권한 확인 requireAssigned, 상세 화면, 승인·반려 알림 문구·만료 판단)
     */
    public Optional<RoundContext> findContext(Long reviewRoundId) {
        Query q = entityManager.createNativeQuery(CONTEXT_SELECT + " WHERE r.id = :id");
        q.setParameter("id", reviewRoundId);
        return rows(q).stream().map(ReviewQuery::toContext).findFirst();
    }

    /**
     * 강사가 "지금" 담당하는 분반의 모든 평가 회차 정보 — 목록(T-S03)용. 상태·마감 판단은 엔티티가 하므로 여기서는 값만 읽는다.
     * 호출: ReviewService.getReviewPage
     */
    public List<RoundContext> findContextsByInstructor(Long instructorId) {
        Query q = entityManager.createNativeQuery(CONTEXT_SELECT + " WHERE s.instructor_id = :instructorId");
        q.setParameter("instructorId", instructorId);
        return rows(q).stream().map(ReviewQuery::toContext).toList();
    }

    /**
     * 강사의 현재 담당 분반 목록 — 목록 화면의 "담당 분반" 필터 선택지.
     * 호출: ReviewService.getReviewPage
     */
    public List<SectionOption> findSectionOptions(Long instructorId) {
        Query q = entityManager.createNativeQuery("""
                SELECT s.id, c.title, c.level, s.name, s.section_no
                  FROM course_sections s
                  JOIN courses c ON c.id = s.course_id
                 WHERE s.instructor_id = :instructorId
                 ORDER BY c.hobby_id, c.level, s.section_no
                """);
        q.setParameter("instructorId", instructorId);
        List<SectionOption> result = new ArrayList<>();
        for (Object[] row : rows(q)) {
            String label = str(row[1]) + " · " + levelLabel(row[2]) + " · " + sectionName(row[3], row[4]);
            result.add(new SectionOption(num(row[0]), label));
        }
        return result;
    }

    /**
     * 제출 작품 버전의 제목·설명 (과제 삭제로 버전이 없으면 빈 값).
     * 호출: ReviewService.getDetail
     */
    public Optional<Submission> findSubmission(Long workVersionId) {
        if (workVersionId == null) {
            return Optional.empty();
        }
        Query q = entityManager.createNativeQuery("SELECT title, description FROM work_versions WHERE id = :id");
        q.setParameter("id", workVersionId);
        List<Object[]> found = rows(q);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Query files = entityManager.createNativeQuery(
                "SELECT id FROM work_files WHERE work_version_id = :id ORDER BY sort_order");
        files.setParameter("id", workVersionId);
        List<Long> fileIds = ((List<?>) files.getResultList()).stream().map(ReviewQuery::num).toList();
        return Optional.of(new Submission(str(found.get(0)[0]), str(found.get(0)[1]), fileIds));
    }

    // ---------------------------------------------------------------
    // 결과 행
    // ---------------------------------------------------------------

    /** 평가 회차 한 건의 수강·강의·분반 정보 (서비스 안에서만 쓰고 화면에는 DTO로 바꿔 넘긴다) */
    @Getter
    @AllArgsConstructor
    public static class RoundContext {
        private Long reviewRoundId;
        private Long enrollmentId;
        private Long learnerId;
        private String learnerName;       // 실명 — 강사 화면에만 보인다(NFR-04)
        private String courseTitle;
        private String levelLabel;
        private String assignmentTitle;
        private Long sectionId;
        private String sectionName;
        private Long instructorId;        // 분반의 현재 담당 강사 (없으면 null)
        private LocalDateTime expiresAt;  // 수강 만료 시각 — 만료 후 반려면 수강 종료(RV-07)

        /** 이 강사가 지금 이 분반의 담당인지 */
        public boolean isAssignedTo(Long userId) {
            return instructorId != null && instructorId.equals(userId);
        }
    }

    /** 제출 작품 버전 — 제목·설명·이미지 파일 id(정렬 순) */
    @Getter
    @AllArgsConstructor
    public static class Submission {
        private String title;
        private String description;
        private List<Long> fileIds;
    }

    // ---------------------------------------------------------------
    // native 결과 변환 도우미 (#07 InstructorStatQuery와 같은 규칙)
    // ---------------------------------------------------------------

    private static RoundContext toContext(Object[] row) {
        return new RoundContext(num(row[0]), num(row[1]), num(row[2]), str(row[3]), str(row[4]), levelLabel(row[5]),
                str(row[6]), num(row[7]), sectionName(row[8], row[9]), num(row[10]), time(row[11]));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query q) {
        return q.getResultList();
    }

    private static Long num(Object o) {
        return o == null ? null : ((Number) o).longValue();
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

    /** courses.level 숫자(1·2·3) → 입문/중급/고급. 표기는 CourseLevel 한곳에서만 정한다 */
    private static String levelLabel(Object level) {
        return CourseLevel.fromCode(((Number) level).intValue()).getLabel();
    }

    /** 분반 표시 이름 — CourseSection.getDisplayName과 같은 규칙(이름이 비면 번호로 A분반·B분반…). 규칙이 바뀌면 함께 고친다 */
    private static String sectionName(Object name, Object sectionNo) {
        String n = str(name);
        if (n != null && !n.isBlank()) {
            return n;
        }
        int no = ((Number) sectionNo).intValue();
        return no <= 26 ? (char) ('A' + no - 1) + "분반" : no + "분반";
    }
}
