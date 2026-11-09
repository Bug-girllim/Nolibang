package com.wanted.lmspractice.domain.notification.dto;

import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import java.util.List;

/**
 * [역할] 알림함의 유형 탭 — 탭마다 보여 줄 알림 종류 묶음
 * [담당] 팀원 4 · #17   [화면] U-S15(학습자 탭), T-S09(강사 탭)   [API] NT-01 (쿼리 tab)
 * [연결] NotificationController(탭 값 받기) → NotificationService.getPage(탭의 종류 묶음으로 조회) → 템플릿 탭 버튼
 * 학습자: 전체 / 수강 확정 / 평가 결과·수료 / 댓글·문의·작품,  강사: 전체 / 문의·댓글 / 평가·피드백 / 운영 안내
 * ❓ API 명세는 쿼리 이름을 kind(💡)로 제안했지만 한 종류가 아니라 묶음이라 tab으로 정했다(#01 확인 필요).
 */
public enum NotificationTab {
    ALL("전체", List.of(NotificationKind.values())),
    ENROLLMENT("수강 확정", List.of(NotificationKind.WAITLIST_CONFIRMED)),
    RESULT("평가 결과·수료", List.of(NotificationKind.REVIEW_APPROVED, NotificationKind.REVIEW_REJECTED,
            NotificationKind.COURSE_COMPLETED)),
    ACTIVITY("댓글·문의·작품", List.of(NotificationKind.WORK_HIDDEN, NotificationKind.WORK_RESTORED,
            NotificationKind.COMMENT_ON_MY_WORK, NotificationKind.REPLY_TO_MY_COMMENT,
            NotificationKind.INQUIRY_ANSWERED)),
    TEACHER_INQUIRY("문의·댓글", List.of(NotificationKind.INQUIRY_RECEIVED, NotificationKind.PUBLIC_COMMENT)),
    TEACHER_REVIEW("평가·피드백", List.of(NotificationKind.REVIEW_SUBMITTED)),
    TEACHER_OPERATION("운영 안내", List.of(NotificationKind.SECTION_ASSIGNED));

    private final String label;
    private final List<NotificationKind> kinds;

    NotificationTab(String label, List<NotificationKind> kinds) {
        this.label = label;
        this.kinds = kinds;
    }

    public String getLabel() {
        return label;
    }

    public List<NotificationKind> getKinds() {
        return kinds;
    }

    /** 쿼리 값 → 탭. 비었거나 모르는 값이면 전체 */
    public static NotificationTab of(String value) {
        if (value != null) {
            for (NotificationTab tab : values()) {
                if (tab.name().equalsIgnoreCase(value)) {
                    return tab;
                }
            }
        }
        return ALL;
    }
}
