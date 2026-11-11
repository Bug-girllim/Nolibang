package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 작성자 이름 옆·주변에 보이는 꾸밈 — 대표 배지(이모지) + 닉네임 색 + 닉네임을 감싸는 테두리.
 * 값이 없는 항목은 빈 목록·null 이고, 화면은 그대로 평범한 이름을 보여 준다.
 */
@Getter
@AllArgsConstructor
public class AuthorStyleDTO {

    private List<EquippedBadgeDTO> badges;
    private String nameColorClass;   // 예: an-nc-green — 직접 장착한 경우만 (장착한 적 없으면 null)
    private String frameClass;       // 예: an-frame-gold — 직접 장착한 경우만

    /** 꾸밈이 하나도 없는 작성자 */
    public static AuthorStyleDTO none() {
        return new AuthorStyleDTO(List.of(), null, null);
    }
}
