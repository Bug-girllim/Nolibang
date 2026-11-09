package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyPicStyle;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의 한 개의 목록·카드 표시값 — 강의명·수준·정원 현황·대기 인원·내 신청 상태
 * [담당] 팀원 2 · #08   [화면] U-S03(수준별 강의 목록), U-S02(수준별 카드)   [API] CO-01 모델 courses, HB-02 hobby.levels
 * [연결]
 *  - 만든 곳: CourseService(from, 기본 정보) → CourseCatalogService(seats·enrollStatus 채움)
 *  - 쓰는 곳: templates/course/list.html, hobby/detail.html
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CourseDTO {

    private Long id;
    private Long hobbyId;
    private CourseLevel level;
    private String title;
    private String description;
    /** 강의 카드 그림 스타일 CSS 클래스(없으면 빈 문자열) */
    private String picClass;
    /** 수준별 예시 작품 이미지 경로(세부 취미 상세용, 없으면 null) */
    private String levelImage;
    /** 신청 가능한 분반의 정원·남은 자리·대기 인원 (수강 도메인 계약으로 조회) */
    private SeatSummaryDTO seats;
    /** 내 신청 상태 (수강 도메인 계약으로 조회, 비회원은 LOGIN_REQUIRED) */
    private EnrollmentStatusDTO enrollStatus;
    /** [이어서 학습]·[다시 보기]가 열 차시 id — 수강 중·수료 상태일 때만 채워진다 (그 밖에는 null) */
    private Long continueLessonId;

    /** Course 엔티티 → 표시용 DTO (seats·enrollStatus는 CourseCatalogService가 채운다). hobbyCode는 이미지 매핑용 */
    public static CourseDTO from(Course course, String hobbyCode) {
        return new CourseDTO(
                course.getId(),
                course.getHobbyId(),
                course.getLevel(),
                course.getTitle(),
                course.getDescription(),
                HobbyPicStyle.coursePicClass(hobbyCode, course.getLevel()),
                HobbyPicStyle.levelImage(hobbyCode, course.getLevel()),
                null,
                null,
                null);
    }

    /** 화면에 보여 줄 수준 이름 (입문·중급·고급) */
    public String getLevelLabel() {
        return level.getLabel();
    }
}
