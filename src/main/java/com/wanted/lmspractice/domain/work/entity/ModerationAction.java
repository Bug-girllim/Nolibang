package com.wanted.lmspractice.domain.work.entity;

/**
 * 작품 조치 종류: 숨김 / 복구 (work_moderation_logs.action)
 * 담당: 팀원 4 · #20
 * 값은 resources/sql/schema.sql의 CHECK 제약(ck_modlogs_action) 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장).
 */
public enum ModerationAction {
    HIDE("숨김"),
    RESTORE("복구");

    private final String label;

    ModerationAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
