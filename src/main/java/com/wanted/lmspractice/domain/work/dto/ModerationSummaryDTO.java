package com.wanted.lmspractice.domain.work.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 관리자 작품 화면 머리의 숫자 — 공개 작품 전체·게시 중·숨김·최종 과제·일반 작품
 * [담당] 팀원 4 · #20   [화면] A-S14 KPI·노출 상태 버튼, A-S08 탭 개수
 * [연결] 만든 곳: WorkModerationService.getSummary / 쓰는 곳: templates/admin/works.html, admin/works-board.html
 */
@Getter
@AllArgsConstructor
public class ModerationSummaryDTO {

    private final long total;
    private final long visible;
    private final long hidden;
    private final long finalCount;
    private final long generalCount;
}
