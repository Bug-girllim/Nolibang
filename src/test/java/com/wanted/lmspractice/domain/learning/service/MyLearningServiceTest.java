package com.wanted.lmspractice.domain.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.LearningState;
import com.wanted.lmspractice.domain.learning.dto.MyCourseDTO;
import com.wanted.lmspractice.domain.learning.dto.MyLearningDTO;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] MyLearningService 조립 테스트 — 시드 데이터로 본인 기록만 나오는지, 대기 번호·분반·남은 기간·탭 필터가 맞는지 확인
 * [담당] 팀원 2 · #11   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 조회 전용이라 DB를 바꾸지 않는다.
 *       시드: learner = 수료 1(강의 1) · 대기열에서 확정된 수강 1(강의 2) · 진행 중 2(강의 3·7) / learner2 = 진행 중 1 / learner3 = 진행 중 1 + 대기 1(강의 4)
 * 주의: 진도(#12)는 실제 구현이 들어왔지만 평가(#14)는 아직 스텁이라 진행 중 수강은 평가 상태 없이 "학습 중"으로 나온다.
 *       #14가 구현되면 이 테스트의 기대값(특히 진행 중 상태)을 바꾼다. 반려·평가 중 같은 상태 규칙은 LearningStateTest가 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class MyLearningServiceTest {

    @Autowired
    private MyLearningService myLearningService;
    @Autowired
    private UserRepository userRepository;

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    @Test
    @DisplayName("본인 기록만 나온다 — learner는 자기 수강 4건, learner2는 자기 1건")
    void onlyOwnRecords() {
        List<MyCourseDTO> learner = myLearningService.getMyCourses(userId("learner"), LearningFilter.ALL);
        List<MyCourseDTO> learner2 = myLearningService.getMyCourses(userId("learner2"), LearningFilter.ALL);

        assertThat(learner).extracting(MyCourseDTO::getEnrollmentId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(learner2).extracting(MyCourseDTO::getEnrollmentId).containsExactly(5L);
        assertThat(learner).extracting(MyCourseDTO::getEnrollmentId).doesNotContain(5L, 6L);
    }

    @Test
    @DisplayName("진행 중·대기 카드가 앞, 끝난(수료) 카드가 뒤")
    void ongoingCardsFirst() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learner"), LearningFilter.ALL);

        assertThat(cards.get(cards.size() - 1).getState()).isEqualTo(LearningState.COMPLETED);
        assertThat(cards.subList(0, cards.size() - 1)).allSatisfy(card -> assertThat(card.isActive()).isTrue());
    }

    @Test
    @DisplayName("대기열에서 자동 확정된 수강은 대기 신청 시각이 붙고, 한 차시라도 끝냈으면 학습 중으로 나온다")
    void waitlistOriginCard() {
        MyCourseDTO card = myLearningService.getMyCourses(userId("learner"), LearningFilter.ALL).stream()
                .filter(c -> c.getEnrollmentId().equals(2L)).findFirst().orElseThrow();

        assertThat(card.getWaitlistAppliedAt()).isNotNull();
        // 시드: 수강 2는 대기열 출신이지만 2개 차시 중 6번 차시를 이미 완료했다(#12 진도) → "자동 확정 안내"가 아니라 학습 중.
        // 완료 차시가 없는 대기열 출신 수강만 AUTO_CONFIRMED가 된다 — 그 규칙은 LearningStateTest가 확인한다.
        assertThat(card.getCompletedLessonCount()).isEqualTo(1);
        assertThat(card.getState()).isEqualTo(LearningState.LEARNING);
        assertThat(card.getSectionName()).isEqualTo("A분반");
        assertThat(card.getCourseTitle()).isEqualTo("색연필화 응용");
        assertThat(card.getLevelLabel()).isEqualTo("중급");
    }

    @Test
    @DisplayName("대기 카드는 대기 번호·waitlistId를 가지고 수강 id는 없다 (learner3: 강의 4 첫 대기)")
    void waitingCard() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learner3"), LearningFilter.WAITING);

        assertThat(cards).hasSize(1);
        MyCourseDTO waiting = cards.get(0);
        assertThat(waiting.getState()).isEqualTo(LearningState.WAITING);
        assertThat(waiting.getWaitingNumber()).isEqualTo(1);
        assertThat(waiting.getWaitlistId()).isNotNull();
        assertThat(waiting.getEnrollmentId()).isNull();
        assertThat(waiting.getCourseTitle()).isEqualTo("디지털 드로잉 기초");
    }

    @Test
    @DisplayName("진행 중 카드는 남은 기간(1~30일)이 있고, 수료 카드는 남은 기간이 없다")
    void remainingDays() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learner"), LearningFilter.ALL);

        MyCourseDTO active = cards.stream().filter(c -> c.getEnrollmentId().equals(3L)).findFirst().orElseThrow();
        assertThat(active.getRemainingDays()).isBetween(0, 30);
        assertThat(active.getRemainingLabel()).startsWith("D-");

        MyCourseDTO completed = cards.stream().filter(c -> c.getEnrollmentId().equals(1L)).findFirst().orElseThrow();
        assertThat(completed.getState()).isEqualTo(LearningState.COMPLETED);
        assertThat(completed.getRemainingDays()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("탭 필터: 수료는 1건, 만료·취소는 0건, 요약 숫자는 탭과 상관없이 같다")
    void filterAndSummary() {
        Long learner = userId("learner");

        MyLearningDTO completed = myLearningService.getMyLearning(learner, LearningFilter.COMPLETED);
        MyLearningDTO ended = myLearningService.getMyLearning(learner, LearningFilter.ENDED);
        MyLearningDTO all = myLearningService.getMyLearning(learner, LearningFilter.ALL);

        assertThat(completed.getCourses()).hasSize(1);
        assertThat(ended.getCourses()).isEmpty();
        assertThat(all.getCourses()).hasSize(4);
        assertThat(completed.getCompletedCount()).isEqualTo(all.getCompletedCount()).isEqualTo(1);
        assertThat(completed.getLearningCount()).isEqualTo(all.getLearningCount());
    }

    @Test
    @DisplayName("기록이 없는 학습자는 빈 목록이다 (newbie)")
    void noRecords() {
        MyLearningDTO newbie = myLearningService.getMyLearning(userId("newbie"), LearningFilter.ALL);

        assertThat(newbie.getCourses()).isEmpty();
        assertThat(newbie.getLearningCount()).isZero();
    }
}
