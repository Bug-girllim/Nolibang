package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 내 캐릭터 화면 전체 */
@Getter
@AllArgsConstructor
public class CharacterPageDTO {
    private CharacterDTO character;
    private List<XpLineDTO> xpLines;
    private List<QuestDTO> dailyQuests;
    private List<QuestDTO> weeklyQuests;
    private List<HobbyClassDTO> classes;
    private List<BossDTO> bosses;
    private List<AvatarGroupDTO> avatarGroups;
    private List<AchievementDTO> achievements;   // 업적 배지 전체(받은 것 + 못 받은 것)
    private int achievementEarned;                // 받은 업적 배지 수
    private List<CourseBadgeDTO> courseBadges;    // 수료 배지
    private List<EquippedBadgeDTO> equippedBadges; // 장착한 대표 배지(칸 번호 순)
    private int badgeSlotMax;                      // 장착 가능한 최대 개수
}
