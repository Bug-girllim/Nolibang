package com.wanted.lmspractice.domain.learning.dto;

import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;

/**
 * [역할] 나의 학습 현황 카드 한 장의 "화면 상태" — 대기·자동 확정·학습 중·평가 대기·평가 중·반려·수료·만료·취소
 * [담당] 팀원 2 · #11   [화면] U-S08   [기획] D-41·D-42·D-53·D-55   [API] ML-01 (MyCourseDTO.상태)
 * [연결]
 *  - 만든 곳: MyLearningService(대기 목록은 WAITING, 수강 건은 derive로 결정)
 *  - 쓰는 곳: MyCourseDTO.state, LearningFilter(탭·요약 숫자), templates/learning/my-learning.html(칩·버튼)
 * [기술] 🟢 enum, 순수 메서드(DB를 부르지 않아 단위 테스트로 규칙을 확인할 수 있다)
 * 수강 상태(EnrollmentStatus)는 ACTIVE/COMPLETED/CANCELLED/EXPIRED 넷뿐이라, 진행 중(ACTIVE)을 평가 상태(ReviewStatus)로 더 나눈 것이다.
 */
public enum LearningState {

    /** 대기열에서 순서를 기다리는 중 */
    WAITING("대기"),
    /** 대기열을 거쳐 막 수강이 확정되어 아직 학습을 시작하지 않은 상태 (확정 안내 카드) */
    AUTO_CONFIRMED("자동 확정"),
    /** 수강 중이며 과제를 아직 내지 않았다 */
    LEARNING("학습 중"),
    /** 최종 과제를 냈고 강사가 평가를 시작하기 전 — 학습자 수정 가능 */
    REVIEW_WAITING("평가 대기"),
    /** 강사가 평가 중 — 학습자 수정 잠금 */
    IN_REVIEW("평가 중"),
    /** 반려됐고 수강 기간 안이라 다시 제출할 수 있다 */
    REJECTED("반려"),
    /** 수료 승인 */
    COMPLETED("수료"),
    /** 기간이 지나 종료(최종 과제 미제출 또는 만료 후 반려) */
    EXPIRED("만료"),
    /** 본인이 취소 */
    CANCELLED("취소");

    private final String label;

    LearningState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 수강 건의 화면 상태를 정한다.
     *
     * @param enrollment         수강 상태(ACTIVE·COMPLETED·CANCELLED·EXPIRED)
     * @param latestReview       가장 최근 평가 회차의 상태. 과제를 낸 적이 없으면 null
     * @param fromWaitlist       대기열을 거쳐 자동 확정된 수강인지
     * @param anyLessonCompleted 완료한 차시가 하나라도 있는지 (있으면 "자동 확정 안내"를 졸업하고 학습 중으로 본다)
     */
    public static LearningState derive(EnrollmentStatus enrollment, ReviewStatus latestReview,
                                       boolean fromWaitlist, boolean anyLessonCompleted) {
        return switch (enrollment) {
            case COMPLETED -> COMPLETED;
            case EXPIRED -> EXPIRED;
            case CANCELLED -> CANCELLED;
            case ACTIVE -> deriveActive(latestReview, fromWaitlist, anyLessonCompleted);
        };
    }

    /** 진행 중(ACTIVE) 수강 건: 평가 상태가 있으면 그것을 따르고, 없으면 자동 확정 안내 또는 학습 중 */
    private static LearningState deriveActive(ReviewStatus latestReview, boolean fromWaitlist, boolean anyLessonCompleted) {
        if (latestReview == null) {
            return (fromWaitlist && !anyLessonCompleted) ? AUTO_CONFIRMED : LEARNING;
        }
        return switch (latestReview) {
            case WAITING -> REVIEW_WAITING;
            case IN_REVIEW -> IN_REVIEW;
            case REJECTED -> REJECTED;
            // 승인된 수강은 같은 트랜잭션에서 COMPLETED가 되므로 ACTIVE와 함께 올 수 없다. 어긋난 데이터를 만나도 학습 중으로 둔다
            case APPROVED -> LEARNING;
        };
    }

    /** 수강 중인(정원을 차지하는) 상태인지 — 남은 기간·취소 가능 여부를 보여 주는 대상 */
    public boolean isActive() {
        return this == AUTO_CONFIRMED || this == LEARNING || this == REVIEW_WAITING || this == IN_REVIEW || this == REJECTED;
    }

    /** 평가 대기·진행 중인지 — 이때는 수강 취소가 안 된다 (CANCEL_NOT_ALLOWED_DURING_REVIEW) */
    public boolean isReviewing() {
        return this == REVIEW_WAITING || this == IN_REVIEW;
    }
}
