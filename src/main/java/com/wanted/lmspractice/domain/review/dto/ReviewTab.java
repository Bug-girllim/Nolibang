package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewStatus;

/**
 * [역할] 강사 평가 목록(T-S03)의 탭 — 평가 대기 / 진행 중 / 완료(승인·반려)
 * [담당] 팀원 3 · #14   [API] RV-01 Query tab (WAITING 기본 / IN_REVIEW / DONE)
 * [연결] 쓰는 곳: InstructorReviewController(?tab=), ReviewService.getReviewList(회차 분류)
 */
public enum ReviewTab {
    WAITING,
    IN_REVIEW,
    DONE;

    /** 회차 상태가 이 탭에 속하는지 */
    public boolean contains(ReviewStatus status) {
        return switch (this) {
            case WAITING -> status == ReviewStatus.WAITING;
            case IN_REVIEW -> status == ReviewStatus.IN_REVIEW;
            case DONE -> status == ReviewStatus.APPROVED || status == ReviewStatus.REJECTED;
        };
    }
}
