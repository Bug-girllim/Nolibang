package com.wanted.lmspractice.domain.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.game.dto.AchievementDTO;
import com.wanted.lmspractice.domain.game.dto.CharacterPageDTO;
import com.wanted.lmspractice.domain.game.dto.GuildPageDTO;
import com.wanted.lmspractice.domain.game.dto.QuestDTO;
import com.wanted.lmspractice.domain.game.repository.UserAchievementRepository;
import com.wanted.lmspractice.domain.game.repository.UserAvatarRepository;
import com.wanted.lmspractice.domain.game.repository.UserEquippedBadgeRepository;
import com.wanted.lmspractice.domain.game.service.GameService;
import com.wanted.lmspractice.domain.game.service.GameService.BadgeEquipResult;
import com.wanted.lmspractice.domain.game.service.GameService.ClaimResult;
import com.wanted.lmspractice.domain.game.service.GameService.EquipResult;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] RPG 서비스 통합 테스트 — 캐릭터 화면 조립, 퀘스트 보상, 꾸미기 장착, 길드 순위
 * [전제] 로컬 MySQL(nolibang)에 schema.sql(quest_claims·user_avatars 포함)·seed.sql이 적재돼 있어야 한다.
 *       시드 사용자: 2 learner(학습자). @Transactional이라 테스트가 끝나면 바꾼 데이터는 되돌린다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class GameServiceTest {

    private static final Long LEARNER = 2L;

    @Autowired
    private GameService gameService;

    @Autowired
    private UserAvatarRepository userAvatarRepository;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Autowired
    private UserEquippedBadgeRepository userEquippedBadgeRepository;

    /**
     * 앱을 직접 써서 DB에 남은 대표 배지 장착 기록이 테스트 결과를 바꾸지 않도록, 각 테스트를 빈 상태에서 시작한다.
     * 클래스가 @Transactional 이라 이 삭제도 테스트가 끝나면 되돌려진다(실제 데이터는 그대로).
     */
    @BeforeEach
    void clearEquippedBadges() {
        userEquippedBadgeRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("캐릭터 화면: 레벨·경험치 막대·퀘스트·직업·꾸미기가 모두 채워져서 나온다")
    void characterPage() {
        CharacterPageDTO page = gameService.getCharacterPage(LEARNER, "테스터");

        assertThat(page.getCharacter().getLevel()).isGreaterThanOrEqualTo(1);
        assertThat(page.getCharacter().getPercent()).isBetween(0, 100);
        assertThat(page.getCharacter().getTotalXp()).isGreaterThanOrEqualTo(page.getCharacter().getLevelStartXp());
        assertThat(page.getXpLines()).hasSize(7);
        assertThat(page.getDailyQuests()).isNotEmpty();
        assertThat(page.getWeeklyQuests()).isNotEmpty();
        assertThat(page.getAvatarGroups()).hasSize(3);
        assertThat(page.getClasses()).isNotEmpty();
        // 경험치 내역의 합 = 총 경험치
        assertThat(page.getXpLines().stream().mapToLong(l -> l.getXp()).sum()).isEqualTo(page.getCharacter().getTotalXp());
    }

    @Test
    @DisplayName("퀘스트 보상: 달성하지 못했으면 못 받고, 없는 퀘스트 키는 입력 오류다")
    void claimQuest() {
        CharacterPageDTO before = gameService.getCharacterPage(LEARNER, "테스터");
        QuestDTO notDone = before.getDailyQuests().stream().filter(q -> !q.isClaimable() && !q.isClaimed()).findFirst().orElse(null);
        if (notDone != null) {
            assertThat(gameService.claimQuest(LEARNER, notDone.getKey())).isEqualTo(ClaimResult.NOT_ENOUGH);
        }
        assertThatThrownBy(() -> gameService.claimQuest(LEARNER, "NO_SUCH_QUEST"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("퀘스트 보상은 기간당 1번: 받을 수 있는 퀘스트를 받으면 경험치가 늘고, 다시 받으면 이미 받음으로 막힌다")
    void claimOnlyOncePerPeriod() {
        CharacterPageDTO before = gameService.getCharacterPage(LEARNER, "테스터");
        QuestDTO claimable = java.util.stream.Stream.concat(before.getDailyQuests().stream(), before.getWeeklyQuests().stream())
                .filter(QuestDTO::isClaimable).findFirst().orElse(null);
        if (claimable == null) {
            return;   // 오늘·이번 주에 달성한 퀘스트가 없는 시드 상태 — 달성 조건 판단은 위 테스트가 본다
        }
        assertThat(gameService.claimQuest(LEARNER, claimable.getKey())).isEqualTo(ClaimResult.CLAIMED);
        assertThat(gameService.claimQuest(LEARNER, claimable.getKey())).isEqualTo(ClaimResult.ALREADY_CLAIMED);
        CharacterPageDTO after = gameService.getCharacterPage(LEARNER, "테스터");
        assertThat(after.getCharacter().getTotalXp()).isEqualTo(before.getCharacter().getTotalXp() + claimable.getRewardXp());
    }

    @Test
    @DisplayName("꾸미기: 기본 아이템은 장착되고, 해금되지 않은 아이템은 서버가 거절하며, 없는 아이템 코드는 입력 오류다")
    void equip() {
        assertThat(gameService.equip(LEARNER, "AVATAR", "chick")).isEqualTo(EquipResult.EQUIPPED);
        assertThat(userAvatarRepository.findById(LEARNER)).get().extracting("avatarCode").isEqualTo("chick");

        assertThat(gameService.equip(LEARNER, "AVATAR", "crown")).isEqualTo(EquipResult.LOCKED);   // 마스터 배지 없이는 잠김
        assertThat(userAvatarRepository.findById(LEARNER)).get().extracting("avatarCode").isEqualTo("chick");   // 바뀌지 않았다

        assertThatThrownBy(() -> gameService.equip(LEARNER, "AVATAR", "nothing"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> gameService.equip(LEARNER, "BAD_SLOT", "chick"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("길드 순위: 순위는 1부터 시작해 경험치 내림차순이고, 멤버 목록은 내 길드에만 채워진다")
    void guildPage() {
        GuildPageDTO page = gameService.getGuildPage(LEARNER);

        assertThat(page.getRanking()).isNotEmpty();
        assertThat(page.getRanking().get(0).getRank()).isEqualTo(1);
        for (int i = 1; i < page.getRanking().size(); i++) {
            assertThat(page.getRanking().get(i - 1).getTotalXp()).isGreaterThanOrEqualTo(page.getRanking().get(i).getTotalXp());
        }
        page.getRanking().stream().filter(g -> !g.isMine())
                .forEach(g -> assertThat(g.getMembers()).as("다른 길드 멤버는 보이지 않는다").isEmpty());
        page.getMyGuilds().forEach(g -> {
            assertThat(g.getMembers()).hasSize(g.getMemberCount());
            assertThat(g.getMembers().stream().filter(m -> m.isMe()).count()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("업적 배지: 달성한 배지는 저장되고 획득 날짜가 생기며, 같은 배지는 두 번째 조회에서 NEW 없이 그대로 남는다(중복 저장 없음)")
    void achievementsAreGrantedOnce() {
        CharacterPageDTO first = gameService.getCharacterPage(LEARNER, "테스터");
        assertThat(first.getAchievements()).hasSize(15);
        assertThat(first.getAchievementEarned()).isEqualTo((int) first.getAchievements().stream().filter(AchievementDTO::isEarned).count());

        // 달성한 것은 모두 획득 처리되고 날짜가 있다. 못 받은 것은 진행도가 목표를 넘지 않는다
        for (AchievementDTO a : first.getAchievements()) {
            if (a.isEarned()) {
                assertThat(a.getEarnedAt()).as(a.getTitle() + " 획득 날짜").isNotNull();
                assertThat(a.getPercent()).isEqualTo(100);
            } else {
                assertThat(a.getCurrent()).isLessThan(a.getTarget());
                assertThat(a.isFresh()).isFalse();
            }
        }
        long saved = userAchievementRepository.findByUserId(LEARNER).size();
        assertThat(saved).isEqualTo(first.getAchievementEarned());

        CharacterPageDTO second = gameService.getCharacterPage(LEARNER, "테스터");
        assertThat(second.getAchievements()).noneMatch(AchievementDTO::isFresh);   // 이미 받은 배지는 NEW 가 아니다
        assertThat(second.getAchievementEarned()).isEqualTo(first.getAchievementEarned());
        assertThat(userAchievementRepository.findByUserId(LEARNER)).hasSize((int) saved);   // 중복 저장 없음
    }

    @Test
    @DisplayName("수료 배지: 기존 BadgeService 의 수료 배지가 화면용으로 함께 내려온다")
    void courseBadges() {
        CharacterPageDTO page = gameService.getCharacterPage(LEARNER, "테스터");
        // 시드: learner 는 색연필화 입문 수료 배지를 가졌다
        assertThat(page.getCourseBadges()).anyMatch(b -> b.getHobbyName().equals("색연필화") && b.getLevelLabel().equals("입문"));
    }

    @Test
    @DisplayName("대표 배지 장착: 받은 배지만 장착되고, 못 받은 배지·없는 코드는 거절하며, 같은 배지는 한 번만 장착된다")
    void equipBadge() {
        gameService.getCharacterPage(LEARNER, "테스터");   // 달성한 업적 배지를 먼저 저장한다(화면을 여는 것과 같다)

        assertThat(gameService.equipBadge(LEARNER, "LESSON_1")).isEqualTo(BadgeEquipResult.EQUIPPED);
        assertThat(gameService.equipBadge(LEARNER, "LESSON_1")).isEqualTo(BadgeEquipResult.ALREADY_EQUIPPED);
        assertThat(gameService.equipBadge(LEARNER, "LESSON_30")).isEqualTo(BadgeEquipResult.NOT_OWNED);   // 아직 못 받은 배지
        assertThat(gameService.equipBadge(LEARNER, "NO_SUCH_BADGE")).isEqualTo(BadgeEquipResult.NOT_OWNED);
        assertThat(gameService.equipBadge(LEARNER, "C999-M")).isEqualTo(BadgeEquipResult.NOT_OWNED);      // 없는 수료 배지
        assertThat(gameService.equipBadge(LEARNER, "")).isEqualTo(BadgeEquipResult.NOT_OWNED);

        CharacterPageDTO page = gameService.getCharacterPage(LEARNER, "테스터");
        assertThat(page.getEquippedBadges()).extracting("code").containsExactly("LESSON_1");
        assertThat(page.getAchievements()).filteredOn(AchievementDTO::isEquipped).extracting("code").containsExactly("LESSON_1");
    }

    @Test
    @DisplayName("대표 배지는 최대 3개: 4번째는 FULL, 해제하면 빈 칸에 다시 장착된다. 수료 배지도 장착할 수 있다")
    void equipBadgeLimitAndUnequip() {
        CharacterPageDTO before = gameService.getCharacterPage(LEARNER, "테스터");
        java.util.List<String> owned = new java.util.ArrayList<>(before.getAchievements().stream()
                .filter(AchievementDTO::isEarned).map(AchievementDTO::getCode).toList());
        before.getCourseBadges().forEach(b -> owned.add(b.getCode()));
        assertThat(owned.size()).as("시드 학습자는 업적 배지와 수료 배지를 합쳐 4개 이상 가졌다").isGreaterThanOrEqualTo(4);

        for (int i = 0; i < 3; i++) {
            assertThat(gameService.equipBadge(LEARNER, owned.get(i))).isEqualTo(BadgeEquipResult.EQUIPPED);
        }
        assertThat(gameService.equipBadge(LEARNER, owned.get(3))).isEqualTo(BadgeEquipResult.FULL);
        assertThat(gameService.getCharacterPage(LEARNER, "테스터").getEquippedBadges()).hasSize(3);

        gameService.unequipBadge(LEARNER, owned.get(1));
        gameService.unequipBadge(LEARNER, "NOT_EQUIPPED");   // 장착하지 않은 것을 해제해도 오류 없이 넘어간다
        assertThat(userEquippedBadgeRepository.findByUserIdOrderBySlotNo(LEARNER)).hasSize(2);

        assertThat(gameService.equipBadge(LEARNER, owned.get(3))).isEqualTo(BadgeEquipResult.EQUIPPED);   // 비워진 칸(2번)에 들어간다
        assertThat(userEquippedBadgeRepository.findByUserIdOrderBySlotNo(LEARNER)).extracting("slotNo").containsExactly(1, 2, 3);
        assertThat(gameService.getCharacterPage(LEARNER, "테스터").getEquippedBadges()).extracting("code")
                .containsExactly(owned.get(0), owned.get(3), owned.get(2));
    }
}
