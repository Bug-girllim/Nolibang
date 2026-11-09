package com.wanted.lmspractice.domain.instructor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 관리자 강사 통계(A-S13)의 강사 한 줄 — 담당 분반 수 + 4개 숫자 + 계정 상태
 * [담당] 팀원 1 · #07
 * [연결] InstructorStatService.getInstructorStats → AdminStatController → admin/stats.html
 * (이슈 목록에 없는 보조 DTO)
 */
@Getter
@AllArgsConstructor
public class InstructorStatSummaryDTO {

    private final Long instructorId;
    private final String name;
    private final String accountLabel;   // 활성 / 비활성 / 첫 로그인 전
    private final int sectionCount;
    private final int learningCount;
    private final int completedCount;
    private final int rejectedCount;
    private final int pendingCount;
}
