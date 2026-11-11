package com.wanted.lmspractice.domain.game.service;

import com.wanted.lmspractice.domain.game.dto.AuthorStyleDTO;
import com.wanted.lmspractice.domain.game.dto.EquippedBadgeDTO;
import com.wanted.lmspractice.domain.game.entity.AvatarItem;
import com.wanted.lmspractice.domain.game.entity.UserAvatar;
import com.wanted.lmspractice.domain.game.entity.UserEquippedBadge;
import com.wanted.lmspractice.domain.game.policy.AchievementType;
import com.wanted.lmspractice.domain.game.repository.UserAvatarRepository;
import com.wanted.lmspractice.domain.game.repository.UserEquippedBadgeRepository;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 작품 게시판·작품 상세·댓글에서 작성자에게 입혀 줄 꾸밈(대표 배지 이모지 + 닉네임 색 + 닉네임 테두리)을 여러 사용자 몫으로 한 번에 읽는다
 * [담당] RPG 성장 요소   [화면] work/board.html, work/board-instructor.html, work/detail.html (작성자 이름 — 관리자 화면은 쓰지 않는다)
 * [연결]
 *  - 호출받음: WorkBoardService(게시판 목록·상세·댓글 작성자)
 *  - 호출함:   UserEquippedBadgeRepository(장착 배지 — 사용자 여러 명을 쿼리 1번), UserAvatarRepository(닉네임 색·테두리 — 쿼리 1번),
 *             HobbyService.getHobbiesIgnoringVisibility(수료 배지 이름의 취미 이름)
 * [규칙]
 *  - 읽기 전용이다. 가진 배지·해금된 아이템인지는 장착할 때 이미 확인했고 사라지지 않으므로, 여기서는 다시 확인하지 않는다.
 *  - 닉네임 색·테두리는 사용자가 직접 장착한 경우에만 입힌다. 장착한 적 없으면(기본값) 평범한 이름 — 모든 이름에 기본 테두리가 둘러지지 않게.
 *  - 꾸밈이 하나도 없는 사용자는 결과 Map 에 키가 없다. 알 수 없는 코드는 건너뛴다.
 *  - GameService 와 따로 둔 이유: GameService 는 학습·평가 서비스까지 의존하는 무거운 서비스라서, work 도메인이 쓰기에는
 *    가벼운 이 서비스가 낫다(서비스 간 순환 의존도 피한다).
 * [기술] 🟢 @Transactional(readOnly), 🟢 Spring Data 쿼리 메서드, 🟡 Java Stream
 */
@Service
@RequiredArgsConstructor
public class AuthorStyleService {

    private static final String CSS_PREFIX = "an-";   // 작품 화면용 클래스(static/css/author-badge.css) — 캐릭터 화면용 클래스와 섞이지 않게

    private final UserEquippedBadgeRepository userEquippedBadgeRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final HobbyService hobbyService;

    /**
     * 사용자 여러 명의 꾸밈 — 배지는 칸 번호 순.
     * 호출: WorkBoardService.toPage, getDetail
     *
     * @return 사용자 id → 꾸밈. 꾸밈이 하나도 없는 사용자는 들어 있지 않다.
     */
    @Transactional(readOnly = true)
    public Map<Long, AuthorStyleDTO> getAuthorStyles(Collection<Long> userIds) {
        Map<Long, AuthorStyleDTO> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = new HashSet<>(userIds);
        Map<Long, List<EquippedBadgeDTO>> badges = getEquippedBadges(ids);
        Map<Long, UserAvatar> avatars = new HashMap<>();
        userAvatarRepository.findAllById(ids).forEach(a -> avatars.put(a.getUserId(), a));

        Set<Long> styled = new HashSet<>(badges.keySet());
        styled.addAll(avatars.keySet());
        for (Long userId : styled) {
            UserAvatar avatar = avatars.get(userId);
            String color = avatar == null ? null : cssOf(AvatarItem.Slot.NAME_COLOR, avatar.getNameColorCode());
            String frame = avatar == null ? null : cssOf(AvatarItem.Slot.FRAME, avatar.getFrameCode());
            List<EquippedBadgeDTO> list = badges.getOrDefault(userId, List.of());
            if (!list.isEmpty() || color != null || frame != null) {
                result.put(userId, new AuthorStyleDTO(list, color, frame));
            }
        }
        return result;
    }

    /** 장착 코드 → 작품 화면용 CSS 클래스. 장착하지 않았거나 모르는 코드면 null */
    private String cssOf(AvatarItem.Slot slot, String code) {
        return code == null ? null : AvatarItem.find(slot, code).map(item -> CSS_PREFIX + item.getValue()).orElse(null);
    }

    /**
     * 사용자 여러 명의 대표 배지 — 칸 번호 순.
     *
     * @return 사용자 id → 배지 목록. 배지가 없는 사용자는 들어 있지 않다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<EquippedBadgeDTO>> getEquippedBadges(Collection<Long> userIds) {
        Map<Long, List<EquippedBadgeDTO>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<UserEquippedBadge> rows = userEquippedBadgeRepository.findByUserIdInOrderByUserIdAscSlotNoAsc(new HashSet<>(userIds));
        if (rows.isEmpty()) {
            return result;
        }

        // 수료 배지 이름에 쓸 취미 이름을 한 번에 가져온다 (코드 C{취미 id}-{B|I|A|M})
        Set<Long> hobbyIds = new HashSet<>();
        rows.forEach(r -> {
            Long hobbyId = courseHobbyId(r.getBadgeCode());
            if (hobbyId != null) {
                hobbyIds.add(hobbyId);
            }
        });
        Map<Long, HobbyDTO> hobbies = hobbyIds.isEmpty() ? Map.of() : hobbyService.getHobbiesIgnoringVisibility(hobbyIds);

        for (UserEquippedBadge row : rows) {
            EquippedBadgeDTO badge = toBadge(row.getBadgeCode(), hobbies);
            if (badge != null) {
                result.computeIfAbsent(row.getUserId(), id -> new ArrayList<>()).add(badge);
            }
        }
        return result;
    }

    private EquippedBadgeDTO toBadge(String code, Map<Long, HobbyDTO> hobbies) {
        Long hobbyId = courseHobbyId(code);
        if (hobbyId != null) {   // 수료 배지
            String suffix = code.substring(code.lastIndexOf('-') + 1);
            HobbyDTO hobby = hobbies.get(hobbyId);
            String hobbyName = hobby == null ? "" : hobby.getName() + " ";
            return switch (suffix) {
                case "B" -> new EquippedBadgeDTO(code, "🥉", hobbyName + "입문");
                case "I" -> new EquippedBadgeDTO(code, "🥈", hobbyName + "중급");
                case "A" -> new EquippedBadgeDTO(code, "🥇", hobbyName + "고급");
                case "M" -> new EquippedBadgeDTO(code, "👑", hobbyName + "마스터");
                default -> null;
            };
        }
        try {   // 업적 배지
            AchievementType type = AchievementType.valueOf(code);
            return new EquippedBadgeDTO(code, type.getEmoji(), type.getTitle());
        } catch (IllegalArgumentException e) {
            return null;   // 없어진 코드는 건너뛴다
        }
    }

    /** 수료 배지 코드(C{취미 id}-{B|I|A|M})면 취미 id, 아니면 null */
    private Long courseHobbyId(String code) {
        if (code == null || !code.startsWith("C") || code.indexOf('-') < 2) {
            return null;
        }
        try {
            return Long.parseLong(code.substring(1, code.indexOf('-')));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
