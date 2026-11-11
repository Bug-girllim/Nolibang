package com.wanted.lmspractice.domain.game.dto;

import com.wanted.lmspractice.domain.game.entity.AvatarItem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 캐릭터 카드 — 레벨·경험치·꾸미기 장착 상태 */
@Getter
@AllArgsConstructor
public class CharacterDTO {
    private String nickname;
    private int level;
    private String title;
    private long totalXp;
    private long levelStartXp;
    private Long nextLevelXp;     // 최고 레벨이면 null
    private int percent;          // 현재 레벨 안에서의 진행률
    private String avatarEmoji;
    private String frameClass;
    private String nameColorClass;
    private int masterCount;

    private AvatarItem displayAvatar() {
        return AvatarItem.of(AvatarItem.Slot.AVATAR).stream()
                .filter(item -> item.getValue().equals(avatarEmoji))
                .findFirst().orElse(AvatarItem.defaultOf(AvatarItem.Slot.AVATAR));
    }

    public String getAvatarImagePath() { return displayAvatar().getImagePath(); }
    public String getAvatarLabel() { return displayAvatar().getLabel(); }
}
