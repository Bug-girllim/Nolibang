package com.wanted.lmspractice.domain.instructor.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 분반 한 줄의 현재 숫자 — 수강 중·수료·반려·평가 대기 (D-58: 현재 숫자만)
 * [담당] 팀원 1 · #07   [화면] T-S05, A-S13 (T-S01은 SectionSummaryDTO.from으로 재사용)   [기획] FR-051, D-58
 * [연결]
 *  - 만든 곳: InstructorStatQuery.findSectionStats (native 집계)
 *  - 쓰는 곳: InstructorStatService, InstructorDashboardService, 강사/관리자 통계 템플릿
 * [통계 정의] ❓ #01 확정 필요 — 시안 T-S05 하단 "통계 정의"를 그대로 따른다.
 *  - 수강 중(learningCount)  = ACTIVE 이면서 평가 대기·진행(WAITING/IN_REVIEW) 회차가 없는 수강
 *  - 수료(completedCount)    = COMPLETED 수강 누적
 *  - 반려(rejectedCount)     = REJECTED 평가 회차 누적
 *  - 평가 대기(pendingCount) = WAITING/IN_REVIEW 평가 회차
 *  - 활성 수강(activeCount)  = ACTIVE 전체 (학습 중 + 평가 대기·진행 + 기간 내 반려)
 * [기술] 🟢 Lombok DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SectionStatDTO {

    private Long sectionId;
    private String courseTitle;
    private String levelLabel;     // 입문 / 중급 / 고급
    private String sectionName;    // 예: A분반
    private int capacity;
    private int activeCount;
    private int learningCount;
    private int completedCount;
    private int rejectedCount;
    private int pendingCount;
    private int imminentCount;     // 평가 대기·진행 중 마감까지 48시간 이내(아직 안 지남)
    private int overdueCount;      // 평가 대기·진행 중 마감 지남

    /** 합계 행용: 분반 정보 없이 숫자만 더한다. */
    public static SectionStatDTO total(List<SectionStatDTO> rows) {
        SectionStatDTO t = new SectionStatDTO();
        for (SectionStatDTO r : rows) {
            t.activeCount += r.activeCount;
            t.learningCount += r.learningCount;
            t.completedCount += r.completedCount;
            t.rejectedCount += r.rejectedCount;
            t.pendingCount += r.pendingCount;
            t.imminentCount += r.imminentCount;
            t.overdueCount += r.overdueCount;
        }
        return t;
    }
}
