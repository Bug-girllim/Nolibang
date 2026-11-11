package com.wanted.lmspractice.domain.game.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** user_achievements 복합 기본키 (user_id, badge_code) */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class UserAchievementId implements Serializable {
    private Long userId;
    private String badgeCode;
}
