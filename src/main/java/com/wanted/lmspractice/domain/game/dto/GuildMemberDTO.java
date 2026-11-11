package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 길드(분반) 멤버 한 줄 — 닉네임만 보인다(실명 비공개) */
@Getter
@AllArgsConstructor
public class GuildMemberDTO {
    private int rank;
    private String nickname;
    private int level;
    private long xp;
    private boolean me;
}
