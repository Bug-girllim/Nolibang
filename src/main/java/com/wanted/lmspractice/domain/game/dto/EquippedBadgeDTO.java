package com.wanted.lmspractice.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 캐릭터 카드에 보이는 장착 배지 1개 */
@Getter
@AllArgsConstructor
public class EquippedBadgeDTO {
    private String code;
    private String emoji;
    private String title;   // 업적 배지 이름 또는 "색연필화 입문"
}
