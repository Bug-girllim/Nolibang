package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 퀘스트 카드 한 장 */
@Getter
@AllArgsConstructor
public class QuestDTO {
    private String key;
    private String title;
    private String description;
    private String periodLabel;
    private int rewardXp;
    private int progress;      // 목표를 넘어도 목표까지만 보여 준다
    private int target;
    private boolean claimed;   // 이번 기간 보상을 이미 받음
    private boolean claimable; // 달성했고 아직 안 받음
    private int percent;
}
