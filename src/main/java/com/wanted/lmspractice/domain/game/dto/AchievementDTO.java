package com.wanted.lmspractice.domain.game.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 업적 배지 카드 한 장 — 받았으면 컬러, 못 받았으면 회색 + 진행도(예: 3 / 10) */
@Getter
@AllArgsConstructor
public class AchievementDTO {
    private String code;
    private String emoji;
    private String title;
    private String description;
    private String group;
    private boolean earned;
    private LocalDateTime earnedAt;   // 못 받았으면 null
    private boolean fresh;            // 이번에 처음 받음 — 화면에 NEW 표시 (알림 대신)
    private int current;              // 목표까지만 보여 준다
    private int target;
    private int percent;
    private boolean equipped;         // 대표 배지로 장착 중
}
