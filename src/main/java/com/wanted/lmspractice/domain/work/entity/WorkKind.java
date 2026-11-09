package com.wanted.lmspractice.domain.work.entity;

/**
 * 작품 종류: 최종 과제 / 일반 작품
 * 담당: 팀원 4 · #16
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum WorkKind {
    FINAL,
    GENERAL
}
