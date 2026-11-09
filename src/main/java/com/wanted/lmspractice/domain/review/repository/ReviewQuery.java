package com.wanted.lmspractice.domain.review.repository;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Repository;

/**
 * [역할] 강사 평가 화면(T-S03·T-S04)이 쓰는 조회 전용 native 쿼리 — 평가 회차에 학습자 실명·강의·분반·제출물을 붙여 읽는다.
 * [담당] 팀원 3 · #14   [화면] T-S03, T-S04   [API] RV-01, RV-02
 * [연결]
 *  - 호출받음: ReviewService(목록·상세 화면 조립, 승인·반려 알림의 학습자 id·강의명)
 *  - 호출함:   DB(review_rounds, enrollments, users, courses, course_sections, work_versions, works, work_files) — 읽기만 한다
 * [근거] 한 화면에 5개 표를 묶어야 해서 다른 도메인 Repository를 주입하는 대신 InstructorStatQuery(#07)처럼 별도 조회 클래스로 둔다.
 *        상태 판단(임박·초과·잠금)은 여기서 하지 않고 ReviewRound 엔티티 메서드가 맡는다.
 * [주의] 분반 이름 규칙(이름이 비면 1 → A분반)은 CourseSection.getDisplayName과 같다. 한쪽이 바뀌면 같이 고친다.
 *        ❓ 다른 도메인 표(works·work_files)를 직접 읽는다 — 팀원 4에게 "작품 버전 1건 조회" 계약이 생기면 그쪽으로 바꾼다.
 * [기술] 🟢 EntityManager native 쿼리
 *        🟡 미학습 문법: Java 텍스트 블록(""") — 긴 SQL을 읽기 쉽게 쓰려고 사용(InstructorStatQuery와 같음, 문자열 연결로 대체 가능)
 */
@Repository
public class ReviewQuery {

    private static final String CONTEXT_SELECT = """
            SELECT r.id, e.id, u.id, u.real_name, c.title, c.level, s.id,
                   COALESCE(NULLIF(TRIM(s.name), ''),
                            CASE WHEN s.section_no <= 26 THEN CONCAT(CHAR(64 + s.section_no USING utf8mb4), '분반')
                                 ELSE CONCAT(s.section_no, '분반') END)
              FROM review_rounds r
              JOIN enrollments e     ON e.id = r.enrollment_id
              JOIN users u           ON u.id = e.user_id
              JOIN courses c         ON c.id = e.course_id
              JOIN course_sections s ON s.id = e.section_id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 강사가 "지금" 담당하는 분반의 평가 회차 전체 — 담당이 바뀌면 이전 강사 목록에서 빠지고 새 강사 목록에 나타난다.
     * 호출: ReviewService.getReviewList (RV-01)
     */
    public List<ReviewContext> findContextsByInstructor(Long instructorId) {
        List<?> rows = entityManager.createNativeQuery(CONTEXT_SELECT + " WHERE s.instructor_id = :instructorId ORDER BY r.due_at, r.id")
                .setParameter("instructorId", instructorId)
                .getResultList();
        return rows.stream().map(row -> toContext((Object[]) row)).toList();
    }

    /**
     * 평가 회차 1건의 학습자·강의·분반 정보.
     * 호출: ReviewService.getReviewDetail(RV-02), approve·reject(알림 받을 학습자 id·강의명)
     */
    public Optional<ReviewContext> findContext(Long reviewRoundId) {
        List<?> rows = entityManager.createNativeQuery(CONTEXT_SELECT + " WHERE r.id = :id")
                .setParameter("id", reviewRoundId)
                .getResultList();
        return rows.stream().map(row -> toContext((Object[]) row)).findFirst();
    }

    /**
     * 강사가 담당하는 분반 목록 — 목록 화면의 「담당 분반」 필터 선택지.
     * 호출: ReviewService.getReviewList
     * ❓ #10 merge 후 계약 C6 SectionService.getSectionsByInstructor로 바꿀 수 있다(지금은 스텁이 빈 목록을 돌려준다).
     */
    public List<SectionOption> findSectionsByInstructor(Long instructorId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT s.id, c.title, c.level,
                               COALESCE(NULLIF(TRIM(s.name), ''),
                                        CASE WHEN s.section_no <= 26 THEN CONCAT(CHAR(64 + s.section_no USING utf8mb4), '분반')
                                             ELSE CONCAT(s.section_no, '분반') END)
                          FROM course_sections s
                          JOIN courses c ON c.id = s.course_id
                         WHERE s.instructor_id = :instructorId
                         ORDER BY c.title, s.section_no
                        """)
                .setParameter("instructorId", instructorId)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new SectionOption(toLong(r[0]), (String) r[1], levelLabel(r[2]), (String) r[3]);
        }).toList();
    }

    /**
     * 제출한 작품 버전의 제목·설명·회고와 이미지 id(올린 순서) — 상세 화면의 「학습자 최종 과제」.
     * 이미지는 /files/works/{fileId}(#16 WorkFileController)로 출력하며, 그쪽 WorkAccessPolicy가 현재 담당 강사 열람을 허용한다.
     * 호출: ReviewService.getReviewDetail
     */
    public Optional<Submission> findSubmission(Long workVersionId) {
        if (workVersionId == null) {
            return Optional.empty();    // 제출물이 삭제된 회차(submission_deleted_at)
        }
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT v.title, v.description, w.reflection
                          FROM work_versions v
                          JOIN works w ON w.id = v.work_id
                         WHERE v.id = :versionId
                        """)
                .setParameter("versionId", workVersionId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        List<?> idRows = entityManager.createNativeQuery(
                        "SELECT id FROM work_files WHERE work_version_id = :versionId ORDER BY sort_order")
                .setParameter("versionId", workVersionId)
                .getResultList();
        List<Long> fileIds = idRows.stream().map(ReviewQuery::toLong).toList();
        return Optional.of(new Submission((String) r[0], (String) r[1], (String) r[2], fileIds));
    }

    private ReviewContext toContext(Object[] r) {
        return new ReviewContext(toLong(r[0]), toLong(r[1]), toLong(r[2]), (String) r[3],
                (String) r[4], levelLabel(r[5]), toLong(r[6]), (String) r[7]);
    }

    // MySQL BIGINT UNSIGNED는 드라이버에 따라 BigInteger·Long으로 온다
    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String levelLabel(Object level) {
        return CourseLevel.fromCode(((Number) level).intValue()).getLabel();
    }

    /** 평가 회차 1건에 붙는 학습자·강의·분반 정보 (학습자 실명은 강사 화면에만 쓴다 — NFR-04) */
    @AllArgsConstructor
    @Getter
    public static class ReviewContext {
        private Long reviewRoundId;
        private Long enrollmentId;
        private Long learnerId;
        private String learnerName;
        private String courseTitle;
        private String levelLabel;
        private Long sectionId;
        private String sectionName;
    }

    /** 「담당 분반」 필터 선택지 1개 */
    @AllArgsConstructor
    @Getter
    public static class SectionOption {
        private Long sectionId;
        private String courseTitle;
        private String levelLabel;
        private String sectionName;
    }

    /** 제출물(작품 버전) 내용 */
    @AllArgsConstructor
    @Getter
    public static class Submission {
        private String title;
        private String description;
        private String reflection;
        private List<Long> fileIds;
    }
}
