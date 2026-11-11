package com.wanted.lmspractice.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 차시별 재생 누적 시간·완료 시각. 재생 보고를 받아 "서버가 관측한 시간만큼만" 적립하고 60%에 닿으면 완료한다.
 * [담당] 팀원 3 · #03(칼럼 매핑), #12(적립·완료 규칙)   [기획] FR-020, D-42
 * [연결]
 *  - 호출받음: ProgressService.report (재생 보고 1건마다 applyReport), ProgressService.isAllCompleted(completedAt 확인)
 *  - 호출함:   없음 (자기 칼럼만 사용 — 다른 표 조회 없음)
 * 칼럼은 resources/sql/schema.sql의 `lesson_progresses` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙.
 * 복합 기본키 (enrollment_id, lesson_id) → @IdClass(LessonProgressId.class). Repository는 JpaRepository<LessonProgress, LessonProgressId>.
 * [기술] 🟡 @IdClass(복합키) — 11-06 팀 결정으로 허용
 */
@Entity
@Table(name = "lesson_progresses")
@IdClass(LessonProgressId.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class LessonProgress {

    /** 완료 기준: 실제 재생 시간이 영상 길이의 60% 이상 (FR-020) */
    public static final BigDecimal COMPLETE_RATIO = new BigDecimal("0.6");

    /**
     * 보고 1건으로 적립할 수 있는 최대 시간(초). 보고 주기 5초의 3배.
     * ❓ 임시값: 5초 주기(DEV_REVIEW 후보, CONVENTIONS §10 #11)에서 네트워크 지연·탭 지연을 견디는 여유. #12 확정 필요
     */
    public static final BigDecimal MAX_CREDIT_PER_REPORT_SEC = new BigDecimal("15");

    @Id
    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Id
    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "played_sec", nullable = false)
    private BigDecimal playedSec = BigDecimal.ZERO;  // DB 기본값과 같은 초기값

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_position_sec", nullable = false)
    private BigDecimal lastPositionSec = BigDecimal.ZERO;  // DB 기본값과 같은 초기값

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    @Column(name = "last_ping_seq", nullable = false)
    private Long lastPingSeq = 0L;  // DB 기본값과 같은 초기값

    @Column(name = "playback_stream_key", length = 64)
    private String playbackStreamKey;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /** 처음 재생하는 차시의 빈 진도 행. 호출: ProgressService.report */
    public static LessonProgress create(Long enrollmentId, Long lessonId, Long courseId) {
        LessonProgress progress = new LessonProgress();
        progress.enrollmentId = enrollmentId;
        progress.lessonId = lessonId;
        progress.courseId = courseId;
        return progress;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    /**
     * 재생 보고 한 건을 반영한다. 적립 시간은 "서버가 직접 잰 경과 시간"이 상한이다(클라이언트가 보낸 시간은 믿지 않는다).
     * 호출: ProgressService.report
     *
     * 규칙(순서가 중요하다)
     *  1. 같은 재생(streamKey)에서 순번(seq)이 이미 받은 값 이하면 무시 — 순서가 뒤바뀌거나 중복된 보고(11-06 DB 확정)
     *  2. 같은 재생이고 "재생 중"일 때만 적립 — 새 재생(streamKey가 다름)의 첫 보고는 기준점만 잡는다.
     *     다중 탭이 번갈아 보고해도 streamKey가 계속 바뀌므로 이중 적립이 생기지 않는다.
     *  3. 적립 = min(서버 경과 시간, 1건 상한, 재생 위치가 앞으로 간 만큼) — 건너뛴 구간·배속으로 부풀린 시간은 인정하지 않는다
     *  4. 일시정지 보고(playing=false)는 적립하지 않고 재생을 끝낸다 — 다시 재생하면 새 streamKey로 시작한다
     *  5. 적립 합계가 길이의 60% 이상이면 완료 시각을 "처음 한 번만" 기록한다
     *
     * @param positionSec 클라이언트가 보고한 현재 재생 위치(초) — 적립 상한 계산에만 쓴다
     */
    public void applyReport(String streamKey, long seq, boolean playing, BigDecimal positionSec,
                            BigDecimal durationSec, LocalDateTime now) {
        boolean sameStream = streamKey.equals(this.playbackStreamKey);
        if (sameStream && seq <= this.lastPingSeq) {
            return;   // 1. 중복·역순 보고
        }

        BigDecimal position = positionSec.max(BigDecimal.ZERO).min(durationSec);
        if (sameStream && playing && lastPingAt != null) {
            credit(position, durationSec, now);   // 2~3. 같은 재생 안에서만 적립
        }

        // 다음 보고의 기준점. 일시정지 보고면 재생 키를 비워 이어서 오는 보고가 적립되지 않게 한다(4)
        this.lastPingAt = now;
        this.lastPingSeq = seq;
        this.lastPositionSec = position;
        this.playbackStreamKey = playing ? streamKey : null;
    }

    private void credit(BigDecimal position, BigDecimal durationSec, LocalDateTime now) {
        BigDecimal elapsedSec = BigDecimal.valueOf(Duration.between(lastPingAt, now).toMillis())
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.DOWN);
        BigDecimal movedSec = position.subtract(lastPositionSec).max(BigDecimal.ZERO);
        BigDecimal credit = elapsedSec.min(MAX_CREDIT_PER_REPORT_SEC).min(movedSec);

        this.playedSec = playedSec.add(credit).min(durationSec);
        if (completedAt == null && playedSec.compareTo(durationSec.multiply(COMPLETE_RATIO)) >= 0) {
            this.completedAt = now;   // 5. 완료 시각은 1회만
        }
    }

    /**
     * 차시 영상이 다른 길이의 영상으로 바뀌었을 때, 이미 쌓인 진도를 새 길이에 맞춘다.
     * 재생 시간·마지막 위치는 새 길이를 넘지 않게 자르고, 이미 새 길이의 60% 이상 봤으면 바로 완료로 기록한다.
     * 이렇게 하지 않으면 짧은 영상으로 바뀐 차시가 "100%인데 미완료"로 남아, 학습자가 다시 재생해야만 완료됐다.
     * 이미 완료한 차시는 그대로 둔다(긴 영상으로 바뀌어도 받은 완료를 빼앗지 않는다).
     * 호출: ProgressService.applyLessonDuration ← LessonVideoService.linkVideo
     */
    public void adjustToDuration(BigDecimal durationSec, LocalDateTime now) {
        this.playedSec = playedSec.min(durationSec);
        this.lastPositionSec = lastPositionSec.min(durationSec);
        if (completedAt == null && playedSec.compareTo(durationSec.multiply(COMPLETE_RATIO)) >= 0) {
            this.completedAt = now;
        }
    }

    /** 이어보기: 마지막 위치가 이보다 앞이면 처음부터 재생한다(몇 초 본 것은 이어 볼 이유가 없다) */
    public static final BigDecimal RESUME_MIN_SEC = new BigDecimal("3");
    /** 이어보기: 끝나기 이만큼 전보다 뒤면 처음부터 재생한다(다 본 영상을 끝에서 다시 여는 일을 막는다) */
    public static final BigDecimal RESUME_END_MARGIN_SEC = new BigDecimal("5");

    /**
     * 차시 화면을 다시 열었을 때 이어서 재생할 위치(초). 이어볼 위치가 없으면 0(처음부터).
     * 마지막 보고 위치(last_position_sec)를 쓴다 — 재생 중 5초마다·일시정지·탭 닫기 때 서버에 남는 위치다.
     * 위치만 정할 뿐 진도 적립에는 영향이 없다(재생을 시작하면 첫 보고가 기준점을 새로 잡는다).
     * 호출: ProgressService.getLessonView → lesson.html data-resume-sec → lesson-progress.js
     */
    public BigDecimal resumePositionSec(BigDecimal durationSec) {
        if (lastPositionSec.compareTo(RESUME_MIN_SEC) < 0
                || lastPositionSec.compareTo(durationSec.subtract(RESUME_END_MARGIN_SEC)) >= 0) {
            return BigDecimal.ZERO;
        }
        return lastPositionSec;
    }

    /** 영상 길이 대비 실제 재생 비율(0~100, 소수 버림). 화면의 진행 막대에 쓴다. */
    public int percent(BigDecimal durationSec) {
        if (durationSec.signum() <= 0) {
            return 0;
        }
        int percent = playedSec.multiply(BigDecimal.valueOf(100)).divide(durationSec, 0, RoundingMode.DOWN).intValue();
        return Math.min(percent, 100);
    }
}
