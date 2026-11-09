package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewStatus;

/**
 * [역할] 강사 평가 목록(T-S03)의 탭 — 평가 대기 / 진행 중 / 완료(승인·반려)
 * [담당] 팀원 3 · #14   [API] RV-01 Query `tab` = WAITING(기본) / IN_REVIEW / DONE
 * [연결] InstructorReviewController(쿼리 값 → from), ReviewService.getReviewPage(includes로 행 거르기·탭별 건수)
 */
public enum ReviewTab {
    WAITING,
    IN_REVIEW,
    DONE;

    /** 이 탭에 보일 상태인지 — 완료 탭은 승인·반려를 함께 보여 준다 */
    public boolean includes(ReviewStatus status) {
        return switch (this) {
            case WAITING -> status == ReviewStatus.WAITING;
            case IN_REVIEW -> status == ReviewStatus.IN_REVIEW;
            case DONE -> status == ReviewStatus.APPROVED || status == ReviewStatus.REJECTED;
        };
    }

    /** 쿼리 값 → 탭. 없거나 모르는 값이면 기본 탭(평가 대기) */
    public static ReviewTab from(String value) {
        for (ReviewTab tab : values()) {
            if (tab.name().equals(value)) {
                return tab;
            }
        }
        return WAITING;
    }
}
