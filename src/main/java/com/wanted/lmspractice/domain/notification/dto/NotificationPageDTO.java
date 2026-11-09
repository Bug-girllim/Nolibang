package com.wanted.lmspractice.domain.notification.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 알림함 한 페이지 — 알림 목록과 페이지 정보
 * [담당] 팀원 4 · #17   [화면] U-S15·T-S09   [API] NT-01
 * [연결] 만든 곳: NotificationService.getPage / 쓰는 곳: NotificationController(모델 notifications·page·totalPages)
 */
@AllArgsConstructor
@Getter
@ToString
public class NotificationPageDTO {

    private List<NotificationDTO> items;
    /** 0부터 (API §1-9) */
    private int page;
    private int totalPages;

    public boolean isHasPrev() {
        return page > 0;
    }

    public boolean isHasNext() {
        return page + 1 < totalPages;
    }
}
