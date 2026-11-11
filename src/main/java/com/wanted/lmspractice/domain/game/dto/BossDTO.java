package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 보스전 카드 한 장 — 수강 1건의 최종 과제 상황을 보스전으로 보여 준다 */
@Getter
@AllArgsConstructor
public class BossDTO {
    private Long enrollmentId;
    private Long reviewRoundId;   // 평가 결과 화면 링크용 (없으면 null)
    private String courseTitle;
    private String levelLabel;
    private String stateName;     // LearningState 이름 — 화면 색·버튼 분기
    private String stateLabel;
    private String message;
    private int hpPercent;        // 보스 체력 막대 (격파 0, 도전 전 100)
    private int completedLessons;
    private int totalLessons;
}
