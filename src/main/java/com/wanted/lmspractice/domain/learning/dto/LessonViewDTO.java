package com.wanted.lmspractice.domain.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 차시 영상 학습 화면(U-S06) 한 장의 데이터 — 지금 차시 + 차시 목록·진도 + 수강 기간
 * [담당] 팀원 3 · #12   [화면] U-S06   [API] LE-01 모델 lesson
 * [연결]
 *  - 만든 곳: ProgressService.getLessonView
 *  - 쓰는 곳: LessonController.lesson → learning/lesson.html
 * [기술] 🟢 Lombok DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LessonViewDTO {

    private Long enrollmentId;
    private Long courseId;
    private String courseTitle;
    private String levelLabel;          // 입문 / 중급 / 고급

    private Long lessonId;
    private Integer lessonNo;
    private String lessonTitle;
    private String learningObjective;
    private String contentText;         // 없으면 null
    private BigDecimal durationSec;

    /** 지금 차시의 진도 */
    private int percent;
    private boolean completed;

    /** 1·2·3차시 목록과 각 진도 */
    private List<LessonProgressDTO> lessons;
    private int completedCount;
    private int totalCount;
    /** 3차시를 모두 완료했는지 — 최종 과제로 가는 버튼을 켠다 */
    private boolean allCompleted;

    private LocalDateTime expiresAt;
    /** 수강 종료까지 남은 날수 표시("D-12", 수료자는 "수료") */
    private String periodText;

    /** 완료 기준(%) — 화면 문구에 쓴다 */
    private int completeThresholdPercent;
}
