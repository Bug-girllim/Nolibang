package com.wanted.lmspractice.domain.notification.entity;

/**
 * 사이트 알림 종류 13종 (notifications.kind) — 이메일 알림 없음(D-57)
 * 담당: 팀원 4 · #17
 * 값은 schema.sql의 CHECK 값과 같아야 한다. 각 종류가 채워야 하는 대상 칼럼(targetType)도 DB CHECK와 같다.
 * 피드백 마감 임박(REVIEW_DUE_SOON)은 11-06 팀 결정으로 제외 — 마감 임박·초과는 평가 목록 화면 경고로만 표시.
 */
public enum NotificationKind {
    // 학습자 — P0
    WAITLIST_CONFIRMED(NotificationTargetType.ENROLLMENT),      // 대기 → 자동 수강 확정
    REVIEW_APPROVED(NotificationTargetType.REVIEW_ROUND),       // 평가 승인
    REVIEW_REJECTED(NotificationTargetType.REVIEW_ROUND),       // 평가 반려
    COURSE_COMPLETED(NotificationTargetType.ENROLLMENT),        // 수료
    // 학습자 — P1
    WORK_HIDDEN(NotificationTargetType.WORK),                   // 관리자 작품 숨김
    WORK_RESTORED(NotificationTargetType.WORK),                 // 관리자 작품 복구
    COMMENT_ON_MY_WORK(NotificationTargetType.COMMENT),         // 내 작품에 댓글
    REPLY_TO_MY_COMMENT(NotificationTargetType.COMMENT),        // 내 댓글에 답글
    INQUIRY_ANSWERED(NotificationTargetType.INQUIRY_THREAD),    // 1:1 문의 답변
    // 강사 (HTML T-S09)
    INQUIRY_RECEIVED(NotificationTargetType.INQUIRY_THREAD),    // 새 1:1 문의
    PUBLIC_COMMENT(NotificationTargetType.COMMENT),             // 담당 취미 공개 작품에 새 댓글
    REVIEW_SUBMITTED(NotificationTargetType.REVIEW_ROUND),      // 담당 분반 과제 제출·재제출
    SECTION_ASSIGNED(NotificationTargetType.SECTION);           // 분반 담당 배정

    private final NotificationTargetType targetType;

    NotificationKind(NotificationTargetType targetType) {
        this.targetType = targetType;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }
}
