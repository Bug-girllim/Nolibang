package com.wanted.lmspractice.domain.enrollment.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 대기열 한 줄 표시 — 대기 중 목록(순번·학습자·신청 시각)과 최근 자동 확정 내역(확정 시각·배정 분반)에 같이 쓴다
 * [담당] 팀원 2 · #10   [화면] A-S12   [API] WL-01 모델 waitlist, recentConfirmed
 * [연결]
 *  - 만든 곳: WaitlistQuery(native 조회) → WaitlistService(getWaitlist·getRecentConfirmed)
 *  - 쓰는 곳: templates/admin/waitlists.html
 * [기술] 🟢 DTO
 * 학습자 실명이 들어 있으므로 관리자 화면에만 쓴다(NFR-04, WL-01 "실명·아이디 관리자 열람").
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class WaitlistDTO {

    private Long entryId;
    /** 대기 순번(1부터) — 대기 중 목록에서만 채운다. 저장된 값이 아니라 FIFO(id) 순서로 매긴다(D-53) */
    private int position;
    private Long userId;
    private String realName;
    private String loginId;
    /** 대기 신청 시각 */
    private LocalDateTime requestedAt;
    /** 자동 확정 시각 — 확정 내역에서만 채운다 */
    private LocalDateTime confirmedAt;
    /** 배정된 분반 표시 이름 — 확정 내역에서만 채운다 */
    private String sectionName;

    /** 대기 1순위인지 — "자리가 나면 즉시 확정" 표시 */
    public boolean isFirst() {
        return position == 1;
    }

    /** "김학습 (learner)" — 관리자 화면 학습자 칸 */
    public String getLearnerLabel() {
        return realName + " (" + loginId + ")";
    }
}
