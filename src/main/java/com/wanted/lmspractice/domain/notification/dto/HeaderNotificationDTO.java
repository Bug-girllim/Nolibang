package com.wanted.lmspractice.domain.notification.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 머리글 알림 값 한 묶음 — 안 읽은 알림 수 + 최근 5건 미리보기 (성능 개선: 한 트랜잭션에서 같이 읽는다)
 * [담당] 성능 개선 (팀원 4 · #17 알림 기능 위)   [화면] 공통 헤더(common/layout.html gnb)   [API] §1-8
 * [연결] 만든 곳: NotificationService.getHeaderSummary / 쓰는 곳: HeaderNotificationInterceptor(모델 unreadCount·headerNotifications)
 */
@AllArgsConstructor
@Getter
@ToString
public class HeaderNotificationDTO {

    private long unreadCount;
    private List<NotificationDTO> notifications;
}
