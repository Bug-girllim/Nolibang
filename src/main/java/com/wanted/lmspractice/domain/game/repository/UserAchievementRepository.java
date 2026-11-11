package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.UserAchievement;
import com.wanted.lmspractice.domain.game.entity.UserAchievementId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievementId> {

    List<UserAchievement> findByUserId(Long userId);

    /**
     * 배지 저장 — 이미 있으면 건너뛴다(INSERT IGNORE).
     * 화면을 동시에 두 번 열어도 기본키 중복 예외로 요청 전체가 롤백되지 않게, 예외 대신 "0행"을 돌려받는다.
     *
     * @return 새로 저장했으면 1, 이미 있었으면 0
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO user_achievements (user_id, badge_code) VALUES (:userId, :badgeCode)",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("badgeCode") String badgeCode);
}
