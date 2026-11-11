package com.wanted.lmspractice.domain.game.dto;

import com.wanted.lmspractice.domain.game.entity.AvatarItem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 꾸미기 아이템 카드 한 장 */
@Getter
@AllArgsConstructor
public class AvatarOptionDTO {
    private String slot;       // AvatarItem.Slot 이름
    private String code;
    private String label;
    private String value;      // 이모지 또는 css 클래스
    private boolean unlocked;
    private boolean equipped;
    private String hint;       // 잠긴 아이템의 해금 조건

    public String getImagePath() {
        if ("AVATAR".equals(slot)) {
            return AvatarItem.find(AvatarItem.Slot.AVATAR, code)
                    .map(AvatarItem::getImagePath).orElse(null);
        }
        if ("FRAME".equals(slot)) {
            return AvatarItem.find(AvatarItem.Slot.FRAME, code)
                    .map(AvatarItem::getImagePath).orElse(null);
        }
        return null;
    }
}
