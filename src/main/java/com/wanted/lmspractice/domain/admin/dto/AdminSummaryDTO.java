package com.wanted.lmspractice.domain.admin.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 관리자 홈(A-S01) KPI 5개 + "확인이 필요한 운영 항목"
 * [담당] 팀원 1 · #07   [화면] A-S01   [기획] D-59
 * [연결] AdminDashboardService.getSummary → AdminHomeController → admin/home.html
 */
@Getter
@AllArgsConstructor
public class AdminSummaryDTO {

    private final int courseCount;
    private final int lessonCount;
    private final int activeEnrollmentCount;
    private final int pendingReviewCount;
    private final int waitingCount;
    private final int vacantSectionCount;
    private final List<Alert> alerts;

    /** 운영 확인 항목 한 줄. linkPath가 null이면 이동 버튼 없이 안내만 표시한다. */
    @Getter
    @AllArgsConstructor
    public static class Alert {
        private final String item;
        private final String content;
        private final String linkPath;
        private final String linkLabel;
    }
}
