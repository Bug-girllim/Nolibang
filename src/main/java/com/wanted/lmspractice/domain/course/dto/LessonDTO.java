package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.domain.course.entity.Lesson;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의 상세의 "차시별 학습 구성" 한 줄 — 차시 번호·제목·학습 목표·영상 길이(분)
 * [담당] 팀원 2 · #08   [화면] U-S04   [API] CO-02 모델 course.lessons
 * [연결]
 *  - 만든 곳: CourseService.getCourseDetail(from)
 *  - 쓰는 곳: templates/course/detail.html (차시 표)
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * 영상 파일 경로(video_key)는 담지 않는다 — 영상 접근은 권한 검사를 거치는 #12(/videos/{lessonId})로만 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LessonDTO {

    private Long id;
    private Integer lessonNo;
    private String title;
    private String learningObjective;
    /** 영상 길이(분, 올림). 예: 600초 → 10분 */
    private int durationMinutes;

    /** Lesson 엔티티 → 표시용 DTO */
    public static LessonDTO from(Lesson lesson) {
        BigDecimal minutes = lesson.getDurationSec().divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING);
        return new LessonDTO(
                lesson.getId(),
                lesson.getLessonNo(),
                lesson.getTitle(),
                lesson.getLearningObjective(),
                minutes.intValue());
    }
}
