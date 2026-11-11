package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

    /** 회원의 user_avatars 행 전부 삭제 — 회원 탈퇴(U-15 4번 배지·개인 기록). 호출: GameService.deleteAllOfUserForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM user_avatars WHERE user_id = :userId", nativeQuery = true)
    int deleteAllByUserId(@Param("userId") Long userId);
}
