package com.wanted.lmspractice.domain.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] LessonProgress.applyReport의 누적·완료 규칙 테스트 — 서버 관측 시간 상한, 60% 완료, 일시정지·건너뛰기·배속·중복 보고·다중 탭
 * [담당] 팀원 3 · #12 (이슈 완료 조건 "ProgressService 누적·완료 판정 테스트")
 * [전제] DB 없이 엔티티만 검사한다(시각을 직접 넘겨 시간 흐름을 흉내 낸다).
 */
class LessonProgressTest {

    private static final BigDecimal DURATION = new BigDecimal("600");   // 10분 영상 (DEV_REVIEW §4-3)
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 9, 10, 0, 0);

    private LessonProgress newProgress() {
        return LessonProgress.create(1L, 1L, 1L);
    }

    private BigDecimal sec(double v) {
        return BigDecimal.valueOf(v);
    }

    /** 5초 간격·1배속으로 한 재생(streamKey)을 count번 보고한다. 첫 보고는 기준점만 잡는다. */
    private void playNormally(LessonProgress p, String key, int count, long startSeq) {
        for (int i = 0; i < count; i++) {
            p.applyReport(key, startSeq + i, true, sec(i * 5), DURATION, T0.plusSeconds(i * 5L));
        }
    }

    @Test
    @DisplayName("10분 영상은 실제로 6분(360초) 재생해야 완료된다")
    void completesAtSixtyPercent() {
        LessonProgress p = newProgress();

        playNormally(p, "s1", 72, 1);   // 기준점 1회 + 71구간 × 5초 = 355초
        assertThat(p.getPlayedSec()).isEqualByComparingTo("355");
        assertThat(p.isCompleted()).isFalse();

        p.applyReport("s1", 100, true, sec(360), DURATION, T0.plusSeconds(360));   // 360초째
        assertThat(p.getPlayedSec()).isEqualByComparingTo("360");
        assertThat(p.isCompleted()).isTrue();
        assertThat(p.percent(DURATION)).isEqualTo(60);
    }

    @Test
    @DisplayName("완료 시각은 처음 한 번만 기록된다")
    void completedAtRecordedOnce() {
        LessonProgress p = newProgress();
        playNormally(p, "s1", 73, 1);
        LocalDateTime firstCompletedAt = p.getCompletedAt();
        assertThat(firstCompletedAt).isNotNull();

        p.applyReport("s1", 200, true, sec(400), DURATION, T0.plusSeconds(1000));
        assertThat(p.getCompletedAt()).isEqualTo(firstCompletedAt);
    }

    @Test
    @DisplayName("보고를 빠르게 반복하거나 큰 값을 보내도 서버가 잰 경과 시간 이상 쌓이지 않는다")
    void creditCappedByServerElapsed() {
        LessonProgress p = newProgress();
        BigDecimal longVideo = new BigDecimal("6000");   // 위치가 영상 길이에서 잘리지 않게 긴 영상으로 검사
        p.applyReport("s1", 1, true, sec(0), longVideo, T0);
        // 1초 간격으로 위치를 100초씩 올려 보낸다 (조작)
        for (int i = 1; i <= 10; i++) {
            p.applyReport("s1", 1 + i, true, sec(i * 100), longVideo, T0.plusSeconds(i));
        }
        assertThat(p.getPlayedSec()).isEqualByComparingTo("10");   // 실제 흐른 10초만
    }

    @Test
    @DisplayName("보고 간격이 길어도 1건에 적립할 수 있는 최대(15초)까지만 쌓인다")
    void creditCappedPerReport() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(60), DURATION, T0.plusSeconds(60));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("건너뛰기: 재생 위치가 앞서 가도 실제 흐른 시간만 적립된다")
    void skipDoesNotInflate() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(65), DURATION, T0.plusSeconds(5));   // 60초 건너뛰기 + 5초 재생
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("2배속: 5초 동안 영상이 10초 진행돼도 경과 시간 5초만 인정된다")
    void doubleSpeedCountsElapsedOnly() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(10), DURATION, T0.plusSeconds(5));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("재생 위치가 뒤로 가면(다시 보기) 적립하지 않는다")
    void rewindNotCredited() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(100), DURATION, T0);
        p.applyReport("s1", 2, true, sec(40), DURATION, T0.plusSeconds(5));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("일시정지 보고(playing=false)는 적립하지 않고, 이어서 오는 보고도 적립하지 않는다")
    void pausedNotCredited() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(5), DURATION, T0.plusSeconds(5));   // +5
        p.applyReport("s1", 3, false, sec(10), DURATION, T0.plusSeconds(10)); // 일시정지: 적립 없음
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
        assertThat(p.getPlaybackStreamKey()).isNull();

        // 같은 키로 늦게 온 재생 보고는 새 재생처럼 기준점만 잡는다
        p.applyReport("s1", 4, true, sec(300), DURATION, T0.plusSeconds(300));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("다시 재생(새 streamKey)하면 첫 보고는 기준점만 잡고 그 뒤부터 쌓인다")
    void newStreamStartsFromBaseline() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(5), DURATION, T0.plusSeconds(5));
        p.applyReport("s1", 3, false, sec(5), DURATION, T0.plusSeconds(6));

        p.applyReport("s2", 1, true, sec(5), DURATION, T0.plusSeconds(3600));   // 한 시간 뒤 재생 — 공백은 적립 안 됨
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
        p.applyReport("s2", 2, true, sec(10), DURATION, T0.plusSeconds(3605));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("순서가 뒤바뀌거나 중복된 보고(seq 이하)는 적립하지 않는다")
    void duplicateOrOutOfOrderIgnored() {
        LessonProgress p = newProgress();
        p.applyReport("s1", 1, true, sec(0), DURATION, T0);
        p.applyReport("s1", 2, true, sec(5), DURATION, T0.plusSeconds(5));
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");

        p.applyReport("s1", 2, true, sec(5), DURATION, T0.plusSeconds(6));   // 중복
        p.applyReport("s1", 1, true, sec(1), DURATION, T0.plusSeconds(7));   // 역순
        assertThat(p.getPlayedSec()).isEqualByComparingTo("5");
        assertThat(p.getLastPingSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("다중 탭이 번갈아 보고해도(streamKey가 계속 바뀜) 이중 적립되지 않는다")
    void multipleTabsDoNotDoubleCount() {
        LessonProgress p = newProgress();
        for (int i = 0; i < 20; i++) {
            String key = (i % 2 == 0) ? "tabA" : "tabB";
            p.applyReport(key, i + 1, true, sec(i * 2.5), DURATION, T0.plusSeconds(i * 3L));
        }
        assertThat(p.getPlayedSec()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("적립은 영상 길이를 넘지 않는다")
    void cappedAtDuration() {
        LessonProgress p = newProgress();
        BigDecimal shortVideo = new BigDecimal("10");
        for (int i = 0; i < 20; i++) {
            p.applyReport("s1", i + 1, true, sec(Math.min(i * 5, 10)), shortVideo, T0.plusSeconds(i * 5L));
        }
        assertThat(p.getPlayedSec()).isLessThanOrEqualTo(shortVideo);
        assertThat(p.percent(shortVideo)).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("영상이 짧은 것으로 바뀌면 이미 본 시간이 새 길이의 60% 이상일 때 바로 완료되고, 시간·위치는 새 길이를 넘지 않는다")
    void shorterVideoCompletesImmediately() {
        LessonProgress p = newProgress();
        ReflectionTestUtils.setField(p, "playedSec", new BigDecimal("150"));        // 600초 영상에서 25%
        ReflectionTestUtils.setField(p, "lastPositionSec", new BigDecimal("150"));

        p.adjustToDuration(new BigDecimal("120"), T0);   // 2분 영상으로 교체

        assertThat(p.isCompleted()).isTrue();
        assertThat(p.getCompletedAt()).isEqualTo(T0);
        assertThat(p.getPlayedSec()).isEqualByComparingTo("120");
        assertThat(p.getLastPositionSec()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("이어보기 위치: 마지막 위치를 돌려주되, 3초 미만이거나 끝나기 5초 전 이후면 처음부터(0)")
    void resumePosition() {
        LessonProgress p = newProgress();
        assertThat(p.resumePositionSec(DURATION)).isEqualByComparingTo("0");   // 아직 본 적 없음

        ReflectionTestUtils.setField(p, "lastPositionSec", new BigDecimal("150.5"));
        assertThat(p.resumePositionSec(DURATION)).isEqualByComparingTo("150.5");

        ReflectionTestUtils.setField(p, "lastPositionSec", new BigDecimal("2.9"));
        assertThat(p.resumePositionSec(DURATION)).isEqualByComparingTo("0");     // 거의 처음

        ReflectionTestUtils.setField(p, "lastPositionSec", new BigDecimal("595"));
        assertThat(p.resumePositionSec(DURATION)).isEqualByComparingTo("0");     // 끝나기 5초 전 이후 = 다 본 영상
        ReflectionTestUtils.setField(p, "lastPositionSec", new BigDecimal("594.9"));
        assertThat(p.resumePositionSec(DURATION)).isEqualByComparingTo("594.9");
    }

    @Test
    @DisplayName("길어진 영상: 아직 60% 안 됐으면 미완료 그대로, 이미 완료한 차시는 완료를 유지한다")
    void longerVideoKeepsState() {
        LessonProgress notYet = newProgress();
        ReflectionTestUtils.setField(notYet, "playedSec", new BigDecimal("150"));
        notYet.adjustToDuration(new BigDecimal("1200"), T0);
        assertThat(notYet.isCompleted()).isFalse();
        assertThat(notYet.getPlayedSec()).isEqualByComparingTo("150");

        LessonProgress done = newProgress();
        ReflectionTestUtils.setField(done, "playedSec", new BigDecimal("380"));
        ReflectionTestUtils.setField(done, "completedAt", T0.minusDays(1));
        done.adjustToDuration(new BigDecimal("1200"), T0);
        assertThat(done.getCompletedAt()).isEqualTo(T0.minusDays(1));   // 받은 완료를 빼앗지 않는다
    }
}
