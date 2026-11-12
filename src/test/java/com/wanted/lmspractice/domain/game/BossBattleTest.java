package com.wanted.lmspractice.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.game.dto.BossDTO;
import com.wanted.lmspractice.domain.game.dto.CharacterPageDTO;
import com.wanted.lmspractice.domain.game.policy.BossType;
import com.wanted.lmspractice.domain.game.service.GameService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 보스 대치 화면 데이터 검증 — 취미별 보스, 단계(phase)별 체력·버튼, 격파 보상
 * [검증] 시드 학습자 learnerB(id 3): 수강 3건 — 색연필화 기초 학습 중(수련 중) · 디지털 드로잉 기초 평가 대기(판정 대기) ·
 *        디저트 만들기 중급 수료(격파, 수료 배지 중급)
 */
@SpringBootTest
@Transactional
class BossBattleTest {

    private static final Long LEARNER = 3L;

    @Autowired
    private GameService gameService;

    private CharacterPageDTO page() {
        return gameService.getCharacterPage(LEARNER, "차분한수달");
    }

    @Test
    @DisplayName("취미 이름으로 보스 종류가 정해지고, 모르는 취미는 기본 보스가 된다")
    void bossTypeByHobby() {
        assertThat(BossType.of("색연필화").getBossName()).isEqualTo("색을 훔친 먹구름");
        assertThat(BossType.of("디지털 드로잉").getBossName()).isEqualTo("오류 난 픽셀 골렘");
        assertThat(BossType.of("디저트 만들기").getBossName()).isEqualTo("폭주한 반죽 슬라임");
        assertThat(BossType.of("처음 보는 취미")).isEqualTo(BossType.GENERIC);
    }

    @Test
    @DisplayName("보스마다 이름·도구·필살기와 크기(수준)가 들어 있고, 대치 화면에는 가장 급한 보스(반려 > 판정 대기 > 보스의 방 > 수련 중)가 나온다")
    void featuredBossIsMostUrgent() {
        CharacterPageDTO page = page();
        assertThat(page.getBosses()).isNotEmpty().allSatisfy(b -> {
            assertThat(b.getBossName()).isNotBlank();
            assertThat(b.getToolEmoji()).isNotBlank();
            assertThat(b.getUltimateName()).isNotBlank();
            assertThat(b.getLevelNo()).isBetween(1, 3);
        });
        int top = page.getBosses().stream().mapToInt(BossDTO::getPriority).max().orElseThrow();
        assertThat(page.getFeaturedBoss().getPriority()).isEqualTo(top);
    }

    @Test
    @DisplayName("체력은 학습 진행도 연출 값 — 판정 대기 40, 반려 55, 수료 0, 수련 중은 완료 차시만큼 줄어든다")
    void hpFollowsProgress() {
        for (BossDTO b : page().getBosses()) {
            switch (b.getPhase()) {
                case "WAITING" -> assertThat(b.getHpPercent()).isEqualTo(40);
                case "REJECTED" -> assertThat(b.getHpPercent()).isEqualTo(55);
                case "DEFEATED" -> assertThat(b.getHpPercent()).isZero();
                case "LEARNING", "ROOM_OPEN" -> assertThat(b.getHpPercent())
                        .isEqualTo(100 - Math.round(b.getCompletedLessons() * 60f / b.getTotalLessons()));
                default -> { }
            }
        }
    }

    @Test
    @DisplayName("단계별 버튼: 판정 대기는 비활성 + 제출 과제 확인, 반려는 피드백 확인, 수련 중은 이어서 수련하기")
    void actionsByPhase() {
        for (BossDTO b : page().getBosses()) {
            switch (b.getPhase()) {
                case "WAITING" -> {
                    assertThat(b.isActionDisabled()).isTrue();
                    assertThat(b.getSubUrl()).startsWith("/assignments?enrollmentId=");
                }
                case "REJECTED" -> assertThat(b.getActionUrl()).isNotNull();
                case "ROOM_OPEN" -> assertThat(b.getActionLabel()).isEqualTo("최종 과제로 도전");
                case "LEARNING" -> assertThat(b.getActionLabel()).isEqualTo("이어서 수련하기");
                default -> { }
            }
        }
    }

    @Test
    @DisplayName("격파한 보스에는 보상(경험치 100+100×수준·수료 배지·직업 단계)이 들어 있다")
    void defeatedBossHasReward() {
        List<BossDTO> defeated = page().getBosses().stream().filter(BossDTO::isDefeated).toList();
        assertThat(defeated).isNotEmpty().allSatisfy(b -> {
            assertThat(b.getRewardXp()).isEqualTo(100L + 100L * b.getLevelNo());
            assertThat(b.getRewardBadge()).contains("수료 배지").contains(b.getLevelLabel());
            assertThat(b.getRewardClassName()).isNotBlank();
            assertThat(b.getRewardClassStage()).isIn("견습", "숙련", "전문가", "마스터");
        });
    }
}
