package com.wanted.lmspractice.domain.enrollment.entity;

/**
 * 대기 상태 (waitlist_entries.status)
 * 담당: 팀원 2 · #09
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum WaitlistStatus {
    WAITING,
    CONFIRMED,
    CANCELLED
}
