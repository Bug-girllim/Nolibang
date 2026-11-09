package com.wanted.lmspractice.domain.work.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 작품 한 건의 조치 화면용 값 — 작품 요약(숨김 상태 포함)과 그 작품의 조치 이력(최신순)
 * [담당] 팀원 4 · #20   [화면] A-S08 선택 패널, U-S16 조치 상세
 * [연결] 만든 곳: WorkModerationService.getTarget / getAuthorView / 쓰는 곳: AdminWorkController, MyWorkController
 */
@Getter
@AllArgsConstructor
public class ModerationViewDTO {

    private final WorkDTO work;
    private final List<ModerationLogDTO> logs;

    /** 가장 최근 조치 — 없으면 null */
    public ModerationLogDTO getLatest() {
        return logs.isEmpty() ? null : logs.get(0);
    }
}
