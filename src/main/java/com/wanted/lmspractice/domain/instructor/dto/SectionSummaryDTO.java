package com.wanted.lmspractice.domain.instructor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강사 홈 "현재 담당 분반" 표의 한 줄, 학습자 관리의 분반 선택 목록
 * [담당] 팀원 1 · #07   [화면] T-S01, T-S02
 * [연결]
 *  - 만든 곳: InstructorDashboardService (SectionStatDTO를 from()으로 변환)
 *  - 쓰는 곳: instructor/home.html, instructor/students.html
 * [기술] 🟢 정적 팩토리 from()
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SectionSummaryDTO {

    private Long sectionId;
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    private int capacity;
    private int activeCount;
    private int pendingCount;
    private int imminentCount;
    private int overdueCount;

    public static SectionSummaryDTO from(SectionStatDTO s) {
        return new SectionSummaryDTO(s.getSectionId(), s.getCourseTitle(), s.getLevelLabel(), s.getSectionName(),
                s.getCapacity(), s.getActiveCount(), s.getPendingCount(), s.getImminentCount(), s.getOverdueCount());
    }
}
