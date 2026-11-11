package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 기간(오늘·이번 주) 안의 활동 수 — 퀘스트 진행도 계산용 */
@Getter
@AllArgsConstructor
public class PeriodStatDTO {
    private int lessons;
    private int comments;
    private int likes;
    private int works;
    private int submits;
}
