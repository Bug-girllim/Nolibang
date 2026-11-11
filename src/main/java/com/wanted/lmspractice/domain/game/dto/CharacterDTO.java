package com.wanted.lmspractice.domain.game.dto;

import com.wanted.lmspractice.domain.game.entity.AvatarItem;
import com.wanted.lmspractice.domain.game.policy.GameAppearance;

import lombok.Getter;

/** 캐릭터 카드 — 레벨·경험치·꾸미기 장착 상태 */
@Getter
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
    private String outfitCode = "colored-pencil";
    private boolean crowned;

    /** 기존 호출부의 생성자 계약을 유지한다. */
    public CharacterDTO(String nickname, int level, String title, long totalXp, long levelStartXp,
                        Long nextLevelXp, int percent, String avatarEmoji, String frameClass,
                        String nameColorClass, int masterCount) {
        this.nickname = nickname;
        this.level = level;
        this.title = title;
        this.totalXp = totalXp;
        this.levelStartXp = levelStartXp;
        this.nextLevelXp = nextLevelXp;
        this.percent = percent;
        this.avatarEmoji = avatarEmoji;
        this.frameClass = frameClass;
        this.nameColorClass = nameColorClass;
        this.masterCount = masterCount;
    }

    public CharacterDTO withEquipment(String outfitCode, boolean crowned) {
        this.outfitCode = AvatarItem.find(AvatarItem.Slot.OUTFIT, outfitCode)
                .orElse(AvatarItem.defaultOf(AvatarItem.Slot.OUTFIT)).getCode();
        this.crowned = crowned && masterCount > 0;
        return this;
    }

    private AvatarItem displayAvatar() {
        return AvatarItem.of(AvatarItem.Slot.AVATAR).stream()
                .filter(item -> item.getValue().equals(avatarEmoji))
                .findFirst().orElse(AvatarItem.defaultOf(AvatarItem.Slot.AVATAR));
    }

    public String getAvatarImagePath() { return displayAvatar().getImagePath(); }
    public String getAvatarLabel() { return displayAvatar().getLabel(); }
    public String getAvatarCode() { return displayAvatar().getCode(); }
    public String getCardImagePath() { return professionImage(outfitCode); }
    public String getOutfitLabel() {
        return AvatarItem.find(AvatarItem.Slot.OUTFIT, outfitCode)
                .orElse(AvatarItem.defaultOf(AvatarItem.Slot.OUTFIT)).getLabel();
    }
    public String professionImage(String hobbyCode) {
        return GameAppearance.professionPath(getAvatarCode(), hobbyCode);
    }
}
