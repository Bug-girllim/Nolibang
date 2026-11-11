package com.wanted.lmspractice.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [역할] 장착한 대표 배지 1개 — 사용자당 최대 3칸(slot_no 1~3). 해제하면 행을 지운다.
 * [연결] 만든 곳: UserEquippedBadgeRepository.insertIgnore(GameService.equipBadge) / 읽는 곳: GameService.getCharacterPage(캐릭터 카드)
 * 칼럼은 schema.sql 의 user_equipped_badges 와 1:1. badge_code 는 업적 배지면 AchievementType 이름, 수료 배지면 C{취미 id}-{B|I|A|M}.
 */
@Entity
@Table(name = "user_equipped_badges")
@IdClass(UserEquippedBadgeId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserEquippedBadge {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "slot_no")
    private Integer slotNo;

    @Column(name = "badge_code", nullable = false, length = 40)
    private String badgeCode;
}
