package com.wanted.lmspractice.domain.game.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** quest_claims 복합 기본키 (user_id, quest_key, period_key) */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class QuestClaimId implements Serializable {
    private Long userId;
    private String questKey;
    private String periodKey;
}
