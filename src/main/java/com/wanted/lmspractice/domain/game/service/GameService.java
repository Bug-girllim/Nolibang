package com.wanted.lmspractice.domain.game.service;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.game.dto.AchievementDTO;
import com.wanted.lmspractice.domain.game.dto.ActivityStatDTO;
import com.wanted.lmspractice.domain.game.dto.AvatarGroupDTO;
import com.wanted.lmspractice.domain.game.dto.AvatarOptionDTO;
import com.wanted.lmspractice.domain.game.dto.BossDTO;
import com.wanted.lmspractice.domain.game.dto.CharacterDTO;
import com.wanted.lmspractice.domain.game.dto.CharacterPageDTO;
import com.wanted.lmspractice.domain.game.dto.CourseBadgeDTO;
import com.wanted.lmspractice.domain.game.dto.EquippedBadgeDTO;
import com.wanted.lmspractice.domain.game.dto.GuildDTO;
import com.wanted.lmspractice.domain.game.dto.GuildMemberDTO;
import com.wanted.lmspractice.domain.game.dto.GuildPageDTO;
import com.wanted.lmspractice.domain.game.dto.HobbyClassDTO;
import com.wanted.lmspractice.domain.game.dto.PeriodStatDTO;
import com.wanted.lmspractice.domain.game.dto.QuestDTO;
import com.wanted.lmspractice.domain.game.dto.XpLineDTO;
import com.wanted.lmspractice.domain.game.entity.AvatarItem;
import com.wanted.lmspractice.domain.game.entity.QuestClaim;
import com.wanted.lmspractice.domain.game.entity.QuestClaimId;
import com.wanted.lmspractice.domain.game.entity.UserAchievement;
import com.wanted.lmspractice.domain.game.entity.UserAchievementId;
import com.wanted.lmspractice.domain.game.entity.UserEquippedBadge;
import com.wanted.lmspractice.domain.game.entity.UserAvatar;
import com.wanted.lmspractice.domain.game.policy.AchievementType;
import com.wanted.lmspractice.domain.game.policy.GameRules;
import com.wanted.lmspractice.domain.game.policy.QuestType;
import com.wanted.lmspractice.domain.game.repository.GameStatQuery;
import com.wanted.lmspractice.domain.game.repository.QuestClaimRepository;
import com.wanted.lmspractice.domain.game.repository.UserAchievementRepository;
import com.wanted.lmspractice.domain.game.repository.UserAvatarRepository;
import com.wanted.lmspractice.domain.game.repository.UserEquippedBadgeRepository;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.dto.MyCourseDTO;
import com.wanted.lmspractice.domain.learning.service.MyLearningService;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import com.wanted.lmspractice.domain.review.service.BadgeService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] RPG 성장 요소 전체 — 캐릭터(레벨·경험치), 취미별 직업·스킬 트리, 보스전, 퀘스트, 꾸미기, 배지(업적·수료), 길드 순위
 * [담당] RPG 성장 요소   [화면] game/character.html(내 캐릭터), game/guild.html(길드)
 * [연결]
 *  - 호출받음: GameController
 *  - 호출함:   GameStatQuery(활동 집계), QuestClaimRepository·UserAvatarRepository(같은 도메인),
 *             BadgeService.getBadges(계약 C12 — 수료 배지로 직업·해금 판단), HobbyService.getCategories(운영 중인 취미 목록),
 *             MyLearningService.getMyCourses(수강·과제 상태를 보스전으로 변환)
 * [규칙]
 *  - 경험치·레벨·직업은 저장하지 않고 매번 기록에서 계산한다 → 기존 서비스에 점수 지급 코드를 끼워 넣지 않아도 되고, 값이 어긋나지 않는다.
 *  - 저장하는 것은 퀘스트 보상 수령(quest_claims), 꾸미기 장착(user_avatars), 업적 배지 획득 기록(user_achievements) 셋뿐이다.
 *  - 길드(분반) 점수는 "그 분반에서 쌓은 기여 경험치"(차시·과제 제출·수료)의 합이다. 총 경험치를 쓰면 여러 강의를 듣는 사람이 모든 길드에 중복으로 들어간다.
 *  - 길드 멤버는 닉네임·레벨·기여 경험치만 보이고, 다른 길드의 멤버 목록은 숨긴다.
 *  - 본인 것만: 모든 메서드는 로그인한 사용자 id 로만 조회·저장한다.
 * [기술] 🟢 @Transactional, 🟢 JPA 변경 감지(꾸미기 저장), 🟡 Java Stream
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private static final int RANKING_LIMIT = 10;   // 길드 순위에 보여 줄 개수
    public static final int BADGE_SLOT_MAX = 3;    // 장착할 수 있는 대표 배지 수

    private final GameStatQuery gameStatQuery;
    private final QuestClaimRepository questClaimRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserEquippedBadgeRepository userEquippedBadgeRepository;
    private final BadgeService badgeService;
    private final HobbyService hobbyService;
    private final MyLearningService myLearningService;

    /** 퀘스트 보상 받기 결과 — 컨트롤러가 안내 문구로 바꾼다 */
    public enum ClaimResult { CLAIMED, NOT_ENOUGH, ALREADY_CLAIMED }

    /** 꾸미기 장착 결과 */
    public enum EquipResult { EQUIPPED, LOCKED }

    /** 대표 배지 장착 결과 */
    public enum BadgeEquipResult { EQUIPPED, ALREADY_EQUIPPED, FULL, NOT_OWNED }

    // ---------------------------------------------------------------
    // 내 캐릭터 화면
    // ---------------------------------------------------------------

    /**
     * 내 캐릭터 화면 전체 (캐릭터 카드·경험치 내역·퀘스트·직업·보스전·꾸미기·배지).
     * 화면을 열 때 새로 달성한 업적 배지를 저장하므로 쓰기 트랜잭션이다(읽기 전용 아님).
     * 호출: GameController.character
     */
    @Transactional
    public CharacterPageDTO getCharacterPage(Long userId, String nickname) {
        ActivityStatDTO stat = statOf(userId);
        long totalXp = GameRules.totalXp(stat);
        int level = GameRules.levelOf(totalXp);

        List<BadgeDTO> badges = badgeService.getBadges(userId);
        int masterCount = (int) badges.stream().filter(BadgeDTO::isMaster).count();
        boolean hasMaster = masterCount > 0;

        UserAvatar avatar = userAvatarRepository.findById(userId).orElse(null);
        List<AvatarGroupDTO> groups = buildAvatarGroups(avatar, level, hasMaster);

        CharacterDTO character = buildCharacter(nickname, level, totalXp, masterCount, avatar);
        List<String> equippedCodes = userEquippedBadgeRepository.findByUserIdOrderBySlotNo(userId).stream()
                .map(UserEquippedBadge::getBadgeCode).toList();
        List<AchievementDTO> achievements = syncAchievements(userId,
                new AchievementType.AchievementContext(stat, level, masterCount), Set.copyOf(equippedCodes));
        int achievementEarned = (int) achievements.stream().filter(AchievementDTO::isEarned).count();
        List<CourseBadgeDTO> courseBadges = buildCourseBadges(badges, Set.copyOf(equippedCodes));
        return new CharacterPageDTO(
                character,
                buildXpLines(stat),
                quests(userId, QuestType.Period.DAILY),
                quests(userId, QuestType.Period.WEEKLY),
                buildClasses(badges),
                buildBosses(userId),
                groups,
                achievements,
                achievementEarned,
                courseBadges,
                buildEquippedBadges(equippedCodes, achievements, courseBadges),
                BADGE_SLOT_MAX);
    }

    private CharacterDTO buildCharacter(String nickname, int level, long totalXp, int masterCount, UserAvatar avatar) {
        long start = GameRules.xpToReach(level);
        Long next = level >= GameRules.MAX_LEVEL ? null : GameRules.xpToReach(level + 1);
        int percent = next == null ? 100 : (int) ((totalXp - start) * 100 / (next - start));
        return new CharacterDTO(nickname, level, GameRules.titleOf(level), totalXp, start, next, percent,
                equippedItem(avatar, AvatarItem.Slot.AVATAR, level, masterCount > 0).getValue(),
                equippedItem(avatar, AvatarItem.Slot.FRAME, level, masterCount > 0).getValue(),
                equippedItem(avatar, AvatarItem.Slot.NAME_COLOR, level, masterCount > 0).getValue(),
                masterCount);
    }

    /** 장착한 아이템 — 기록이 없거나 지금은 잠긴 아이템(예: 마스터 배지를 잃음)이면 슬롯 기본 아이템으로 되돌려 보여 준다 */
    private AvatarItem equippedItem(UserAvatar avatar, AvatarItem.Slot slot, int level, boolean hasMaster) {
        String code = avatar == null ? null : avatar.codeOf(slot);
        return code == null ? AvatarItem.defaultOf(slot)
                : AvatarItem.find(slot, code).filter(item -> item.isUnlocked(level, hasMaster))
                        .orElse(AvatarItem.defaultOf(slot));
    }

    private List<XpLineDTO> buildXpLines(ActivityStatDTO stat) {
        List<XpLineDTO> lines = new ArrayList<>();
        lines.add(new XpLineDTO("차시 완료", stat.getLessons(), (long) stat.getLessons() * GameRules.XP_LESSON));
        lines.add(new XpLineDTO("최종 과제 제출", stat.getSubmits(), (long) stat.getSubmits() * GameRules.XP_SUBMIT));
        lines.add(new XpLineDTO("보스 격파(수료)", stat.getBossXp() == 0 ? 0 : 1, stat.getBossXp()));
        lines.add(new XpLineDTO("작품 공개", stat.getWorks(), (long) stat.getWorks() * GameRules.XP_WORK));
        lines.add(new XpLineDTO("댓글 (최대 " + GameRules.COMMENT_XP_CAP + "개)", stat.getComments(),
                (long) Math.min(stat.getComments(), GameRules.COMMENT_XP_CAP) * GameRules.XP_COMMENT));
        lines.add(new XpLineDTO("받은 좋아요 (최대 " + GameRules.LIKE_XP_CAP + "개)", stat.getLikes(),
                (long) Math.min(stat.getLikes(), GameRules.LIKE_XP_CAP) * GameRules.XP_LIKE));
        lines.add(new XpLineDTO("퀘스트 보상", stat.getQuestXp() == 0 ? 0 : 1, stat.getQuestXp()));
        return lines;
    }

    // ---------------------------------------------------------------
    // 배지 (업적 배지 + 수료 배지)
    // ---------------------------------------------------------------

    /**
     * 업적 배지 달성 판단·저장. 새로 달성한 배지는 user_achievements 에 남기고(알림은 보내지 않는다),
     * 이번 화면에서 처음 받은 것에는 fresh 표시를 붙여 NEW 로 보여 준다.
     * 달성 시각은 "조건을 처음 만족한 때"가 아니라 "화면에서 처음 확인한 때"다 — 기존 서비스에 배지 지급 코드를 끼우지 않으려는 선택.
     */
    private List<AchievementDTO> syncAchievements(Long userId, AchievementType.AchievementContext context,
                                                  Set<String> equippedCodes) {
        Map<String, LocalDateTime> earned = new LinkedHashMap<>();
        userAchievementRepository.findByUserId(userId).forEach(a -> earned.put(a.getBadgeCode(), a.getEarnedAt()));

        Set<String> fresh = new HashSet<>();
        for (AchievementType type : AchievementType.values()) {
            if (!earned.containsKey(type.name()) && type.isAchieved(context)
                    && userAchievementRepository.insertIgnore(userId, type.name()) == 1) {
                fresh.add(type.name());
            }
        }
        if (!fresh.isEmpty()) {   // 방금 저장한 행의 획득 시각(DB 기록값)을 다시 읽는다
            userAchievementRepository.findByUserId(userId).forEach(a -> earned.put(a.getBadgeCode(), a.getEarnedAt()));
        }

        List<AchievementDTO> result = new ArrayList<>();
        for (AchievementType type : AchievementType.values()) {
            boolean has = earned.containsKey(type.name());
            int current = Math.min(type.current(context), type.getTarget());
            result.add(new AchievementDTO(type.name(), type.getEmoji(), type.getTitle(), type.getDescription(),
                    type.getGroup(), has, earned.get(type.name()), fresh.contains(type.name()),
                    has ? type.getTarget() : current, type.getTarget(),
                    has ? 100 : current * 100 / type.getTarget(), has && equippedCodes.contains(type.name())));
        }
        return result;
    }

    /** 기존 수료 배지(BadgeService) → 화면용. 마스터 → 수준 순서는 BadgeService 가 정한 순서를 그대로 따른다 */
    private List<CourseBadgeDTO> buildCourseBadges(List<BadgeDTO> badges, Set<String> equippedCodes) {
        return badges.stream().map(b -> {
            String code = courseBadgeCode(b);
            String emoji = b.isMaster() ? "👑" : switch (b.getLevel()) {
                case BASIC -> "🥉";
                case INTERMEDIATE -> "🥈";
                case ADVANCED -> "🥇";
            };
            String label = b.isMaster() ? "마스터" : b.getLevel().getLabel();
            return new CourseBadgeDTO(code, emoji, b.getHobbyName(), label, b.isMaster(), equippedCodes.contains(code));
        }).toList();
    }

    /** 수료 배지 장착 코드 — C{취미 id}-{B(입문)|I(중급)|A(고급)|M(마스터)} */
    private String courseBadgeCode(BadgeDTO b) {
        String suffix = b.isMaster() ? "M" : switch (b.getLevel()) {
            case BASIC -> "B";
            case INTERMEDIATE -> "I";
            case ADVANCED -> "A";
        };
        return "C" + b.getHobbyId() + "-" + suffix;
    }

    /** 장착 코드 목록(칸 번호 순) → 카드에 보일 배지. 지금 가지고 있지 않은 코드는 건너뛴다 */
    private List<EquippedBadgeDTO> buildEquippedBadges(List<String> codes, List<AchievementDTO> achievements,
                                                       List<CourseBadgeDTO> courseBadges) {
        List<EquippedBadgeDTO> result = new ArrayList<>();
        for (String code : codes) {
            achievements.stream().filter(a -> a.isEarned() && a.getCode().equals(code)).findFirst()
                    .ifPresentOrElse(a -> result.add(new EquippedBadgeDTO(code, a.getEmoji(), a.getTitle())),
                            () -> courseBadges.stream().filter(c -> c.getCode().equals(code)).findFirst()
                                    .ifPresent(c -> result.add(new EquippedBadgeDTO(code, c.getEmoji(),
                                            c.getHobbyName() + " " + c.getLevelLabel()))));
        }
        return result;
    }

    /**
     * 대표 배지 장착 — 가진 배지만, 최대 BADGE_SLOT_MAX 개까지, 같은 배지는 한 번만. 빈 칸 중 가장 앞 번호에 넣는다.
     * 가진 배지인지는 서버가 다시 확인한다(요청을 직접 만들어 보내도 못 속인다).
     * 호출: GameController.equipBadge (POST /my/character/badges/equip)
     */
    @Transactional
    public BadgeEquipResult equipBadge(Long userId, String code) {
        if (!ownsBadge(userId, code)) {
            return BadgeEquipResult.NOT_OWNED;
        }
        List<UserEquippedBadge> current = userEquippedBadgeRepository.findByUserIdOrderBySlotNo(userId);
        if (current.stream().anyMatch(e -> e.getBadgeCode().equals(code))) {
            return BadgeEquipResult.ALREADY_EQUIPPED;
        }
        Set<Integer> used = current.stream().map(UserEquippedBadge::getSlotNo).collect(Collectors.toSet());
        for (int slot = 1; slot <= BADGE_SLOT_MAX; slot++) {
            if (!used.contains(slot)) {
                return userEquippedBadgeRepository.insertIgnore(userId, slot, code) == 1
                        ? BadgeEquipResult.EQUIPPED : BadgeEquipResult.ALREADY_EQUIPPED;
            }
        }
        return BadgeEquipResult.FULL;
    }

    /** 대표 배지 해제. 장착하지 않은 배지를 해제해도 오류 없이 넘어간다. 호출: GameController.unequipBadge */
    @Transactional
    public void unequipBadge(Long userId, String code) {
        userEquippedBadgeRepository.deleteByCode(userId, code);
    }

    /** 지금 가진 배지인가 — 업적 배지는 획득 기록으로, 수료 배지는 BadgeService 로 확인한다 */
    private boolean ownsBadge(Long userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (code.startsWith("C")) {
            return badgeService.getBadges(userId).stream().anyMatch(b -> courseBadgeCode(b).equals(code));
        }
        try {
            AchievementType.valueOf(code);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return userAchievementRepository.existsById(new UserAchievementId(userId, code));
    }

    // ---------------------------------------------------------------
    // 취미별 직업·스킬 트리 (기존 수료 배지에서 계산)
    // ---------------------------------------------------------------

    private List<HobbyClassDTO> buildClasses(List<BadgeDTO> badges) {
        Map<Long, Set<CourseLevel>> cleared = new LinkedHashMap<>();
        Set<Long> masters = new TreeSet<>();
        for (BadgeDTO badge : badges) {
            if (badge.isMaster()) {
                masters.add(badge.getHobbyId());
            } else {
                cleared.computeIfAbsent(badge.getHobbyId(), id -> EnumSet.noneOf(CourseLevel.class)).add(badge.getLevel());
            }
        }
        List<HobbyClassDTO> classes = new ArrayList<>();
        for (CategoryDTO category : hobbyService.getCategories(Role.USER)) {
            for (HobbyDTO hobby : category.getHobbies()) {
                Set<CourseLevel> levels = cleared.getOrDefault(hobby.getId(), EnumSet.noneOf(CourseLevel.class));
                boolean master = masters.contains(hobby.getId());
                int highest = levels.stream().mapToInt(CourseLevel::getCode).max().orElse(0);
                List<HobbyClassDTO.Step> steps = new ArrayList<>();
                for (CourseLevel level : CourseLevel.values()) {
                    steps.add(new HobbyClassDTO.Step(level.getLabel(), levels.contains(level)));
                }
                classes.add(new HobbyClassDTO(hobby.getId(), hobby.getName(),
                        GameRules.classNameOf(hobby.getName(), highest, master), steps, master, levels.size()));
            }
        }
        // 많이 수련한 취미가 위로 — 같으면 취미 이름순
        classes.sort(Comparator.comparingInt(HobbyClassDTO::getClearedCount).reversed()
                .thenComparing(HobbyClassDTO::getHobbyName));
        return classes;
    }

    // ---------------------------------------------------------------
    // 보스전 (수강 1건 = 보스 1마리)
    // ---------------------------------------------------------------

    private List<BossDTO> buildBosses(Long userId) {
        List<BossDTO> bosses = new ArrayList<>();
        for (MyCourseDTO card : myLearningService.getMyCourses(userId, LearningFilter.ALL)) {
            BossDTO boss = toBoss(card);
            if (boss != null) {
                bosses.add(boss);
            }
        }
        return bosses;
    }

    /** 수강 카드 → 보스 카드. 대기·취소는 아직 보스가 나타나지 않았으므로 null */
    private BossDTO toBoss(MyCourseDTO card) {
        List<LessonProgressDTO> lessons = card.getLessons() == null ? List.of() : card.getLessons();
        int done = (int) lessons.stream().filter(LessonProgressDTO::isCompleted).count();
        String message;
        int hp;
        switch (card.getState()) {
            case LEARNING -> {
                hp = 100;
                message = card.isAllLessonsCompleted()
                        ? "보스의 방이 열렸어요! 최종 과제로 도전하세요."
                        : "수련 중 — 차시를 모두 마치면 보스의 방이 열려요. (" + done + "/" + lessons.size() + ")";
            }
            case REVIEW_WAITING -> {
                hp = 60;
                message = "공격을 마쳤어요! 강사님의 판정을 기다리는 중이에요.";
            }
            case IN_REVIEW -> {
                hp = 40;
                message = "격전 중! 강사님이 과제를 확인하고 있어요.";
            }
            case REJECTED -> {
                hp = 70;
                message = "일격을 맞았어요… 피드백을 읽고 기간 안에 재도전하세요.";
            }
            case COMPLETED -> {
                hp = 0;
                message = "보스 격파! 수료 경험치와 배지를 얻었어요.";
            }
            case EXPIRED -> {
                hp = 100;
                message = "시간 초과 — 이번 도전은 끝났어요.";
            }
            default -> {
                return null;
            }
        }
        Long roundId = card.getReview() == null ? null : card.getReview().getReviewRoundId();
        return new BossDTO(card.getEnrollmentId(), roundId, card.getCourseTitle(),
                card.getLevel() == null ? "" : card.getLevel().getLabel(),
                card.getState().name(), card.getState().getLabel(), message, hp, done, lessons.size());
    }

    // ---------------------------------------------------------------
    // 퀘스트
    // ---------------------------------------------------------------

    private List<QuestDTO> quests(Long userId, QuestType.Period period) {
        LocalDate today = LocalDate.now();
        PeriodStatDTO stat = gameStatQuery.findSince(userId, period.startOf(today));
        Set<String> claimedKeys = questClaimRepository
                .findByUserIdAndPeriodKeyIn(userId, List.of(period.keyOf(today))).stream()
                .map(QuestClaim::getQuestKey).collect(Collectors.toSet());
        List<QuestDTO> result = new ArrayList<>();
        for (QuestType quest : QuestType.values()) {
            if (quest.getPeriod() != period) {
                continue;
            }
            int raw = quest.getMetric().pick(stat);
            int progress = Math.min(raw, quest.getTarget());
            boolean claimed = claimedKeys.contains(quest.name());
            result.add(new QuestDTO(quest.name(), quest.getTitle(), quest.getDescription(), period.getLabel(),
                    quest.getRewardXp(), progress, quest.getTarget(), claimed,
                    !claimed && raw >= quest.getTarget(), progress * 100 / quest.getTarget()));
        }
        return result;
    }

    /**
     * 퀘스트 보상 받기. 달성했고 이번 기간에 아직 안 받았을 때만 경험치 보상(기록 1건)을 남긴다.
     * 호출: GameController.claimQuest (POST /my/character/quests/claim)
     *
     * @throws BusinessException INVALID_INPUT — 없는 퀘스트 키
     */
    @Transactional
    public ClaimResult claimQuest(Long userId, String questKey) {
        QuestType quest = parseQuest(questKey);
        LocalDate today = LocalDate.now();
        String periodKey = quest.getPeriod().keyOf(today);
        if (questClaimRepository.existsById(new QuestClaimId(
                userId, quest.name(), periodKey))) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        PeriodStatDTO stat = gameStatQuery.findSince(userId, quest.getPeriod().startOf(today));
        if (quest.getMetric().pick(stat) < quest.getTarget()) {
            return ClaimResult.NOT_ENOUGH;
        }
        questClaimRepository.save(QuestClaim.create(userId, quest.name(), periodKey, quest.getRewardXp()));
        return ClaimResult.CLAIMED;
    }

    private QuestType parseQuest(String key) {
        try {
            return QuestType.valueOf(key);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // ---------------------------------------------------------------
    // 꾸미기 (인벤토리 = 해금 조건을 만족한 아이템)
    // ---------------------------------------------------------------

    private List<AvatarGroupDTO> buildAvatarGroups(UserAvatar avatar, int level, boolean hasMaster) {
        List<AvatarGroupDTO> groups = new ArrayList<>();
        for (AvatarItem.Slot slot : AvatarItem.Slot.values()) {
            AvatarItem equipped = equippedItem(avatar, slot, level, hasMaster);
            List<AvatarOptionDTO> options = AvatarItem.of(slot).stream()
                    .map(item -> new AvatarOptionDTO(slot.name(), item.getCode(), item.getLabel(), item.getValue(),
                            item.isUnlocked(level, hasMaster), item == equipped, item.unlockHint()))
                    .toList();
            groups.add(new AvatarGroupDTO(slot.name(), slot.getLabel(), options));
        }
        return groups;
    }

    /**
     * 아이템 장착. 해금되지 않은 아이템은 장착하지 않는다(요청을 직접 만들어 보내도 서버가 다시 확인한다).
     * 호출: GameController.equip (POST /my/character/equip)
     *
     * @throws BusinessException INVALID_INPUT — 없는 슬롯·아이템
     */
    @Transactional
    public EquipResult equip(Long userId, String slotName, String code) {
        AvatarItem.Slot slot;
        try {
            slot = AvatarItem.Slot.valueOf(slotName);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        AvatarItem item = AvatarItem.find(slot, code).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        int level = GameRules.levelOf(GameRules.totalXp(statOf(userId)));
        boolean hasMaster = badgeService.getBadges(userId).stream().anyMatch(BadgeDTO::isMaster);
        if (!item.isUnlocked(level, hasMaster)) {
            return EquipResult.LOCKED;
        }
        UserAvatar avatar = userAvatarRepository.findById(userId).orElseGet(() -> UserAvatar.create(userId));
        avatar.equip(slot, item.getCode());
        userAvatarRepository.save(avatar);
        return EquipResult.EQUIPPED;
    }

    // ---------------------------------------------------------------
    // 길드(분반) 순위
    // ---------------------------------------------------------------

    /**
     * 길드 화면 — 내가 속한 길드(멤버 목록 포함)와 전체 길드 순위(상위 N + 내 길드).
     * 순위는 멤버 경험치 합계이고, 다른 길드의 멤버는 보이지 않는다.
     * 호출: GameController.guild
     */
    @Transactional(readOnly = true)
    public GuildPageDTO getGuildPage(Long userId) {
        List<Object[]> rows = gameStatQuery.findGuildMembers();

        // 길드별 멤버 모으기 (분반 id → 멤버 행)
        Map<Long, List<Object[]>> bySection = new LinkedHashMap<>();
        for (Object[] row : rows) {
            bySection.computeIfAbsent(((Number) row[0]).longValue(), id -> new ArrayList<>()).add(row);
        }
        Set<Long> memberIds = rows.stream().map(r -> ((Number) r[5]).longValue()).collect(Collectors.toSet());
        Map<Long, ActivityStatDTO> stats = gameStatQuery.findActivity(memberIds);

        List<GuildDTO> guilds = new ArrayList<>();
        bySection.forEach((sectionId, members) -> guilds.add(toGuild(userId, members, stats)));
        guilds.sort(Comparator.comparingLong(GuildDTO::getTotalXp).reversed().thenComparing(GuildDTO::getName));

        // 순위 번호 매기기 (동점이면 같은 순위)
        List<GuildDTO> ranked = new ArrayList<>();
        for (int i = 0; i < guilds.size(); i++) {
            GuildDTO g = guilds.get(i);
            int rank = (i > 0 && guilds.get(i - 1).getTotalXp() == g.getTotalXp()) ? ranked.get(i - 1).getRank() : i + 1;
            ranked.add(new GuildDTO(rank, g.getName(), g.getInstructorName(), g.getMemberCount(), g.getTotalXp(),
                    g.isMine(), g.getMembers()));
        }
        List<GuildDTO> mine = ranked.stream().filter(GuildDTO::isMine).toList();
        List<GuildDTO> top = ranked.stream().limit(RANKING_LIMIT).toList();
        return new GuildPageDTO(mine, top);
    }

    private GuildDTO toGuild(Long userId, List<Object[]> members, Map<Long, ActivityStatDTO> stats) {
        Object[] first = members.get(0);
        String name = first[3] + " · " + first[2] + " " + first[1] + "분반";
        String instructor = first[4] == null ? "담당 강사 미정" : String.valueOf(first[4]);
        boolean mine = members.stream().anyMatch(r -> ((Number) r[5]).longValue() == userId);

        List<GuildMemberDTO> list = new ArrayList<>();
        long total = 0;
        List<Object[]> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingLong((Object[] r) -> ((Number) r[7]).longValue()).reversed());   // 길드 기여 경험치 순
        int rank = 0;
        for (Object[] r : sorted) {
            long memberId = ((Number) r[5]).longValue();
            long xp = ((Number) r[7]).longValue();   // 이 길드(수강 건)에서 쌓은 기여 경험치
            total += xp;
            rank++;
            if (mine) {   // 다른 길드의 멤버 목록은 만들지 않는다
                String nick = r[6] == null ? "이름 없음" : String.valueOf(r[6]);
                list.add(new GuildMemberDTO(rank, nick, GameRules.levelOf(xpOf(stats, memberId)), xp, memberId == userId));   // 레벨은 전체 경험치 기준
            }
        }
        return new GuildDTO(0, name, instructor, members.size(), total, mine, list);
    }

    private long xpOf(Map<Long, ActivityStatDTO> stats, Long userId) {
        ActivityStatDTO stat = stats.get(userId);
        return stat == null ? 0L : GameRules.totalXp(stat);
    }

    private ActivityStatDTO statOf(Long userId) {
        return Optional.ofNullable(gameStatQuery.findActivity(List.of(userId)).get(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
