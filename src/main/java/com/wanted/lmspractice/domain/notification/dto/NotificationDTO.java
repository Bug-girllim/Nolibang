package com.wanted.lmspractice.domain.notification.dto;

import com.wanted.lmspractice.domain.notification.entity.Notification;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 알림 목록·헤더 미리보기에 보여 줄 값 — 유형(제목), 메시지, 생성 시각, 읽음 여부, 이동 버튼 글자
 * [담당] 팀원 4 · #17   [화면] U-S15·T-S09, 공통 헤더 알림창   [API] NT-01
 * [연결]
 *  - 만든 곳: NotificationService (from)
 *  - 쓰는 곳: notification/list.html, notification/instructor-list.html, common/layout.html (headerNotifications: id·title·body·readAt)
 * 필드 이름(id·title·body·readAt·createdAt)은 헤더 조각이 이미 쓰는 이름이다.
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class NotificationDTO {

    private Long id;
    private NotificationKind kind;
    private String title;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public static NotificationDTO from(Notification n) {
        return new NotificationDTO(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getCreatedAt(), n.getReadAt());
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 알림 행의 이동 버튼 글자 (API NT-01 표의 이동 화면에 맞춤) */
    public String getActionLabel() {
        return switch (kind) {
            case WAITLIST_CONFIRMED -> "확정 내용 보기";
            case REVIEW_APPROVED -> "결과 보기";
            case REVIEW_REJECTED -> "사유 확인";
            case COURSE_COMPLETED -> "배지 보기";
            case WORK_HIDDEN -> "사유 보기";
            case WORK_RESTORED -> "작품 보기";
            case COMMENT_ON_MY_WORK, REPLY_TO_MY_COMMENT, PUBLIC_COMMENT -> "댓글 보기";
            case INQUIRY_ANSWERED -> "답변 보기";
            case INQUIRY_RECEIVED -> "답변하기";
            case REVIEW_SUBMITTED -> "평가하러 가기";
            case SECTION_ASSIGNED -> "담당 분반 보기";
        };
    }
}
