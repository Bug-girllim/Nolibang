package com.wanted.lmspractice.domain.learning.dto;

import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 나의 학습 현황 카드 한 장 — 강의·상태·분반·남은 기간·차시별 진도·최종 과제 평가 상태, 그리고 버튼을 정하는 값
 * [담당] 팀원 2 · #11   [화면] U-S08   [기획] FR-029, D-20·D-41·D-42·D-55   [API] ML-01 MyCourseDTO
 * [연결]
 *  - 만든 곳: MyLearningService.getMyLearning(대기는 toWaitingCard, 수강 건은 toEnrollmentCard)
 *  - 쓰는 곳: templates/learning/my-learning.html (상태 칩·4칸 정보·버튼)
 * [기술] 🟢 DTO + 화면 판단용 게터(DB를 부르지 않는 단순 계산)
 * 대기 카드는 enrollmentId가 null이고 waitlistId가 있으며, 수강 건 카드는 반대다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class MyCourseDTO {

    /** 카드 마감 임박 기준: 남은 기간이 이 일수 미만이면 "반려 시 재제출 기회가 없을 수 있어요" 안내 (D-41, 30일 중 23일 경과) */
    public static final int SHORT_ON_TIME_DAYS = 7;

    /** 수강 건 카드의 수강 id (대기 카드는 null) */
    private Long enrollmentId;
    /** 대기 카드의 대기 id — [대기 취소] 버튼에 쓴다 (수강 건 카드는 null) */
    private Long waitlistId;
    private Long courseId;
    private String courseTitle;
    private String hobbyName;
    /** 취미가 지금 운영 중인지. false면 "준비 중 전환" 표시(새 신청은 불가, 기존 수강은 계속 진입 — D-20) */
    private boolean hobbyOperating;
    private CourseLevel level;
    private LearningState state;

    /** 대기 번호(대기 카드). 그 밖에는 0 */
    private int waitingNumber;
    /** 대기 신청 시각 — 대기 카드, 그리고 대기열을 거쳐 자동 확정된 수강 건 */
    private LocalDateTime waitlistAppliedAt;

    private String sectionName;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    /** 수강 종료까지 남은 일수(올림, 0 이상). 진행 중이 아니면 null */
    private Integer remainingDays;

    /** 차시별 진도 (진도 계약 C10). 아직 정보가 없으면 빈 목록 */
    private List<LessonProgressDTO> lessons;
    /** 필수 3차시를 모두 완료했는지 — [최종 과제] 버튼 활성 조건 */
    private boolean allLessonsCompleted;
    /** 최종 과제 평가 상태 (평가 계약 C11). 미제출이면 status가 null */
    private ReviewStatusDTO review;
    /** [이어서 학습] 버튼이 열 차시 id — 아직 안 끝낸 첫 차시, 모두 끝났으면 마지막 차시. 차시 정보가 없으면 null */
    private Long continueLessonId;

    public String getLevelLabel() {
        return level.getLabel();
    }

    /** 수강 중인(진행 중) 카드인지 */
    public boolean isActive() {
        return state.isActive();
    }

    /** 수강 취소 버튼을 쓸 수 있는지 — 진행 중이고 평가 대기·진행 중이 아니어야 한다 (EN-03: 평가 중 취소 불가) */
    public boolean isCancellable() {
        return state.isActive() && !state.isReviewing();
    }

    /** "남은 기간이 7일 미만" 안내를 보여 줄지 — 앞으로 반려될 수 있는 상태(과제 전·평가 중)일 때만 (D-41) */
    public boolean isShortOnTime() {
        return remainingDays != null
                && remainingDays < SHORT_ON_TIME_DAYS
                && (state == LearningState.LEARNING || state.isReviewing());
    }

    /** 최종 과제를 새로 제출하거나 다시 제출할 수 있는 상태인지 — 3차시를 모두 끝냈고 (학습 중 또는 반려) */
    public boolean isSubmittable() {
        return allLessonsCompleted && (state == LearningState.LEARNING || state == LearningState.REJECTED);
    }

    /** 남은 기간 표시 (예: D-12). 진행 중이 아니면 null */
    public String getRemainingLabel() {
        if (remainingDays == null) {
            return null;
        }
        return remainingDays == 0 ? "D-day" : "D-" + remainingDays;
    }

    /** 수강 기간(일) — 기획 확정값을 강의 상세와 같은 상수에서 가져온다 */
    public int getEnrollmentDays() {
        return CourseDetailDTO.ENROLLMENT_DAYS;
    }

    /** 최종 과제 평가 상태 이름 (미제출·평가 대기·평가 중·수료 승인·반려) */
    public String getReviewLabel() {
        if (review == null || review.getStatus() == null) {
            return "미제출";
        }
        return switch (review.getStatus()) {
            case WAITING -> "평가 대기";
            case IN_REVIEW -> "평가 중";
            case APPROVED -> "수료 승인";
            case REJECTED -> "반려";
        };
    }

    /** 완료한 차시 수 */
    public long getCompletedLessonCount() {
        return lessons.stream().filter(LessonProgressDTO::isCompleted).count();
    }

    /** 완료한 차시가 하나라도 있는지 */
    public boolean isAnyLessonCompleted() {
        return lessons.stream().anyMatch(LessonProgressDTO::isCompleted);
    }
}
