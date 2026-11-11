package com.wanted.lmspractice.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [역할] 캐릭터 꾸미기 장착 상태 — 사용자당 1행. 값이 null 이면 슬롯의 기본 아이템을 쓴다
 * [연결] 만든 곳: GameService.equip(JPA 변경 감지로 저장) / 읽는 곳: GameService.getCharacterPage
 * 장착 가능 여부(해금 조건)는 AvatarItem 과 GameService 가 판단한다. 이 Entity 는 값만 가진다.
 */
@Entity
@Table(name = "user_avatars")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "avatar_code", length = 30)
    private String avatarCode;

    @Column(name = "frame_code", length = 30)
    private String frameCode;

    @Column(name = "name_color_code", length = 30)
    private String nameColorCode;

    public static UserAvatar create(Long userId) {
        UserAvatar avatar = new UserAvatar();
        avatar.userId = userId;
        return avatar;
    }

    public void equip(AvatarItem.Slot slot, String code) {
        switch (slot) {
            case AVATAR -> this.avatarCode = code;
            case FRAME -> this.frameCode = code;
            case NAME_COLOR -> this.nameColorCode = code;
        }
    }

    public String codeOf(AvatarItem.Slot slot) {
        return switch (slot) {
            case AVATAR -> avatarCode;
            case FRAME -> frameCode;
            case NAME_COLOR -> nameColorCode;
        };
    }
}
