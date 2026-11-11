package com.wanted.lmspractice.domain.game.policy;

import java.util.Set;

/** 선택 동물과 취미 의상을 조합한다. 경로는 서버에서 정한 코드만 허용한다. */
public final class GameAppearance {
    private static final Set<String> AVATARS = Set.of("chick", "fox", "panda", "owl", "dragon");
    private static final Set<String> HOBBIES = Set.of("colored-pencil", "digital-drawing", "dessert");

    private GameAppearance() { }

    public static String professionPath(String avatarCode, String hobbyCode) {
        String animal = AVATARS.contains(avatarCode == null ? "" : avatarCode) ? avatarCode : "chick";
        if (!HOBBIES.contains(hobbyCode == null ? "" : hobbyCode)) {
            return "/images/game/avatars/" + animal + ".png";
        }
        return "/images/game/outfits/" + animal + "-" + hobbyCode + ".png";
    }
}
