package com.wanted.lmspractice.domain.learning.dto;

/**
 * [역할] 나의 학습 현황의 상태 탭(전체·학습 중·대기·자동 확정·평가 중·반려·수료·만료·취소)과 각 탭에 속하는 상태
 * [담당] 팀원 2 · #11   [화면] U-S08   [API] ML-01 쿼리 status
 * [연결]
 *  - 만든 곳: MyLearningController(요청의 status → 이 enum, 없으면 ALL)
 *  - 쓰는 곳: MyLearningService(목록 거르기), templates/learning/my-learning.html(탭 이름)
 * [기술] 🟢 enum
 * API 명세는 탭을 5개(ALL·WAITING·LEARNING·COMPLETED·ENDED)로 제안했지만, 시안에는 "평가 중"과 "반려" 탭이 따로 있어서 7개로 맞췄다.
 */
public enum LearningFilter {

    ALL("전체"),
    LEARNING("학습 중"),
    WAITING("대기·자동 확정"),
    REVIEWING("평가 중"),
    REJECTED("반려"),
    COMPLETED("수료"),
    ENDED("만료·취소");

    private final String label;

    LearningFilter(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 이 탭에 보여 줄 상태인지 */
    public boolean matches(LearningState state) {
        return switch (this) {
            case ALL -> true;
            case LEARNING -> state == LearningState.LEARNING;
            case WAITING -> state == LearningState.WAITING || state == LearningState.AUTO_CONFIRMED;
            case REVIEWING -> state == LearningState.REVIEW_WAITING || state == LearningState.IN_REVIEW;
            case REJECTED -> state == LearningState.REJECTED;
            case COMPLETED -> state == LearningState.COMPLETED;
            case ENDED -> state == LearningState.EXPIRED || state == LearningState.CANCELLED;
        };
    }
}
