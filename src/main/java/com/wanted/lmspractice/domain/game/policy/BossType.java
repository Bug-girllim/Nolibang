package com.wanted.lmspractice.domain.game.policy;

/**
 * [역할] 취미별 보스 3종(+기본)과 취미별 공격 연출 — 이름·도구 이모지·일반 공격·최종 과제 필살기
 * [담당] RPG 성장 요소   [화면] game/character.html 보스전 대치 화면, 보스 카드
 * [연결]
 *  - 호출받음: GameService.toBoss (취미 이름 → 보스 종류)
 *  - 쓰는 곳: BossDTO(이름·연출 문구), templates/game/boss-art.html (보스 그림 — code 로 고른다), static/js/boss-battle.js (이모지)
 * [규칙] 입문·중급·고급은 같은 보스에 장식(뿔·왕관)과 크기만 더한다(화면 CSS). 취미 이름이 목록에 없으면 기본 보스(GENERIC).
 *        보스 체력과 연출은 학습 진행도에 따른 것일 뿐 실제 평가 점수와 관계없다.
 * [기술] 🟢 enum
 */
public enum BossType {

    COLORED_PENCIL("colored-pencil", "색연필화", "색을 훔친 먹구름", "🖍️", "색연필 빛줄기", "🌈", "무지개 채색"),
    DIGITAL_DRAWING("digital-drawing", "디지털 드로잉", "오류 난 픽셀 골렘", "🖊️", "스타일러스 궤적", "💥", "레이어 폭발"),
    DESSERT("dessert", "디저트 만들기", "폭주한 반죽 슬라임", "🌫️", "밀가루 구름", "🍰", "달콤한 피니시"),
    GENERIC("generic", "", "정체불명의 그림자", "✨", "빛줄기", "🌟", "전력 공격");

    private final String code;
    private final String hobbyName;
    private final String bossName;
    private final String toolEmoji;
    private final String attackName;
    private final String ultimateEmoji;
    private final String ultimateName;

    BossType(String code, String hobbyName, String bossName, String toolEmoji, String attackName,
             String ultimateEmoji, String ultimateName) {
        this.code = code;
        this.hobbyName = hobbyName;
        this.bossName = bossName;
        this.toolEmoji = toolEmoji;
        this.attackName = attackName;
        this.ultimateEmoji = ultimateEmoji;
        this.ultimateName = ultimateName;
    }

    /** 취미 이름으로 보스 종류 찾기 — 없으면 GENERIC */
    public static BossType of(String hobbyName) {
        for (BossType type : values()) {
            if (type != GENERIC && type.hobbyName.equals(hobbyName)) {
                return type;
            }
        }
        return GENERIC;
    }

    public String getCode() { return code; }
    public String getBossName() { return bossName; }
    public String getToolEmoji() { return toolEmoji; }
    public String getAttackName() { return attackName; }
    public String getUltimateEmoji() { return ultimateEmoji; }
    public String getUltimateName() { return ultimateName; }
}
