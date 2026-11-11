package com.wanted.lmspractice.domain.game.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 취미별 직업·스킬 트리 한 줄 — 입문 → 중급 → 고급 수료 여부 */
@Getter
@AllArgsConstructor
public class HobbyClassDTO {
    private Long hobbyId;
    private String hobbyName;
    private String className;
    private List<Step> steps;
    private boolean master;
    private int clearedCount;

    /** 세 수준을 모두 수료한 경우에만 마스터. 수료 전에는 견습 이미지를 흐리게 보여 준다. */
    public String getImageStage() {
        if (master) return "master";
        int highest = 0;
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).isCleared()) highest = i + 1;
            }
        }
        return switch (highest) {
            case 3 -> "expert";
            case 2 -> "skilled";
            default -> "apprentice";
        };
    }

    public String getImagePath() {
        if (hobbyName == null) return null;
        String code = switch (hobbyName) {
            case "색연필화" -> "colored-pencil";
            case "디지털 드로잉" -> "digital-drawing";
            case "디저트 만들기" -> "dessert";
            default -> null;
        };
        return code == null ? null : "/images/game/jobs/" + code + "-" + getImageStage() + ".png";
    }

    @Getter
    @AllArgsConstructor
    public static class Step {
        private String levelLabel;
        private boolean cleared;
    }
}
