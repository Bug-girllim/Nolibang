package com.wanted.lmspractice.domain.instructor.dto;

/**
 * [역할] 담당 학습자 목록의 "상태" 필터·표시 값 (T-S02). 수강 상태 + 최신 평가 회차 상태로 정한다.
 * [담당] 팀원 1 · #07
 * [연결] InstructorStatQuery(판정), InstructorStudentController(필터 파라미터), instructor/students.html
 */
public enum StudentStatus {
    LEARNING("학습 중"),
    WAITING("평가 대기"),
    IN_REVIEW("평가 진행"),
    REJECTED("반려"),
    APPROVED("수료 승인"),
    EXPIRED("만료");

    private final String label;

    StudentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * enrollments.status / review_rounds.status 글자(schema.sql CHECK 값)로 판정한다.
     * 평가 회차가 없으면 reviewStatus는 null. enum 값이 바뀌면 여기도 함께 고친다(이슈 #07 주의 사항).
     */
    public static StudentStatus of(String enrollmentStatus, String reviewStatus) {
        if ("COMPLETED".equals(enrollmentStatus)) {
            return APPROVED;
        }
        if ("EXPIRED".equals(enrollmentStatus)) {
            return EXPIRED;
        }
        if ("WAITING".equals(reviewStatus)) {
            return WAITING;
        }
        if ("IN_REVIEW".equals(reviewStatus)) {
            return IN_REVIEW;
        }
        if ("REJECTED".equals(reviewStatus)) {
            return REJECTED;
        }
        return LEARNING;
    }
}
