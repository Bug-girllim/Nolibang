package com.wanted.lmspractice.domain.work.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 관리자 작품 숨김·복구 조치 이력 1건 (work_moderation_logs) — 한 번 남기면 고치거나 지우지 않는다
 * [담당] 팀원 4 · #20   [화면] A-S08 조치 이력 탭, U-S16 작성자 사유 확인   [기획] FR-036, Q-01
 * [연결] 만든 곳: WorkModerationService.hide / restore(WorkModerationLog.record) / 읽는 곳: WorkModerationService(이력 조회)
 * 칼럼은 resources/sql/schema.sql 의 `work_moderation_logs`(H5 블록) 표와 1:1로 매핑했다. 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다.
 */
@Entity
@Table(name = "work_moderation_logs")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class WorkModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ModerationAction action;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    /** 조치 1건 기록 — 작품 상태 변경(Work.hide / restore)과 같은 트랜잭션에서 저장한다 */
    public static WorkModerationLog record(Long workId, ModerationAction action, String reason, Long adminId) {
        WorkModerationLog log = new WorkModerationLog();
        log.workId = workId;
        log.action = action;
        log.reason = reason;
        log.adminId = adminId;
        return log;
    }
}
