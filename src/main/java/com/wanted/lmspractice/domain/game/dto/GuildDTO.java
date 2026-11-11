package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 길드(분반) 한 곳 — 멤버 합산 경험치로 순위를 매긴다. 멤버 목록은 내 길드만 채운다 */
@Getter
@AllArgsConstructor
public class GuildDTO {
    private int rank;
    private String name;
    private String instructorName;   // 길드마스터(담당 강사) 닉네임
    private int memberCount;
    private long totalXp;
    private boolean mine;
    private List<GuildMemberDTO> members;
}
