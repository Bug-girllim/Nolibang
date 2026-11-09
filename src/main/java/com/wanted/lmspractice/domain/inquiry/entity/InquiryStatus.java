package com.wanted.lmspractice.domain.inquiry.entity;

/**
 * 1:1 문의 스레드 상태 (inquiry_threads.status)
 * 담당: 팀원 4 · #21
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum InquiryStatus {
    OPEN,
    ANSWERED,
    CLOSED
}
