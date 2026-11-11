package com.wanted.lmspractice.domain.game.policy;

import com.wanted.lmspractice.domain.game.dto.PeriodStatDTO;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * [역할] 퀘스트 목록(일일·주간). DB에 두지 않고 코드로 정의한다 — 달성 여부는 학습·작품 기록에서 세고, 보상 수령만 quest_claims 에 남긴다.
 * [연결] 사용처: GameService(진행도 계산·보상 지급), templates/game/character.html(퀘스트 카드)
 * [규칙] 일일 = 오늘 0시부터, 주간 = 이번 주 월요일 0시부터 센다. 같은 기간·같은 퀘스트 보상은 1번만 받는다(기간 키로 구분).
 */
public enum QuestType {

    DAILY_LESSON("오늘의 수련", "차시 1개 완료하기", Period.DAILY, Metric.LESSON, 1, 20),
    DAILY_COMMENT("따뜻한 한마디", "작품에 댓글 1개 남기기", Period.DAILY, Metric.COMMENT, 1, 10),
    DAILY_LIKE("응원 보내기", "작품 2개에 좋아요 누르기", Period.DAILY, Metric.LIKE, 2, 10),
    WEEKLY_LESSON("주간 수련", "차시 3개 완료하기", Period.WEEKLY, Metric.LESSON, 3, 60),
    WEEKLY_COMMENT("소통의 달인", "작품에 댓글 3개 남기기", Period.WEEKLY, Metric.COMMENT, 3, 40),
    WEEKLY_WORK("작품 공개", "일반 작품 1개 공개하기", Period.WEEKLY, Metric.WORK, 1, 50),
    WEEKLY_BOSS("보스 도전", "최종 과제 제출하기", Period.WEEKLY, Metric.SUBMIT, 1, 80);

    public enum Period {
        DAILY("일일"), WEEKLY("주간");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** 기간 시작 시각 (서버 시간대 기준) */
        public LocalDateTime startOf(LocalDate today) {
            return switch (this) {
                case DAILY -> today.atStartOfDay();
                case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            };
        }

        /** 보상 중복 수령을 막는 기간 키 — 일일 "2026-11-11", 주간 "2026-W46" */
        public String keyOf(LocalDate today) {
            return switch (this) {
                case DAILY -> today.toString();
                case WEEKLY -> today.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                        + String.format("%02d", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            };
        }
    }

    public enum Metric {
        LESSON, COMMENT, LIKE, WORK, SUBMIT;

        public int pick(PeriodStatDTO stat) {
            return switch (this) {
                case LESSON -> stat.getLessons();
                case COMMENT -> stat.getComments();
                case LIKE -> stat.getLikes();
                case WORK -> stat.getWorks();
                case SUBMIT -> stat.getSubmits();
            };
        }
    }

    private final String title;
    private final String description;
    private final Period period;
    private final Metric metric;
    private final int target;
    private final int rewardXp;

    QuestType(String title, String description, Period period, Metric metric, int target, int rewardXp) {
        this.title = title;
        this.description = description;
        this.period = period;
        this.metric = metric;
        this.target = target;
        this.rewardXp = rewardXp;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Period getPeriod() { return period; }
    public Metric getMetric() { return metric; }
    public int getTarget() { return target; }
    public int getRewardXp() { return rewardXp; }
}
