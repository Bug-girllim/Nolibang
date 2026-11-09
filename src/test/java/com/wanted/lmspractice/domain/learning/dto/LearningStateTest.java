package com.wanted.lmspractice.domain.learning.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] 나의 학습 현황의 상태 결정 규칙·탭 분류·요약 숫자 테스트 — DB 없이 순수 계산만 확인한다
 * [담당] 팀원 2 · #11
 * 평가 서비스(#14)가 아직 스텁이라 시드 데이터로는 반려·평가 중 상태를 만들 수 없어서, 그 규칙은 이 단위 테스트로 검증한다.
 */
class LearningStateTest {

    @Test
    @DisplayName("끝난 수강(수료·만료·취소)은 평가 상태와 상관없이 수강 상태를 따른다")
    void finishedEnrollments() {
        assertThat(LearningState.derive(EnrollmentStatus.COMPLETED, ReviewStatus.APPROVED, false, true)).isEqualTo(LearningState.COMPLETED);
        assertThat(LearningState.derive(EnrollmentStatus.EXPIRED, null, false, false)).isEqualTo(LearningState.EXPIRED);
        assertThat(LearningState.derive(EnrollmentStatus.EXPIRED, ReviewStatus.REJECTED, false, true)).isEqualTo(LearningState.EXPIRED);
        assertThat(LearningState.derive(EnrollmentStatus.CANCELLED, null, false, false)).isEqualTo(LearningState.CANCELLED);
    }

    @Test
    @DisplayName("진행 중 수강은 가장 최근 평가 상태에 따라 평가 대기·평가 중·반려로 나뉜다")
    void activeFollowsReviewStatus() {
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, ReviewStatus.WAITING, false, true)).isEqualTo(LearningState.REVIEW_WAITING);
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, ReviewStatus.IN_REVIEW, false, true)).isEqualTo(LearningState.IN_REVIEW);
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, ReviewStatus.REJECTED, false, true)).isEqualTo(LearningState.REJECTED);
    }

    @Test
    @DisplayName("과제를 안 냈으면 학습 중, 대기열에서 막 확정되어 완료한 차시가 없으면 자동 확정")
    void learningOrAutoConfirmed() {
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, null, false, false)).isEqualTo(LearningState.LEARNING);
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, null, false, true)).isEqualTo(LearningState.LEARNING);
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, null, true, false)).isEqualTo(LearningState.AUTO_CONFIRMED);
        // 대기열 출신이어도 한 차시라도 끝냈으면 더는 "자동 확정 안내"가 아니라 학습 중이다
        assertThat(LearningState.derive(EnrollmentStatus.ACTIVE, null, true, true)).isEqualTo(LearningState.LEARNING);
    }

    @Test
    @DisplayName("진행 중 여부와 평가 중 여부 — 평가 대기·진행 중에는 수강 취소가 안 된다")
    void activeAndReviewingFlags() {
        assertThat(LearningState.REVIEW_WAITING.isReviewing()).isTrue();
        assertThat(LearningState.IN_REVIEW.isReviewing()).isTrue();
        assertThat(LearningState.REJECTED.isReviewing()).isFalse();
        assertThat(LearningState.LEARNING.isActive()).isTrue();
        assertThat(LearningState.WAITING.isActive()).isFalse();   // 대기는 아직 수강이 아니다
        assertThat(LearningState.COMPLETED.isActive()).isFalse();
    }

    @Test
    @DisplayName("탭 분류: 대기·자동 확정은 한 탭, 만료·취소는 한 탭")
    void filterMatching() {
        assertThat(LearningFilter.WAITING.matches(LearningState.WAITING)).isTrue();
        assertThat(LearningFilter.WAITING.matches(LearningState.AUTO_CONFIRMED)).isTrue();
        assertThat(LearningFilter.LEARNING.matches(LearningState.AUTO_CONFIRMED)).isFalse();
        assertThat(LearningFilter.REVIEWING.matches(LearningState.IN_REVIEW)).isTrue();
        assertThat(LearningFilter.REVIEWING.matches(LearningState.REJECTED)).isFalse();
        assertThat(LearningFilter.ENDED.matches(LearningState.EXPIRED)).isTrue();
        assertThat(LearningFilter.ENDED.matches(LearningState.CANCELLED)).isTrue();
        assertThat(LearningFilter.ENDED.matches(LearningState.COMPLETED)).isFalse();
        for (LearningState state : LearningState.values()) {
            assertThat(LearningFilter.ALL.matches(state)).isTrue();
        }
    }

    private MyCourseDTO card(LearningState state) {
        MyCourseDTO card = new MyCourseDTO();
        card.setState(state);
        card.setLevel(CourseLevel.BASIC);
        card.setLessons(List.of());
        card.setReview(ReviewStatusDTO.notSubmitted());
        return card;
    }

    @Test
    @DisplayName("요약 숫자는 탭과 상관없이 전체 카드로 세고, 목록은 선택한 탭만 담는다")
    void summaryCountsAllCards() {
        List<MyCourseDTO> all = List.of(
                card(LearningState.LEARNING), card(LearningState.LEARNING),
                card(LearningState.WAITING), card(LearningState.AUTO_CONFIRMED),
                card(LearningState.IN_REVIEW), card(LearningState.COMPLETED),
                card(LearningState.REJECTED), card(LearningState.EXPIRED), card(LearningState.CANCELLED));

        MyLearningDTO onlyCompleted = MyLearningDTO.of(all, LearningFilter.COMPLETED);

        assertThat(onlyCompleted.getCourses()).hasSize(1);
        assertThat(onlyCompleted.getLearningCount()).isEqualTo(2);
        assertThat(onlyCompleted.getWaitingCount()).isEqualTo(2);
        assertThat(onlyCompleted.getReviewingCount()).isEqualTo(1);
        assertThat(onlyCompleted.getCompletedCount()).isEqualTo(1);
        assertThat(onlyCompleted.getEndedCount()).isEqualTo(3);  // 반려 + 만료 + 취소
    }

    @Test
    @DisplayName("수강 종료가 7일 미만이면 반려 재제출 안내(D-41), 이미 끝난 카드나 반려된 카드에는 보이지 않는다")
    void shortOnTimeNotice() {
        MyCourseDTO learning = card(LearningState.LEARNING);
        learning.setRemainingDays(6);
        assertThat(learning.isShortOnTime()).isTrue();

        learning.setRemainingDays(7);
        assertThat(learning.isShortOnTime()).isFalse();

        MyCourseDTO reviewing = card(LearningState.IN_REVIEW);
        reviewing.setRemainingDays(2);
        assertThat(reviewing.isShortOnTime()).isTrue();

        MyCourseDTO completed = card(LearningState.COMPLETED);
        assertThat(completed.isShortOnTime()).isFalse();  // remainingDays가 null
    }

    @Test
    @DisplayName("수강 취소는 진행 중이고 평가 대기·진행 중이 아닐 때만, 최종 과제는 3차시를 끝낸 학습 중·반려일 때만")
    void buttonRules() {
        assertThat(card(LearningState.LEARNING).isCancellable()).isTrue();
        assertThat(card(LearningState.REJECTED).isCancellable()).isTrue();
        assertThat(card(LearningState.AUTO_CONFIRMED).isCancellable()).isTrue();
        assertThat(card(LearningState.REVIEW_WAITING).isCancellable()).isFalse();
        assertThat(card(LearningState.IN_REVIEW).isCancellable()).isFalse();
        assertThat(card(LearningState.COMPLETED).isCancellable()).isFalse();
        assertThat(card(LearningState.WAITING).isCancellable()).isFalse();

        MyCourseDTO learning = card(LearningState.LEARNING);
        assertThat(learning.isSubmittable()).isFalse();   // 3차시를 아직 못 끝냄
        learning.setAllLessonsCompleted(true);
        assertThat(learning.isSubmittable()).isTrue();

        MyCourseDTO reviewing = card(LearningState.REVIEW_WAITING);
        reviewing.setAllLessonsCompleted(true);
        assertThat(reviewing.isSubmittable()).isFalse();  // 이미 제출함
    }

    @Test
    @DisplayName("남은 기간 표시: D-12 / D-day / 진행 중이 아니면 없음")
    void remainingLabel() {
        MyCourseDTO card = card(LearningState.LEARNING);
        card.setRemainingDays(12);
        assertThat(card.getRemainingLabel()).isEqualTo("D-12");
        card.setRemainingDays(0);
        assertThat(card.getRemainingLabel()).isEqualTo("D-day");
        card.setRemainingDays(null);
        assertThat(card.getRemainingLabel()).isNull();
    }
}
