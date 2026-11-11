package com.wanted.lmspractice.domain.game.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

/** 한 사람의 활동 기록 집계 — 경험치 계산의 재료 (GameStatQuery 가 만든다) */
@Getter
@AllArgsConstructor
public class ActivityStatDTO {
    private Long userId;
    private int lessons;      // 완료한 차시
    private int submits;      // 최종 과제 제출 횟수(재제출 포함)
    private long bossXp;      // 수료(보스 격파) 경험치 합계 — 수준별로 다르다
    private int works;        // 공개 일반 작품
    private int comments;     // 쓴 댓글
    private int likes;        // 내 작품이 받은 좋아요
    private long questXp;     // 받은 퀘스트 보상 합계
    private int bosses;       // 보스 격파(수료) 횟수 — 업적 배지 판단용
    private int quests;       // 받은 퀘스트 보상 횟수 — 업적 배지 판단용
}
