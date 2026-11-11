package com.wanted.lmspractice.domain.game.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [역할] 보스전 카드 한 장 — 수강 1건의 최종 과제 상황을 보스전으로 보여 준다 (대치 화면·보스 목록 공통)
 * [연결] 만든 곳: GameService.toBoss   쓰는 곳: game/character.html (#gm-battle 대치 화면, 보스전 목록), static/js/boss-battle.js (data-* 속성)
 * [규칙] hpPercent 는 학습 진행도에 따른 연출 값이다 — 실제 평가 점수가 아니다(화면에 그렇게 안내한다).
 *        phase: LEARNING(수련 중) · ROOM_OPEN(보스의 방 열림) · WAITING(강사 판정 대기) · REJECTED(반려) · DEFEATED(수료) · EXPIRED(기간 끝)
 */
@Getter
@Builder
public class BossDTO {
    private Long enrollmentId;
    private Long reviewRoundId;   // 평가 결과(강사 피드백) 화면 링크용 (없으면 null)
    private String courseTitle;
    private String levelLabel;
    private int levelNo;          // 1 입문 · 2 중급 · 3 고급 — 보스 크기·장식
    private String stateName;     // LearningState 이름
    private String stateLabel;
    private String phase;
    private String message;
    private int hpPercent;        // 보스 체력 막대 (격파 0)
    private int completedLessons;
    private int totalLessons;

    // 보스 종류·연출
    private String bossCode;      // colored-pencil · digital-drawing · dessert · generic — 보스 그림·CSS 선택
    private String bossName;
    private String toolEmoji;     // 일반 공격(차시 완료) 때 날아가는 도구
    private String attackName;
    private String ultimateEmoji; // 최종 과제 제출 필살기
    private String ultimateName;

    // 지금 할 일 하나 (큰 버튼)
    private String actionLabel;
    private String actionUrl;     // null 이면 눌러도 갈 곳이 없는 상태(비활성 표시)
    private boolean actionDisabled;
    private String subLabel;      // 보조 버튼(없으면 null)
    private String subUrl;

    // 격파 보상 (수료 승인 상태에서만 채운다)
    private long rewardXp;
    private String rewardBadge;       // 예: "🥉 색연필화 입문 수료 배지"
    private String rewardClassName;   // 예: "색연필 견습생"
    private String rewardClassStage;  // 견습 · 숙련 · 전문가 · 마스터
    private String rewardClassImage;

    private String heroImagePath;
    private String heroAvatarLabel;
    private String heroStage;
    private Long recordOwner;
    private boolean heroCrowned;

    public void setRecordOwner(Long userId) { recordOwner = userId; }

    /** 직업 카드·대치·격파 보상에 모두 현재 장착 동물을 사용한다. */
    public void applyAppearance(CharacterDTO character, String imageStage) {
        heroImagePath = character.professionImage(bossCode);
        heroAvatarLabel = character.getAvatarLabel();
        heroStage = imageStage;
        heroCrowned = character.isCrowned();
        if (rewardClassName != null) rewardClassImage = heroImagePath;
    }

    public String getAttackImagePath() {
        return "/images/game/effects/" + artCode() + "-attack.png";
    }

    public String getUltimateImagePath() {
        return "/images/game/effects/" + artCode() + "-ultimate.png";
    }

    private String artCode() {
        return switch (bossCode == null ? "" : bossCode) {
            case "digital-drawing" -> "digital-drawing";
            case "dessert" -> "dessert";
            default -> "colored-pencil";
        };
    }

    /** 대치 화면에서 먼저 보여 줄 우선순위 — 클수록 먼저 (반려 > 판정 대기 > 보스의 방 > 수련 중 > 격파 > 기간 끝) */
    public int getPriority() {
        return switch (phase) {
            case "REJECTED" -> 6;
            case "WAITING" -> 5;
            case "ROOM_OPEN" -> 4;
            case "LEARNING" -> 3;
            case "DEFEATED" -> 2;
            default -> 1;
        };
    }

    public boolean isDefeated() { return "DEFEATED".equals(phase); }

    /** 탭에 보여 줄 짧은 상황 이름 */
    public String getPhaseLabel() {
        return switch (phase) {
            case "LEARNING" -> "수련 중";
            case "ROOM_OPEN" -> "보스의 방 열림";
            case "WAITING" -> "판정 대기";
            case "REJECTED" -> "재도전";
            case "DEFEATED" -> "격파";
            default -> "기간 끝";
        };
    }
}
