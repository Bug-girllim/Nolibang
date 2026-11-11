package com.wanted.lmspractice.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 수료 배지 한 개 — 기존 BadgeService 의 입문·중급·고급·마스터 배지를 화면용으로 바꾼 값 */
@Getter
@AllArgsConstructor
public class CourseBadgeDTO {
    private String code;        // 장착용 코드 C{취미 id}-{B|I|A|M}
    private String emoji;       // 입문 🥉 · 중급 🥈 · 고급 🥇 · 마스터 👑
    private String hobbyName;
    private String levelLabel;  // 입문·중급·고급·마스터
    private boolean master;
    private boolean equipped;   // 대표 배지로 장착 중
}
