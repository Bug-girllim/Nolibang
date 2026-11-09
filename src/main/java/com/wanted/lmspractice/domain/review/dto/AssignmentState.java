package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewStatus;

/**
 * [역할] 최종 과제 화면(U-S07)의 상태 — API AS-01 모델 state 값. 수강 기간·3차시 완료·최신 회차 상태로 정한다.
 * [담당] 팀원 3 · #13   [화면] U-S07   [기획] FR-021·026, D-41   [API] AS-01 state (§ 상태값 표)
 * [연결]
 *  - 만든 곳: AssignmentService.getAssignmentView(decide)
 *  - 쓰는 곳: AssignmentViewDTO, templates/review/assignment.html(th:switch·단계 표시)
 * [기술] 🟢 enum
 */
public enum AssignmentState {

    LOCKED_LESSONS("3차시 미완료", 0),     // 3차시를 다 끝내지 않아 아직 제출할 수 없음
    NOT_SUBMITTED("미제출 · 작성 중", 0),   // 제출 가능, 아직 제출 전
    WAITING("평가 대기", 1),               // 제출됨 — 평가 시작 전까지 수정 가능(마감 불변)
    IN_REVIEW("평가 진행 중", 2),          // 강사가 평가 시작 — 수정 잠금
    REJECTED("반려 · 재제출 가능", 3),      // 반려 확정 + 수강 기간 안 — 정식 재제출(새 7일)
    APPROVED("수료", 4),                   // 승인 → 수료
    EXPIRED("제출 기간 종료", -1);          // 제출·재제출할 수 없는 수강 건(기간 만료·취소)

    private final String label;
    /** 화면 위 진행 단계(과제 작성·평가 대기·평가 진행·반려/재제출·수료 결과) 중 현재 칸 번호. -1이면 강조하지 않음 */
    private final int step;

    AssignmentState(String label, int step) {
        this.label = label;
        this.step = step;
    }

    /**
     * 화면 상태를 정한다. 평가가 걸려 있는 회차(대기·진행·승인)는 수강 기간과 관계없이 그 상태를 보여 준다
     * — 만료 후에도 평가는 이어진다. 기간이 닫혀 있으면 새 제출·재제출만 막힌다(EXPIRED).
     * 호출: AssignmentService.getAssignmentView
     *
     * @param latest      최신 회차 상태 (미제출이면 null)
     * @param periodOpen  제출할 수 있는 수강 기간인지 (SubmissionPolicy.isPeriodOpen)
     * @param lessonsDone 3차시를 모두 완료했는지 (ProgressService.isAllCompleted)
     */
    public static AssignmentState decide(ReviewStatus latest, boolean periodOpen, boolean lessonsDone) {
        if (latest == ReviewStatus.WAITING) {
            return WAITING;
        }
        if (latest == ReviewStatus.IN_REVIEW) {
            return IN_REVIEW;
        }
        if (latest == ReviewStatus.APPROVED) {
            return APPROVED;
        }
        if (!periodOpen) {
            return EXPIRED;  // 미제출이거나 반려인데 기간이 끝남 — 05 §4-2 만료 후 반려는 종료
        }
        if (latest == ReviewStatus.REJECTED) {
            return REJECTED;
        }
        return lessonsDone ? NOT_SUBMITTED : LOCKED_LESSONS;
    }

    public String getLabel() {
        return label;
    }

    public int getStep() {
        return step;
    }

    /** 새 제출 폼(최초 제출·재제출)을 보여 줄 상태인지 */
    public boolean isWritable() {
        return this == NOT_SUBMITTED || this == REJECTED;
    }

    /** 앞으로 반려될 수 있는 상태인지 — 23일 경과 안내(D-41) 대상 */
    public boolean canBeRejectedLater() {
        return this == LOCKED_LESSONS || this == NOT_SUBMITTED || this == WAITING || this == IN_REVIEW || this == REJECTED;
    }
}
