package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 길드 화면 — 내 길드 + 전체 길드 순위 */
@Getter
@AllArgsConstructor
public class GuildPageDTO {
    private List<GuildDTO> myGuilds;
    private List<GuildDTO> ranking;
}
