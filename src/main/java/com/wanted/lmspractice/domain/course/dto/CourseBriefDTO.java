package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의를 한 줄로 소개하는 최소 정보 — 강의명·수준·취미 이름·취미가 지금 운영 중인지
 * [담당] 팀원 2 · #11(조회 추가)   [화면] U-S08   [기획] D-20(준비 중으로 바뀐 취미의 기존 강의도 계속 진입)   [API] ML-01
 * [연결]
 *  - 만든 곳: CourseService.getCourseBriefs
 *  - 쓰는 곳: MyLearningService → MyCourseDTO (카드 제목·"준비 중 전환" 표시)
 * [기술] 🟢 DTO 정적 팩토리
 * 노출 규칙(비공개 취미 숨김)을 적용하지 않은 값이므로, 본인 수강 기록처럼 이미 권한이 확인된 곳에서만 쓴다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CourseBriefDTO {

    private Long courseId;
    private Long hobbyId;
    private String hobbyName;
    /** 취미가 운영 중인지. false면 준비 중으로 바뀐 취미라 새 신청은 불가하지만 기존 수강은 계속 진입 가능 */
    private boolean hobbyOperating;
    private CourseLevel level;
    private String title;

    /** Course 엔티티 + 취미 정보 → 한 줄 소개 */
    public static CourseBriefDTO from(Course course, String hobbyName, boolean hobbyOperating) {
        return new CourseBriefDTO(
                course.getId(),
                course.getHobbyId(),
                hobbyName,
                hobbyOperating,
                course.getLevel(),
                course.getTitle());
    }

    public String getLevelLabel() {
        return level.getLabel();
    }
}
