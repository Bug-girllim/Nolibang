package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 평가 결과·공식 피드백 화면(U-S14)에 보여 줄 값 — 결과, 피드백/반려 사유 원문, 담당 강사, 제출·평가 시작·확정 시각, 이력, 배지
 * [담당] 팀원 3 · #15 (#14와 같은 이름을 쓴다)   [화면] U-S14   [API] AS-05
 * [연결]
 *  - 만든 곳: ReviewResultService.getResult
 *  - 쓰는 곳: AssignmentController.result → templates/review/result.html (모델 review)
 * [#15 정의 — 팀원 3 확인 필요] #14에서 같은 DTO를 쓰게 되면 이 필드를 기준으로 맞춘다.
 * 확정 시각 표시: 승인이면 "수료일", 반려면 "평가 확정"으로 같은 값(finalizedAt)을 쓴다(수료일 = 승인 시각).
 */
@AllArgsConstructor
@Getter
@ToString
public class ReviewDetailDTO {

    private Long reviewRoundId;
    private Long enrollmentId;
    private Long courseId;
    /** 사이드 메뉴의 "차시 영상 학습" 링크용 첫 차시 id */
    private Long firstLessonId;
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    /** 평가를 확정한 강사 이름. 알 수 없으면 null */
    private String instructorName;
    private ReviewStatus status;
    /** 승인 피드백 또는 반려 사유 원문 */
    private String feedbackBody;
    private int roundNo;
    private LocalDateTime submittedAt;
    private LocalDateTime dueAt;
    private LocalDateTime startedAt;
    private LocalDateTime finalizedAt;
    /** 수강 종료 시각 — 재제출 가능 기간 계산용 */
    private LocalDateTime expiresAt;
    /** 반려 후 재제출까지 남은 일수 (기간이 지났으면 0) */
    private long remainingDays;
    /** 반려이고 최신 회차이며 수강 기간 안이면 재제출 링크를 보인다 */
    private boolean canResubmit;
    /** 승인일 때 이 강의로 얻은 배지(수준 배지, 마스터가 되면 마스터 배지). 그 밖에는 빈 목록 */
    private List<BadgeDTO> badges;
    /** 같은 수강 건의 제출·평가 이력 (회차 순) */
    private List<HistoryDTO> history;

    public boolean isApproved() {
        return status == ReviewStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ReviewStatus.REJECTED;
    }

    /** 이력 한 회차 */
    @AllArgsConstructor
    @Getter
    @ToString
    public static class HistoryDTO {
        private int roundNo;
        private LocalDateTime submittedAt;
        private LocalDateTime dueAt;
        /** 아직 확정 전이면 null */
        private LocalDateTime finalizedAt;
        private ReviewStatus status;

        public boolean isApproved() {
            return status == ReviewStatus.APPROVED;
        }
    }
}
