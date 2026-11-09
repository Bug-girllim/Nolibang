package com.wanted.lmspractice.domain.course.entity;

/**
 * 강의 상태 (courses.status) — MVP는 모두 OPEN. CLOSED는 영상 제공 종료(P2)용
 * 담당: 팀원 2 · #08
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum CourseStatus {
    OPEN,
    CLOSED
}
