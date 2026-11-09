package com.wanted.lmspractice.domain.hobby.entity;

/**
 * 대분류·세부 취미 노출 상태: 운영 중 / 준비 중 / 비공개
 * 담당: 팀원 2 · #08
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum ExposureState {
    OPERATING,
    PREPARING,
    PRIVATE
}
