package com.wanted.lmspractice.domain.enrollment.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 학습자가 대기 중인 강의 한 개 — 대기 번호와 신청 시각
 * [담당] 팀원 2 · #11(조회 계약 추가) — 수강 도메인 소유(#09)   [화면] U-S08   [기획] D-53(강의 단일 대기열, 신청 순서대로 FIFO)   [API] ML-01
 * [연결]
 *  - 만든 곳: WaitlistService.getMyWaiting
 *  - 쓰는 곳: MyLearningService → MyCourseDTO ("대기 n번" 카드, [대기 취소] 버튼의 waitlistId)
 * [기술] 🟢 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class MyWaitlistDTO {

    private Long waitlistId;
    private Long courseId;
    /** 대기 번호(1부터). 같은 강의에서 먼저 신청한 대기자 수 + 1 */
    private int waitingNumber;
    private LocalDateTime appliedAt;
}
