package com.wanted.lmspractice.domain.notification.entity;

/**
 * 알림이 가리키는 대상 종류 → notifications 표의 대상 칼럼
 * 담당: 팀원 4 · #17
 * DB CHECK(ck_notifications_target)가 알림 종류별로 정한 칼럼과 같다.
 */
public enum NotificationTargetType {
    ENROLLMENT,      // enrollment_id
    REVIEW_ROUND,    // review_round_id
    WORK,            // work_id
    COMMENT,         // comment_id
    INQUIRY_THREAD,  // inquiry_thread_id
    SECTION          // section_id
}
