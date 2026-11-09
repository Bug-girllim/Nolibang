package com.wanted.lmspractice.domain.enrollment.entity;

/**
 * 수강 상태 (enrollments.status)
 * ACTIVE = 정원 점유: 학습 중 + 평가 대기·진행(기간 만료 후 포함) + 기간 내 반려
 * 담당: 팀원 2 · #09
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum EnrollmentStatus {
    ACTIVE,      // 정원 점유
    COMPLETED,   // 수료 승인
    CANCELLED,   // 본인 취소 (평가 대기·진행 중에는 불가)
    EXPIRED      // 30일 만료 + 평가할 회차 없음, 또는 만료 후 반려
}
