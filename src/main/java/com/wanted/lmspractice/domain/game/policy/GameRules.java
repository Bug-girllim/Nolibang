package com.wanted.lmspractice.domain.game.policy;

import com.wanted.lmspractice.domain.game.dto.ActivityStatDTO;

/**
 * [역할] RPG 성장 규칙을 한곳에 모은 클래스 — 경험치 값, 레벨 곡선, 레벨별 칭호. 규칙을 바꾸려면 이 파일만 고친다.
 * [연결] 사용처: GameService(캐릭터·길드 점수 계산), GameStatQuery(보스 경험치 SQL 파라미터), 테스트 GameRulesTest
 * [규칙]
 *  - 경험치는 저장하지 않고 학습·작품 기록에서 계산한다(차시 완료 30, 최종 과제 제출 50, 보스 격파 수준별, 공개 작품 40, 댓글 5, 받은 좋아요 3, 퀘스트 보상).
 *  - 댓글·좋아요는 경험치 반영에 상한이 있다(도배로 레벨을 올리지 못하게).
 *  - 레벨 L 에 도달하는 누적 경험치 = 50 × L × (L − 1) → Lv2 100, Lv3 300, Lv4 600, Lv5 1000 …
 * [기술] 🟢 순수 Java 계산(DB·Spring 의존 없음)
 */
public final class GameRules {

    public static final int XP_LESSON = 30;            // 차시 완료 1개
    public static final int XP_SUBMIT = 50;            // 최종 과제 제출 1회(재제출 포함)
    public static final int XP_BOSS_BASE = 100;        // 보스 격파(수료) 기본 — 수준 번호(입문 1·중급 2·고급 3)를 곱한 값을 더한다
    public static final int XP_BOSS_PER_LEVEL = 100;
    public static final int XP_WORK = 40;              // 공개 일반 작품 1개
    public static final int XP_COMMENT = 5;            // 댓글 1개
    public static final int XP_LIKE = 3;               // 내 작품이 받은 좋아요 1개
    public static final int COMMENT_XP_CAP = 20;       // 경험치가 되는 댓글 수 상한
    public static final int LIKE_XP_CAP = 100;         // 경험치가 되는 좋아요 수 상한
    public static final int MAX_LEVEL = 50;

    private GameRules() {
    }

    /** 활동 기록 → 총 경험치 */
    public static long totalXp(ActivityStatDTO stat) {
        return (long) stat.getLessons() * XP_LESSON
                + (long) stat.getSubmits() * XP_SUBMIT
                + stat.getBossXp()
                + (long) stat.getWorks() * XP_WORK
                + (long) Math.min(stat.getComments(), COMMENT_XP_CAP) * XP_COMMENT
                + (long) Math.min(stat.getLikes(), LIKE_XP_CAP) * XP_LIKE
                + stat.getQuestXp();
    }

    /** 레벨 level 에 도달하는 데 필요한 누적 경험치 (Lv1 = 0) */
    public static long xpToReach(int level) {
        return 50L * level * (level - 1);
    }

    /** 누적 경험치 → 레벨 (1 ~ MAX_LEVEL) */
    public static int levelOf(long xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpToReach(level + 1)) {
            level++;
        }
        return level;
    }

    /** 레벨별 칭호 */
    public static String titleOf(int level) {
        if (level >= 12) return "장인";
        if (level >= 8) return "숙련가";
        if (level >= 5) return "수련생";
        if (level >= 3) return "견습생";
        return "새싹 놀이꾼";
    }

    /** 취미별 직업 이름 — 그 취미에서 수료한 수준에 따라 달라진다 (cleared = 수료한 수준 개수, master = 세 수준 모두 수료) */
    public static String classNameOf(String hobbyName, int highestClearedCode, boolean master) {
        if (master) return hobbyName + " 마스터";
        return switch (highestClearedCode) {
            case 1 -> hobbyName + " 견습생";
            case 2 -> hobbyName + " 숙련자";
            case 3 -> hobbyName + " 고수";
            default -> hobbyName + " 입문 전";
        };
    }
}
