package com.wanted.lmspractice.domain.game.policy;

import com.wanted.lmspractice.domain.game.dto.ActivityStatDTO;
import java.util.function.ToIntFunction;

/**
 * [역할] 업적 배지 목록(15종)과 달성 조건. DB에 두지 않고 코드로 정의한다 — 달성 여부는 활동 기록에서 계산하고,
 *        받은 기록(획득 날짜)만 user_achievements 에 남긴다.
 * [연결] 사용처: GameService.syncAchievements(달성 판단·저장), templates/game/character.html(배지 카드)
 * [규칙] current(지금 값) ≥ target(목표) 이면 획득. 아이콘은 이모지(emoji)를 쓴다.
 *        코드(name())는 user_achievements.badge_code 에 저장되므로, 이미 배포한 뒤에는 이름을 바꾸지 않는다.
 *        기존 "수료 배지"(BadgeService — 입문·중급·고급·마스터)와는 별개이며, 화면에서만 나란히 보여 준다.
 */
public enum AchievementType {

    // 학습
    LESSON_1("🌱", "첫걸음", "차시를 1개 완료했어요", "학습", 1, c -> c.stat().getLessons()),
    LESSON_10("📚", "열공러", "차시를 10개 완료했어요", "학습", 10, c -> c.stat().getLessons()),
    LESSON_30("🎓", "배움의 달인", "차시를 30개 완료했어요", "학습", 30, c -> c.stat().getLessons()),
    SUBMIT_1("📝", "첫 도전", "최종 과제를 처음 제출했어요", "학습", 1, c -> c.stat().getSubmits()),
    BOSS_1("⚔️", "보스 슬레이어", "보스를 처음 격파(수료)했어요", "학습", 1, c -> c.stat().getBosses()),
    BOSS_3("🏆", "보스 헌터", "보스를 3번 격파(수료)했어요", "학습", 3, c -> c.stat().getBosses()),

    // 작품·소통
    WORK_1("🖼️", "첫 작품", "작품을 처음 공개했어요", "작품·소통", 1, c -> c.stat().getWorks()),
    WORK_5("🎨", "다작가", "작품을 5개 공개했어요", "작품·소통", 5, c -> c.stat().getWorks()),
    COMMENT_1("💬", "첫 한마디", "작품에 댓글을 처음 남겼어요", "작품·소통", 1, c -> c.stat().getComments()),
    COMMENT_10("🗣️", "소통왕", "댓글을 10개 남겼어요", "작품·소통", 10, c -> c.stat().getComments()),
    LIKE_10("❤️", "인기인", "내 작품이 좋아요를 10개 받았어요", "작품·소통", 10, c -> c.stat().getLikes()),

    // 성장
    QUEST_5("🎯", "퀘스트 러너", "퀘스트 보상을 5번 받았어요", "성장", 5, c -> c.stat().getQuests()),
    LEVEL_5("⭐", "수련생", "Lv.5에 도달했어요", "성장", 5, AchievementContext::level),
    LEVEL_10("🌟", "숙련의 길", "Lv.10에 도달했어요", "성장", 10, AchievementContext::level),
    MASTER_1("👑", "마스터", "한 취미의 마스터 배지를 얻었어요", "성장", 1, AchievementContext::masterCount);

    /** 달성 판단에 쓰는 재료 — 활동 집계 + 레벨 + 마스터 배지 수 */
    public record AchievementContext(ActivityStatDTO stat, int level, int masterCount) {
    }

    private final String emoji;
    private final String title;
    private final String description;
    private final String group;
    private final int target;
    private final ToIntFunction<AchievementContext> metric;

    AchievementType(String emoji, String title, String description, String group, int target,
                    ToIntFunction<AchievementContext> metric) {
        this.emoji = emoji;
        this.title = title;
        this.description = description;
        this.group = group;
        this.target = target;
        this.metric = metric;
    }

    public String getEmoji() { return emoji; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getGroup() { return group; }
    public int getTarget() { return target; }

    /** 지금 값 (목표를 넘어도 그대로 돌려준다 — 화면에서는 목표까지만 보여 준다) */
    public int current(AchievementContext context) {
        return metric.applyAsInt(context);
    }

    public boolean isAchieved(AchievementContext context) {
        return current(context) >= target;
    }
}
