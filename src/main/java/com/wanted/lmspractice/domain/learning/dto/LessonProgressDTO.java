package com.wanted.lmspractice.domain.learning.dto;

import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 차시 한 개의 진도 — 차시 번호·실제 재생 비율·완료 시각. 계약 C10 반환값이자 재생 보고 응답(누적 비율·완료 여부).
 * [담당] 팀원 3 · #12   [화면] U-S06   [API] LE-01, LE-02
 * [연결]
 *  - 만든 곳: ProgressService (getLessonStatuses, getLessonView, report)
 *  - 쓰는 곳: 팀원 2 MyLearningService(#11), 팀원 1 InstructorDashboardService(#07), learning/lesson.html, lesson-progress.js(JSON)
 * [기술] 🟢 DTO 정적 팩토리
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LessonProgressDTO {

    private Long lessonId;
    private Integer lessonNo;
    private String title;
    /** 실제 재생 비율(0~100). 완료 기준(60%)과 별개로 서버가 기록한 실제 값이다. */
    private int percent;
    private LocalDateTime completedAt;
    private boolean completed;

    /** progress가 null이면(아직 재생한 적 없음) 0%·미완료 */
    public static LessonProgressDTO of(Long lessonId, Integer lessonNo, String title, BigDecimal durationSec,
                                       LessonProgress progress) {
        if (progress == null) {
            return new LessonProgressDTO(lessonId, lessonNo, title, 0, null, false);
        }
        return new LessonProgressDTO(lessonId, lessonNo, title, progress.percent(durationSec),
                progress.getCompletedAt(), progress.isCompleted());
    }
}
