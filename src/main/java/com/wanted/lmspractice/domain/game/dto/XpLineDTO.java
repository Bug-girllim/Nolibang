package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 경험치 내역 한 줄 (예: 차시 완료 12개 → 360 XP) */
@Getter
@AllArgsConstructor
public class XpLineDTO {
    private String label;
    private long count;
    private long xp;
}
