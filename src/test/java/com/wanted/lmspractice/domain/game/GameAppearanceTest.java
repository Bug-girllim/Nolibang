package com.wanted.lmspractice.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.game.dto.BossDTO;
import com.wanted.lmspractice.domain.game.dto.CharacterDTO;
import com.wanted.lmspractice.domain.game.dto.HobbyClassDTO;
import com.wanted.lmspractice.domain.game.policy.GameAppearance;
import com.wanted.lmspractice.domain.game.entity.AvatarItem;
import com.wanted.lmspractice.domain.game.entity.UserAvatar;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DB 없이 실행하는 외형 회귀 테스트: 선택 동물이 직업·보스전·보상에서 바뀌지 않는다. */
class GameAppearanceTest {
    @Test
    void everyAnimalHasEveryHobbyOutfit() {
        for (String animal : List.of("chick", "fox", "panda", "owl", "dragon")) {
            for (String hobby : List.of("colored-pencil", "digital-drawing", "dessert")) {
                String path = GameAppearance.professionPath(animal, hobby);
                assertThat(path).isEqualTo("/images/game/outfits/" + animal + "-" + hobby + ".png");
                assertThat(getClass().getResource("/static" + path)).as(path).isNotNull();
            }
        }
    }

    @Test
    void pandaStaysPandaOnProfessionBattleAndRewardAtEveryRank() {
        CharacterDTO panda = new CharacterDTO("판다 사용자", 12, "장인", 7000, 6600,
                7800L, 33, "🐼", "frame-gold", "nc-blue", 1);
        for (String stage : List.of("apprentice", "skilled", "expert", "master")) {
            BossDTO boss = BossDTO.builder().bossCode("colored-pencil")
                    .rewardClassName("색연필화 견습생").build();
            boss.applyAppearance(panda, stage);
            assertThat(boss.getHeroImagePath()).endsWith("panda-colored-pencil.png");
            assertThat(boss.getRewardClassImage()).isEqualTo(boss.getHeroImagePath());
            assertThat(boss.getHeroStage()).isEqualTo(stage);
        }
        HobbyClassDTO artist = new HobbyClassDTO(1L, "색연필화", "색연필화 견습생",
                List.of(new HobbyClassDTO.Step("입문", true)), false, 1);
        assertThat(artist.imageFor(panda.getAvatarCode())).endsWith("panda-colored-pencil.png");
    }

    @Test
    void unknownHobbyKeepsSelectedIdentityAndPathsAreAllowlisted() {
        assertThat(GameAppearance.professionPath("dragon", "future-hobby"))
                .isEqualTo("/images/game/avatars/dragon.png");
        assertThat(GameAppearance.professionPath("../../elsewhere", "dessert"))
                .isEqualTo("/images/game/outfits/chick-dessert.png");
        assertThat(GameAppearance.professionPath(null, null))
                .isEqualTo("/images/game/avatars/chick.png");
    }

    @Test
    void crownIsSeparateFromAnimalAndOutfitAndNeverChangesEither() {
        UserAvatar equipment = UserAvatar.create(2L);
        equipment.equip(AvatarItem.Slot.AVATAR, "panda");
        equipment.equip(AvatarItem.Slot.OUTFIT, "dessert");
        equipment.equip(AvatarItem.Slot.HEAD, "crown");
        assertThat(equipment.getAvatarCode()).isEqualTo("panda");
        assertThat(equipment.getOutfitCode()).isEqualTo("dessert");
        equipment.equip(AvatarItem.Slot.AVATAR, "dragon");
        equipment.equip(AvatarItem.Slot.OUTFIT, "digital-drawing");
        assertThat(equipment.getHeadCode()).isEqualTo("crown");
        equipment.equip(AvatarItem.Slot.HEAD, "none");
        assertThat(equipment.getAvatarCode()).isEqualTo("dragon");
        assertThat(equipment.getOutfitCode()).isEqualTo("digital-drawing");
        assertThat(AvatarItem.find(AvatarItem.Slot.AVATAR, "crown")).isEmpty();
    }

    @Test
    void selectedAnimalAndSelectedOutfitRemainVisibleWithCrownOnOrOff() {
        String[][] animals = {{"🐣", "chick"}, {"🦊", "fox"}, {"🐼", "panda"}, {"🦉", "owl"}, {"🐲", "dragon"}};
        for (String[] animal : animals) {
            for (String hobby : List.of("colored-pencil", "digital-drawing", "dessert")) {
                CharacterDTO character = new CharacterDTO("학습자", 12, "장인", 7000, 6600,
                        7800L, 33, animal[0], "frame-gold", "nc-blue", 1).withEquipment(hobby, true);
                assertThat(character.getCardImagePath()).endsWith(animal[1] + "-" + hobby + ".png");
                assertThat(character.isCrowned()).isTrue();
                BossDTO boss = BossDTO.builder().bossCode(hobby).rewardClassName("직업").build();
                boss.applyAppearance(character, "apprentice");
                assertThat(boss.isHeroCrowned()).isTrue();
                assertThat(boss.getHeroImagePath()).isEqualTo(character.getCardImagePath());
                assertThat(boss.getRewardClassImage()).isEqualTo(character.getCardImagePath());
                character.withEquipment(hobby, false);
                assertThat(character.getCardImagePath()).endsWith(animal[1] + "-" + hobby + ".png");
                boss.applyAppearance(character, "master");
                assertThat(boss.isHeroCrowned()).isFalse(); // 직업이 마스터여도 벗긴 왕관은 강제로 표시하지 않는다.
            }
        }
    }

    @Test
    void missingMasterBadgePreventsCrownDisplay() {
        CharacterDTO character = new CharacterDTO("학습자", 12, "장인", 7000, 6600,
                7800L, 33, "🐼", "frame-gold", "nc-blue", 0).withEquipment("dessert", true);
        assertThat(character.isCrowned()).isFalse();
        assertThat(character.getCardImagePath()).endsWith("panda-dessert.png");
    }
}
