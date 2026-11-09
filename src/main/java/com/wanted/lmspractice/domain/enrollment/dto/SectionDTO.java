package com.wanted.lmspractice.domain.enrollment.dto;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 분반 한 줄 표시 — 번호·이름, 담당 강사, 정원, 활성 수강 수, 남은 자리, 배정 대상 여부 (DB VIEW v_section_seats 기준)
 * [담당] 팀원 2 · #10   [화면] A-S05, A-S12   [API] SE-01·WL-01 모델 sections, 계약 C6
 * [연결]
 *  - 만든 곳: SectionQuery(native 조회) → SectionService(getSectionsForAdmin·getSectionsByInstructor·getSection)
 *  - 쓰는 곳: templates/admin/sections.html·waitlists.html, AdminInstructorController(담당 분반 수), 팀원 1 #07(C6)
 * [기술] 🟢 DTO
 * 담당 강사 실명이 들어 있으므로 관리자·강사 화면에만 쓴다(NFR-04).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SectionDTO {

    private Long id;
    private Long courseId;
    private String courseTitle;
    private Integer sectionNo;
    /** 분반 이름 — 비어 있으면 화면은 getDisplayName()으로 번호 표시 */
    private String name;
    /** 담당 강사 id — null이면 담당 공백(신규 배정 제외) */
    private Long instructorId;
    private String instructorName;
    private int capacity;
    /** 활성 수강 = ACTIVE 수 (학습 중 + 평가 대기·진행 + 기간 내 반려) */
    private int activeCount;
    /** 남은 자리 = 정원 − 활성 수강 */
    private int remaining;
    /** 다음 신규 신청·자동 확정이 들어갈 분반인지 — SectionService가 정한다(D-52 남은 자리 최대, 같으면 번호 순) */
    private boolean nextAssign;

    /** 화면에 보이는 분반 이름. 이름이 비어 있으면 번호로 1→"A분반", 2→"B분반" (DB H4) */
    public String getDisplayName() {
        return displayName(sectionNo, name);
    }

    /** 담당 강사가 없는 분반인지 — 신규 신청·자동 확정에서 빠진다(D-52) */
    public boolean isVacant() {
        return instructorId == null;
    }

    /** 표의 상태 칩 문구 (A-S05 시안: 운영 · 배정 대상 / 다음 배정 분반 / 신규 배정 제외) */
    public String getStatusLabel() {
        if (isVacant()) {
            return "신규 배정 제외";
        }
        if (nextAssign) {
            return "다음 배정 분반";
        }
        return remaining > 0 ? "운영 · 배정 대상" : "운영 · 만석";
    }

    /** 상태 칩 class — 시안의 chip / chip dk / chip ol 을 그대로 쓴다 */
    public String getStatusChipClass() {
        if (isVacant()) {
            return "chip ol";
        }
        return nextAssign ? "chip dk" : "chip";
    }

    /** 담당 강사 칸 문구 (없으면 "담당 공백") */
    public String getInstructorLabel() {
        return isVacant() ? "담당 공백" : instructorName;
    }

    /** 분반 표시 이름 — 규칙은 CourseSection.displayName 한곳에 있다(#09 신청 화면과 같은 이름). WaitlistQuery도 이 메서드를 쓴다 */
    public static String displayName(Integer sectionNo, String name) {
        return CourseSection.displayName(sectionNo, name);
    }
}
