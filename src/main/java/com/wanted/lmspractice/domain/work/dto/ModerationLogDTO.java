package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.work.entity.ModerationAction;
import com.wanted.lmspractice.domain.work.entity.WorkModerationLog;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 조치 이력 1줄 표시값 — 작품 제목, 조치(숨김/복구), 사유, 시각
 * [담당] 팀원 4 · #20   [화면] A-S08 조치 이력 탭, U-S16 이력 표
 * [연결] 만든 곳: WorkModerationService / 쓰는 곳: templates/admin/works.html, work/moderation.html
 *  - 관리자 id·이름은 담지 않는다(작성자 화면에 노출하지 않음).
 */
@Getter
@AllArgsConstructor
public class ModerationLogDTO {

    private final Long id;
    private final Long workId;
    private final String workTitle;
    private final ModerationAction action;
    private final String reason;
    private final LocalDateTime createdAt;

    public static ModerationLogDTO of(WorkModerationLog log, String workTitle) {
        return new ModerationLogDTO(log.getId(), log.getWorkId(), workTitle, log.getAction(), log.getReason(), log.getCreatedAt());
    }

    public String getActionLabel() {
        return action.getLabel();
    }

    public boolean isHide() {
        return action == ModerationAction.HIDE;
    }

    /** 표의 "사유 요약" — 40자를 넘으면 줄임표 */
    public String getReasonSummary() {
        return reason.length() > 40 ? reason.substring(0, 40) + "…" : reason;
    }
}
