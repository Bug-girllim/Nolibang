package com.wanted.lmspractice.domain.review.dto;

/**
 * [역할] 강사 평가 목록(T-S03)의 「기한」 필터 — 마감 임박(48시간 이하) / 기한 초과
 * [담당] 팀원 3 · #14   [API] RV-01 (❓ 명세 Query에 없음 — 시안 T-S03 기한 select를 살리려고 due 파라미터를 추가)
 * [연결] 쓰는 곳: InstructorReviewController(?due=), ReviewService.getReviewList — 판단은 ReviewListDTO의 dueSoon·overdue
 */
public enum DueFilter {
    SOON,
    OVERDUE;

    /** 목록 1행이 이 필터에 맞는지 */
    public boolean matches(ReviewListDTO row) {
        return this == SOON ? row.isDueSoon() : row.isOverdue();
    }
}
