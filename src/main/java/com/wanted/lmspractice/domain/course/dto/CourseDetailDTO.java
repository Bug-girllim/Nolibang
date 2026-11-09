package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의 상세 한 장 — 목표·준비물·필수 3차시·최종 과제 안내·수강 기간 30일·잔여 정원·대기 인원
 * [담당] 팀원 2 · #08   [화면] U-S04   [기획] D-08(비회원도 열람)   [API] CO-02 모델 course
 * [연결]
 *  - 만든 곳: CourseService.getCourseDetail(from) → CourseCatalogService.getCourseDetail(seats 채움)
 *  - 쓰는 곳: templates/course/detail.html
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CourseDetailDTO {

    /** 수강 확정 후 수강 기간(일) — 값은 수강 도메인 Enrollment.ENROLLMENT_DAYS 한 곳에서 정한다(#09, DB CHECK +30일과 같음) */
    public static final int ENROLLMENT_DAYS = Enrollment.ENROLLMENT_DAYS;

    private Long id;
    private Long hobbyId;
    private String hobbyName;
    private CourseLevel level;
    private String title;
    private String description;
    /** 준비물 한 줄씩(줄바꿈으로 구분된 supplies_text를 나눈 것) */
    private List<String> supplies;
    private String assignmentTitle;
    private String assignmentDescription;
    private List<LessonDTO> lessons;
    private SeatSummaryDTO seats;

    /** Course 엔티티 + 차시 목록 → 상세 DTO (seats는 CourseCatalogService가 채운다) */
    public static CourseDetailDTO from(Course course, String hobbyName, List<LessonDTO> lessons) {
        return new CourseDetailDTO(
                course.getId(),
                course.getHobbyId(),
                hobbyName,
                course.getLevel(),
                course.getTitle(),
                course.getDescription(),
                splitLines(course.getSuppliesText()),
                course.getAssignmentTitle(),
                course.getAssignmentDescription(),
                lessons,
                null);
    }

    public String getLevelLabel() {
        return level.getLabel();
    }

    /** 수강 기간(일) — 템플릿에서 ${course.enrollmentDays}로 쓴다 */
    public int getEnrollmentDays() {
        return ENROLLMENT_DAYS;
    }

    /** 줄바꿈(\n·\r\n)으로 나누고 빈 줄은 버린다 */
    private static List<String> splitLines(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
