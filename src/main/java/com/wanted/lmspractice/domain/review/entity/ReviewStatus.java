package com.wanted.lmspractice.domain.review.entity;

/**
 * 평가 회차 상태 (review_rounds.status)
 * 담당: 팀원 3 · #14
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum ReviewStatus {
    WAITING,     // 평가 대기 — 학습자 수정 가능
    IN_REVIEW,   // 평가 진행 중 — 학습자 수정 잠금
    APPROVED,    // 수료 승인 (피드백 50자 이상)
    REJECTED     // 반려 (사유 50자 이상)
}
