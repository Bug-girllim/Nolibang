package com.wanted.lmspractice.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.game.dto.ActivityStatDTO;
import com.wanted.lmspractice.domain.game.entity.AvatarItem;
import com.wanted.lmspractice.domain.game.policy.AchievementType;
import com.wanted.lmspractice.domain.game.policy.AchievementType.AchievementContext;
import com.wanted.lmspractice.domain.game.policy.GameRules;
import com.wanted.lmspractice.domain.game.policy.QuestType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] RPG 규칙(경험치·레벨 곡선·퀘스트 기간·꾸미기 해금) 단위 테스트 — DB·Spring 없이 돈다
 */
class GameRulesTest {

    @Test
    @DisplayName("레벨 곡선: 레벨 L 에 도달하는 누적 경험치는 50×L×(L−1) 이고, 경계값에서 레벨이 바뀐다")
    void levelCurve() {
        assertThat(GameRules.xpToReach(1)).isEqualTo(0);
        assertThat(GameRules.xpToReach(2)).isEqualTo(100);
        assertThat(GameRules.xpToReach(3)).isEqualTo(300);

        assertThat(GameRules.levelOf(0)).isEqualTo(1);
        assertThat(GameRules.levelOf(99)).isEqualTo(1);
        assertThat(GameRules.levelOf(100)).isEqualTo(2);
        assertThat(GameRules.levelOf(299)).isEqualTo(2);
        assertThat(GameRules.levelOf(300)).isEqualTo(3);
        assertThat(GameRules.levelOf(Long.MAX_VALUE / 2)).isEqualTo(GameRules.MAX_LEVEL);   // 최고 레벨을 넘지 않는다
    }

    @Test
    @DisplayName("총 경험치: 차시·제출·작품·보스·퀘스트를 합치고, 댓글·좋아요는 상한까지만 센다")
    void totalXp() {
        ActivityStatDTO stat = new ActivityStatDTO(1L, 3, 2, 200, 1, 50, 500, 60, 1, 4);
        long expected = 3 * 30 + 2 * 50 + 200 + 1 * 40
                + GameRules.COMMENT_XP_CAP * 5      // 댓글 50개를 써도 상한 20개분만
                + GameRules.LIKE_XP_CAP * 3         // 좋아요 500개를 받아도 상한 100개분만
                + 60;
        assertThat(GameRules.totalXp(stat)).isEqualTo(expected);
    }

    @Test
    @DisplayName("칭호·직업 이름: 레벨과 수료 수준에 따라 바뀐다")
    void titles() {
        assertThat(GameRules.titleOf(1)).isEqualTo("새싹 놀이꾼");
        assertThat(GameRules.titleOf(5)).isEqualTo("수련생");
        assertThat(GameRules.titleOf(12)).isEqualTo("장인");
        assertThat(GameRules.classNameOf("색연필화", 0, false)).isEqualTo("색연필화 입문 전");
        assertThat(GameRules.classNameOf("색연필화", 2, false)).isEqualTo("색연필화 숙련자");
        assertThat(GameRules.classNameOf("색연필화", 3, true)).isEqualTo("색연필화 마스터");
    }

    @Test
    @DisplayName("퀘스트 기간: 일일은 오늘 0시, 주간은 그 주 월요일 0시부터이고, 기간 키가 다르면 보상을 다시 받을 수 있다")
    void questPeriod() {
        LocalDate wednesday = LocalDate.of(2026, 11, 11);   // 2026-11-11 은 수요일
        assertThat(QuestType.Period.DAILY.startOf(wednesday)).isEqualTo(wednesday.atStartOfDay());
        assertThat(QuestType.Period.WEEKLY.startOf(wednesday)).isEqualTo(LocalDate.of(2026, 11, 9).atStartOfDay());

        assertThat(QuestType.Period.DAILY.keyOf(wednesday)).isEqualTo("2026-11-11");
        assertThat(QuestType.Period.WEEKLY.keyOf(wednesday)).isEqualTo("2026-W46");
        assertThat(QuestType.Period.WEEKLY.keyOf(wednesday.plusDays(7))).isEqualTo("2026-W47");
        assertThat(QuestType.Period.WEEKLY.keyOf(LocalDate.of(2026, 11, 9)))                // 같은 주 월요일·일요일은 같은 키
                .isEqualTo(QuestType.Period.WEEKLY.keyOf(LocalDate.of(2026, 11, 15)));
    }

    @Test
    @DisplayName("꾸미기 해금: 레벨 조건, 마스터 배지 조건을 모두 만족해야 열리고, 슬롯마다 기본 아이템이 있다")
    void avatarUnlock() {
        assertThat(AvatarItem.FRAME_GOLD.isUnlocked(7, true)).isFalse();     // 레벨 부족
        assertThat(AvatarItem.FRAME_GOLD.isUnlocked(8, false)).isTrue();
        assertThat(AvatarItem.AVATAR_CROWN.isUnlocked(50, false)).isFalse(); // 마스터 배지 없음
        assertThat(AvatarItem.AVATAR_CROWN.isUnlocked(1, true)).isTrue();

        for (AvatarItem.Slot slot : AvatarItem.Slot.values()) {
            assertThat(AvatarItem.defaultOf(slot).isUnlocked(1, false)).as(slot + " 기본 아이템은 처음부터 열려 있다").isTrue();
        }
        assertThat(AvatarItem.find(AvatarItem.Slot.FRAME, "없는코드")).isEmpty();
    }

    @Test
    @DisplayName("업적 배지 조건: 목표 값에 닿아야 달성이고, 기록이 없으면 하나도 달성하지 못한다")
    void achievements() {
        AchievementContext none = new AchievementContext(new ActivityStatDTO(1L, 0, 0, 0, 0, 0, 0, 0, 0, 0), 1, 0);
        for (AchievementType type : AchievementType.values()) {
            assertThat(type.isAchieved(none)).as(type + " — 기록이 없으면 미달성").isFalse();
        }

        AchievementContext some = new AchievementContext(new ActivityStatDTO(1L, 10, 1, 0, 0, 1, 9, 0, 1, 4), 5, 0);
        assertThat(AchievementType.LESSON_1.isAchieved(some)).isTrue();
        assertThat(AchievementType.LESSON_10.isAchieved(some)).isTrue();    // 정확히 목표 값 = 달성
        assertThat(AchievementType.LESSON_30.isAchieved(some)).isFalse();
        assertThat(AchievementType.BOSS_1.isAchieved(some)).isTrue();
        assertThat(AchievementType.BOSS_3.isAchieved(some)).isFalse();
        assertThat(AchievementType.COMMENT_1.isAchieved(some)).isTrue();
        assertThat(AchievementType.LIKE_10.isAchieved(some)).isFalse();      // 9개 — 1개 모자란다
        assertThat(AchievementType.QUEST_5.isAchieved(some)).isFalse();
        assertThat(AchievementType.LEVEL_5.isAchieved(some)).isTrue();
        assertThat(AchievementType.MASTER_1.isAchieved(some)).isFalse();
        assertThat(AchievementType.MASTER_1.isAchieved(new AchievementContext(some.stat(), 5, 1))).isTrue();
    }
}
