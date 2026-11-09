package com.wanted.lmspractice.domain.instructor.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 강사 홈(T-S01) 화면 한 장의 데이터 — KPI 5개 + 분반 표 + 기한 초과 경고
 * [담당] 팀원 1 · #07
 * [연결] InstructorDashboardService.getHome → InstructorHomeController → instructor/home.html
 * (이슈 목록에 없는 보조 DTO: KPI 합계를 템플릿에서 계산하지 않으려고 추가)
 */
@Getter
@AllArgsConstructor
public class InstructorHomeDTO {

    private final List<SectionSummaryDTO> sections;
    private final int activeTotal;
    private final int pendingTotal;
    private final int imminentTotal;
    private final int overdueTotal;
    private final List<OverdueReviewDTO> overdueReviews;

    public int getSectionCount() {
        return sections.size();
    }

    /** 기한이 지난 평가 한 건 (기한이 지나도 평가할 수 있다 — 경고만 표시). 관리자 홈에서도 재사용한다. */
    @Getter
    @AllArgsConstructor
    public static class OverdueReviewDTO {
        private final Long reviewId;
        private final String courseTitle;
        private final String levelLabel;
        private final String studentName;
        private final String instructorName;   // 담당 공백이면 null
        private final LocalDateTime dueAt;
    }
}
