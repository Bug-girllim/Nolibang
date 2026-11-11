package com.wanted.lmspractice.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.game.dto.CharacterPageDTO;
import com.wanted.lmspractice.domain.game.dto.AuthorStyleDTO;
import com.wanted.lmspractice.domain.game.dto.EquippedBadgeDTO;
import com.wanted.lmspractice.domain.game.repository.UserEquippedBadgeRepository;
import com.wanted.lmspractice.domain.game.service.AuthorStyleService;
import com.wanted.lmspractice.domain.game.service.GameService;
import com.wanted.lmspractice.domain.game.service.GameService.BadgeEquipResult;
import com.wanted.lmspractice.domain.game.service.GameService.EquipResult;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.CommentDTO;
import com.wanted.lmspractice.domain.work.entity.WorkComment;
import com.wanted.lmspractice.domain.work.repository.WorkCommentRepository;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDetailDTO;
import com.wanted.lmspractice.domain.work.dto.WorkPageDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.service.WorkBoardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 작성자 꾸밈 테스트(대표 배지·닉네임 색·닉네임 테두리) — 여러 사용자 한 번에 조회, 작품 게시판·상세·댓글에 붙는지, 관리자 화면에는 안 붙는지
 * [전제] 로컬 MySQL(nolibang)에 schema.sql(user_equipped_badges 포함)·seed.sql이 적재돼 있어야 한다.
 *       시드 사용자: 2 learner(학습자, 색연필화 입문 수료). @Transactional이라 테스트가 끝나면 바꾼 데이터는 되돌린다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class AuthorStyleTest {

    private static final Long LEARNER = 2L;
    private static final Long OTHER = 3L;

    @Autowired
    private AuthorStyleService authorStyleService;

    @Autowired
    private GameService gameService;

    @Autowired
    private WorkBoardService workBoardService;

    @Autowired
    private WorkCommentRepository workCommentRepository;

    @Autowired
    private UserEquippedBadgeRepository userEquippedBadgeRepository;

    /** 화면을 열어 달성한 업적 배지를 저장한 뒤 그 가운데 앞의 count 개를 장착하고, 장착한 코드를 돌려준다 */
    private List<String> equipFirst(Long userId, int count) {
        CharacterPageDTO page = gameService.getCharacterPage(userId, "테스터");
        List<String> codes = new ArrayList<>(page.getAchievements().stream()
                .filter(a -> a.isEarned()).map(a -> a.getCode()).toList());
        page.getCourseBadges().forEach(b -> codes.add(b.getCode()));
        List<String> equipped = new ArrayList<>();
        for (String code : codes) {
            if (equipped.size() == count) {
                break;
            }
            assertThat(gameService.equipBadge(userId, code)).isEqualTo(BadgeEquipResult.EQUIPPED);
            equipped.add(code);
        }
        assertThat(equipped).as("시드 학습자가 장착할 배지를 충분히 가졌다").hasSize(count);
        return equipped;
    }

    /**
     * 앱을 직접 써서 DB에 남은 대표 배지 장착 기록이 테스트 결과를 바꾸지 않도록, 각 테스트를 빈 상태에서 시작한다.
     * 클래스가 @Transactional 이라 이 삭제도 테스트가 끝나면 되돌려진다(실제 데이터는 그대로).
     */
    @BeforeEach
    void clearEquippedBadges() {
        userEquippedBadgeRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("여러 사용자의 대표 배지를 한 번에 읽는다: 장착한 사람만 결과에 있고, 칸 번호 순이며, 이모지·이름이 채워진다")
    void getEquippedBadges() {
        List<String> equipped = equipFirst(LEARNER, 3);

        Map<Long, List<EquippedBadgeDTO>> result = authorStyleService.getEquippedBadges(Set.of(LEARNER, OTHER, 999999L));

        assertThat(result).containsOnlyKeys(LEARNER);   // 장착하지 않은 사람·없는 사람은 키가 없다
        List<EquippedBadgeDTO> badges = result.get(LEARNER);
        assertThat(badges).extracting(EquippedBadgeDTO::getCode).containsExactlyElementsOf(equipped);
        assertThat(badges).allSatisfy(b -> {
            assertThat(b.getEmoji()).isNotBlank();
            assertThat(b.getTitle()).isNotBlank();
        });

        assertThat(authorStyleService.getEquippedBadges(Set.of())).isEmpty();
        assertThat(authorStyleService.getEquippedBadges(null)).isEmpty();
    }

    @Test
    @DisplayName("수료 배지는 취미 이름이 붙은 이름으로 나온다 (예: 색연필화 입문)")
    void courseBadgeTitle() {
        CharacterPageDTO page = gameService.getCharacterPage(LEARNER, "테스터");
        String code = page.getCourseBadges().stream().filter(b -> b.getHobbyName().equals("색연필화") && !b.isMaster())
                .findFirst().orElseThrow().getCode();
        assertThat(gameService.equipBadge(LEARNER, code)).isEqualTo(BadgeEquipResult.EQUIPPED);

        EquippedBadgeDTO badge = authorStyleService.getEquippedBadges(Set.of(LEARNER)).get(LEARNER).get(0);
        assertThat(badge.getEmoji()).isEqualTo("🥉");
        assertThat(badge.getTitle()).isEqualTo("색연필화 입문");
    }

    @Test
    @DisplayName("작품 게시판: 장착한 작성자의 카드에만 대표 배지가 붙고, 장착하지 않은 작성자는 빈 목록이다")
    void boardShowsAuthorBadges() {
        WorkPageDTO before = workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER);
        assertThat(before.getItems()).isNotEmpty();
        assertThat(before.getItems()).allSatisfy(w -> assertThat(w.getAuthorId()).as("작성자 id").isNotNull());
        assertThat(before.getItems()).allSatisfy(w -> assertThat(w.getAuthorStyle().getBadges()).isEmpty());

        // 게시판 첫 카드 작성자에게 배지를 장착시킨다
        Long authorId = before.getItems().get(0).getAuthorId();
        List<String> equipped = equipFirst(authorId, 1);

        WorkPageDTO after = workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER);
        for (WorkDTO w : after.getItems()) {
            if (w.getAuthorId().equals(authorId)) {
                assertThat(w.getAuthorStyle().getBadges()).extracting(EquippedBadgeDTO::getCode).containsExactlyElementsOf(equipped);
            } else {
                assertThat(w.getAuthorStyle().getBadges()).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("작품 상세: 내가 쓴 댓글·답글에만 내 대표 배지가 붙고, 남의 댓글에는 붙지 않는다")
    void detailShowsCommentBadges() {
        WorkComment mine = workCommentRepository.findAll().stream()
                .filter(c -> c.getAuthorId().equals(LEARNER) && !c.isDeleted()).findFirst().orElse(null);
        if (mine == null) {
            return;   // 시드에 학습자가 쓴 댓글이 없는 상태
        }
        List<String> equipped = equipFirst(LEARNER, 1);

        WorkDetailDTO detail = workBoardService.getDetail(mine.getWorkId(), LEARNER, Role.USER);
        List<CommentDTO> all = new ArrayList<>();
        detail.getComments().forEach(c -> {
            all.add(c);
            all.addAll(c.getReplies());
        });
        assertThat(all).anyMatch(CommentDTO::isMine);
        for (CommentDTO c : all) {
            if (c.isMine()) {
                assertThat(c.getAuthorStyle().getBadges()).extracting(EquippedBadgeDTO::getCode).containsExactlyElementsOf(equipped);
            } else {
                assertThat(c.getAuthorStyle().getBadges()).as("남의 댓글에는 배지가 붙지 않는다").isEmpty();
            }
        }
    }

    @Test
    @DisplayName("닉네임 색·테두리: 직접 장착한 사람만 작품 화면용 클래스가 붙고, 장착한 적 없으면(기본값) 아무것도 붙지 않는다")
    void nameColorAndFrame() {
        // 아무것도 장착하지 않은 상태 — 꾸밈 없음(키가 없다)
        assertThat(authorStyleService.getAuthorStyles(Set.of(OTHER))).doesNotContainKey(OTHER);

        assertThat(gameService.equip(LEARNER, "NAME_COLOR", "green")).isEqualTo(EquipResult.EQUIPPED);
        assertThat(gameService.equip(LEARNER, "FRAME", "bronze")).isEqualTo(EquipResult.EQUIPPED);

        Map<Long, AuthorStyleDTO> styles = authorStyleService.getAuthorStyles(Set.of(LEARNER, OTHER));
        assertThat(styles).containsOnlyKeys(LEARNER);
        assertThat(styles.get(LEARNER).getNameColorClass()).isEqualTo("an-nc-green");
        assertThat(styles.get(LEARNER).getFrameClass()).isEqualTo("an-frame-bronze");
        assertThat(styles.get(LEARNER).getBadges()).isEmpty();   // 배지는 장착하지 않았다

        // 색만 장착한 사람은 테두리가 없다
        gameService.equip(OTHER, "NAME_COLOR", "ink");
        AuthorStyleDTO other = authorStyleService.getAuthorStyles(Set.of(OTHER)).get(OTHER);
        assertThat(other.getNameColorClass()).isEqualTo("an-nc-ink");
        assertThat(other.getFrameClass()).isNull();
    }

    @Test
    @DisplayName("작품 게시판 카드에 작성자의 닉네임 색·테두리·배지가 함께 붙는다")
    void boardShowsFullStyle() {
        WorkPageDTO before = workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER);
        Long authorId = before.getItems().get(0).getAuthorId();
        gameService.equip(authorId, "NAME_COLOR", "ink");
        gameService.equip(authorId, "FRAME", "wood");
        List<String> equipped = equipFirst(authorId, 1);

        WorkPageDTO after = workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER);
        for (WorkDTO w : after.getItems()) {
            if (w.getAuthorId().equals(authorId)) {
                assertThat(w.getAuthorStyle().getNameColorClass()).isEqualTo("an-nc-ink");
                assertThat(w.getAuthorStyle().getFrameClass()).isEqualTo("an-frame-wood");
                assertThat(w.getAuthorStyle().getBadges()).extracting(EquippedBadgeDTO::getCode).containsExactlyElementsOf(equipped);
            } else {
                assertThat(w.getAuthorStyle().getFrameClass()).isNull();
            }
        }
    }

    @Test
    @DisplayName("관리자 화면에는 꾸밈을 입히지 않는다: 관리자 게시판 목록과 관리자가 보는 작품 상세")
    void adminSeesNoStyle() {
        WorkPageDTO board = workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER);
        WorkDTO first = board.getItems().get(0);
        Long authorId = first.getAuthorId();
        gameService.equip(authorId, "NAME_COLOR", "ink");
        gameService.equip(authorId, "FRAME", "wood");
        equipFirst(authorId, 1);

        // 일반 게시판에서는 보이지만
        assertThat(workBoardService.search(new WorkSearchDTO(), LEARNER, Role.USER).getItems())
                .filteredOn(w -> w.getAuthorId().equals(authorId)).allSatisfy(w -> assertThat(w.getAuthorStyle().getFrameClass()).isNotNull());
        // 관리자 목록·관리자가 보는 상세에서는 비어 있다
        assertThat(workBoardService.searchForAdmin(new WorkSearchDTO(), 1L).getItems())
                .allSatisfy(w -> {
                    assertThat(w.getAuthorStyle().getFrameClass()).isNull();
                    assertThat(w.getAuthorStyle().getBadges()).isEmpty();
                });
        WorkDetailDTO adminDetail = workBoardService.getDetail(first.getWorkId(), 1L, Role.ADMIN);
        assertThat(adminDetail.getWork().getAuthorStyle().getFrameClass()).isNull();
        assertThat(adminDetail.getWork().getAuthorStyle().getBadges()).isEmpty();
    }
}
