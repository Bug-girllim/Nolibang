package com.wanted.lmspractice.domain.learning.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 재생 시간 보고 입력 — 브라우저가 재생 중 약 5초마다 보내는 값 (폼 파라미터)
 * [담당] 팀원 3 · #12   [화면] U-S06   [API] LE-02
 * [연결]
 *  - 받는 곳: LessonController.report (POST /lessons/{lessonId}/progress) → ProgressService.report
 *  - 보내는 곳: static/js/lesson-progress.js
 * [주의] 적립 시간은 이 값이 아니라 서버가 잰 경과 시간이다. positionSec은 "건너뛰기"를 가려내는 데만 쓴다.
 * [기술] 🟢 Bean Validation, 🟢 @ModelAttribute
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ProgressReportDTO {

    @NotNull
    private Long enrollmentId;

    /** 재생을 시작할 때마다 브라우저가 새로 만드는 값 (DB playback_stream_key 길이 64) */
    @NotBlank
    @Size(max = 64)
    private String streamKey;

    /** 같은 재생 안에서 1씩 커지는 보고 순번 (DB last_ping_seq) */
    @NotNull
    @Min(0)
    private Long seq;

    /** 재생 중이면 true, 일시정지·종료 보고면 false */
    private boolean playing;

    /** 현재 재생 위치(초) */
    @NotNull
    @DecimalMin("0")
    private BigDecimal positionSec;
}
