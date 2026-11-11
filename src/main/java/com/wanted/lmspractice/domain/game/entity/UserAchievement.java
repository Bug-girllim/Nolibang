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
 * [역할] 업적 배지 획득 기록 1건 — 같은 배지는 한 번만 받는다(기본키가 막는다). 행은 지우거나 고치지 않는다(이력).
 * [연결] 만든 곳: UserAchievementRepository.insertIgnore(GameService.syncAchievements) / 읽는 곳: GameService(획득 날짜·"새로 받음" 표시)
 * 칼럼은 schema.sql 의 user_achievements 와 1:1. badge_code 는 AchievementType 의 name().
 */
@Entity
@Table(name = "user_achievements")
@IdClass(UserAchievementId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserAchievement {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "badge_code", length = 40)
    private String badgeCode;

    @Column(name = "earned_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime earnedAt;   // DB가 자동 기록
}
