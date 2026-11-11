package com.wanted.lmspractice.domain.game.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** user_equipped_badges 복합 기본키 (user_id, slot_no) */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class UserEquippedBadgeId implements Serializable {
    private Long userId;
    private Integer slotNo;
}
