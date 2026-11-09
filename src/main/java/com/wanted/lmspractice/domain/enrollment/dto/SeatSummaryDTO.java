package com.wanted.lmspractice.domain.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 한 강의의 정원 현황 — 신청 가능한 분반(담당 강사가 있는 분반)의 정원 합계·남은 자리 합계·대기 인원
 * [담당] 팀원 2 · #08(조회 계약 추가) — 수강 도메인 소유(#09)   [화면] U-S03·U-S04   [기획] D-52·D-53
 * [연결]
 *  - 만든 곳: SeatService.getSeatSummary
 *  - 쓰는 곳: CourseCatalogService → CourseDTO·CourseDetailDTO → templates/course/list.html·detail.html
 * [기술] 🟢 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SeatSummaryDTO {

    /** 신청 가능한 분반의 정원 합계 */
    private int totalCapacity;
    /** 신청 가능한 분반의 남은 자리 합계 */
    private int remainingSeats;
    /** 대기 중(WAITING) 인원 */
    private int waitingCount;

    /** 남은 자리가 없는지 (만석이면 대기 신청으로 바뀐다 — D-52) */
    public boolean isFull() {
        return remainingSeats <= 0;
    }
}
