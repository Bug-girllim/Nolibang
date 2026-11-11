package com.wanted.lmspractice.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [역할] 퀘스트 보상을 받은 기록 1건 — 같은 기간(오늘·이번 주)에 같은 퀘스트는 1번만 받는다(기본키가 막는다)
 * [연결] 만든 곳: GameService.claimQuest / 읽는 곳: GameService(받은 기록 확인), GameStatQuery(퀘스트 경험치 합계)
 * 칼럼은 schema.sql 의 quest_claims 와 1:1. 행은 지우거나 고치지 않는다(이력).
 */
@Entity
@Table(name = "quest_claims")
@IdClass(QuestClaimId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class QuestClaim {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "quest_key", length = 40)
    private String questKey;

    @Id
    @Column(name = "period_key", length = 20)
    private String periodKey;

    @Column(name = "reward_xp", nullable = false)
    private int rewardXp;

    @Column(name = "claimed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime claimedAt;   // DB가 자동 기록

    public static QuestClaim create(Long userId, String questKey, String periodKey, int rewardXp) {
        QuestClaim claim = new QuestClaim();
        claim.userId = userId;
        claim.questKey = questKey;
        claim.periodKey = periodKey;
        claim.rewardXp = rewardXp;
        return claim;
    }
}
