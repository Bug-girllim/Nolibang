package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 꾸미기 슬롯 한 묶음 (캐릭터·대표 취미 의상·머리 장식·테두리·닉네임 색) */
@Getter
@AllArgsConstructor
public class AvatarGroupDTO {
    private String slot;
    private String slotLabel;
    private List<AvatarOptionDTO> options;
}
