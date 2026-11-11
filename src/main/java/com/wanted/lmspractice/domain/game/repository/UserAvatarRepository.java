package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
