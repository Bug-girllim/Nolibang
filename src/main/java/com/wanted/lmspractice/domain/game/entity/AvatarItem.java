package com.wanted.lmspractice.domain.game.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * [역할] 꾸미기 아이템 목록(캐릭터 아이콘·프로필 테두리·닉네임 색). DB에 두지 않고 코드로 정의한다 —
 *        "가진 아이템"은 레벨·마스터 배지 같은 해금 조건에서 계산하므로 인벤토리 표가 필요 없다.
 * [연결] 사용처: GameService(해금 판단·장착 검증), UserAvatar(슬롯), templates/game/character.html(미리보기 css 클래스)
 * [규칙] unlockLevel 이상이고, masterRequired 이면 마스터 배지를 1개 이상 가졌을 때 해금된다.
 */
public enum AvatarItem {

    // 캐릭터 아이콘 (value = 이모지)
    AVATAR_CHICK(Slot.AVATAR, "chick", "병아리", "🐣", 1, false),
    AVATAR_FOX(Slot.AVATAR, "fox", "여우", "🦊", 2, false),
    AVATAR_PANDA(Slot.AVATAR, "panda", "판다", "🐼", 4, false),
    AVATAR_OWL(Slot.AVATAR, "owl", "부엉이", "🦉", 6, false),
    AVATAR_DRAGON(Slot.AVATAR, "dragon", "용", "🐲", 10, false),

    OUTFIT_PENCIL(Slot.OUTFIT, "colored-pencil", "색연필화 의상", "colored-pencil", 1, false),
    OUTFIT_DIGITAL(Slot.OUTFIT, "digital-drawing", "디지털 드로잉 의상", "digital-drawing", 1, false),
    OUTFIT_DESSERT(Slot.OUTFIT, "dessert", "디저트 만들기 의상", "dessert", 1, false),

    HEAD_NONE(Slot.HEAD, "none", "왕관 벗기", "", 1, false),
    HEAD_CROWN(Slot.HEAD, "crown", "마스터의 왕관", "👑", 1, true),

    // 프로필 테두리 (value = css 클래스)
    FRAME_WOOD(Slot.FRAME, "wood", "나무 테두리", "frame-wood", 1, false),
    FRAME_BRONZE(Slot.FRAME, "bronze", "청동 테두리", "frame-bronze", 3, false),
    FRAME_SILVER(Slot.FRAME, "silver", "은 테두리", "frame-silver", 5, false),
    FRAME_GOLD(Slot.FRAME, "gold", "금 테두리", "frame-gold", 8, false),
    FRAME_RAINBOW(Slot.FRAME, "rainbow", "무지개 테두리", "frame-rainbow", 1, true),

    // 닉네임 색 (value = css 클래스)
    COLOR_INK(Slot.NAME_COLOR, "ink", "먹색", "nc-ink", 1, false),
    COLOR_GREEN(Slot.NAME_COLOR, "green", "초록", "nc-green", 2, false),
    COLOR_BLUE(Slot.NAME_COLOR, "blue", "파랑", "nc-blue", 4, false),
    COLOR_PURPLE(Slot.NAME_COLOR, "purple", "보라", "nc-purple", 6, false),
    COLOR_ORANGE(Slot.NAME_COLOR, "orange", "주황", "nc-orange", 10, false);

    public enum Slot {
        AVATAR("캐릭터"), OUTFIT("대표 취미 의상"), HEAD("머리 장식"), FRAME("테두리"), NAME_COLOR("닉네임 색");

        private final String label;

        Slot(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Slot slot;
    private final String code;
    private final String label;
    private final String value;
    private final int unlockLevel;
    private final boolean masterRequired;

    AvatarItem(Slot slot, String code, String label, String value, int unlockLevel, boolean masterRequired) {
        this.slot = slot;
        this.code = code;
        this.label = label;
        this.value = value;
        this.unlockLevel = unlockLevel;
        this.masterRequired = masterRequired;
    }

    public Slot getSlot() { return slot; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    /** 정적 이미지 경로. 기존 value(이모지/CSS)는 호환성을 위해 유지한다. */
    public String getImagePath() {
        return switch (slot) {
            case AVATAR -> "/images/game/avatars/" + code + ".png";
            case FRAME -> "/images/game/frames/" + code + ".svg";
            case NAME_COLOR -> null;
            case OUTFIT -> "/images/game/outfits/chick-" + code + ".png";
            case HEAD -> "crown".equals(code) ? "/images/game/avatars/crown.png" : null;
        };
    }
    public int getUnlockLevel() { return unlockLevel; }
    public boolean isMasterRequired() { return masterRequired; }

    public boolean isUnlocked(int level, boolean hasMaster) {
        return level >= unlockLevel && (!masterRequired || hasMaster);
    }

    /** 해금 조건 안내 문구 — 잠긴 아이템 카드에 보인다 */
    public String unlockHint() {
        return masterRequired ? "마스터 배지 1개 필요" : "Lv." + unlockLevel + " 달성";
    }

    public static List<AvatarItem> of(Slot slot) {
        return Arrays.stream(values()).filter(item -> item.slot == slot).toList();
    }

    public static Optional<AvatarItem> find(Slot slot, String code) {
        return of(slot).stream().filter(item -> item.code.equals(code)).findFirst();
    }

    /** 슬롯의 기본 아이템(첫 번째) — 장착 기록이 없을 때 쓴다 */
    public static AvatarItem defaultOf(Slot slot) {
        return of(slot).get(0);
    }
}
