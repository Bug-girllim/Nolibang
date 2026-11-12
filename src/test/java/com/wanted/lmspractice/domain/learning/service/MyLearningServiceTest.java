package com.wanted.lmspractice.domain.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.LearningState;
import com.wanted.lmspractice.domain.learning.dto.MyCourseDTO;
import com.wanted.lmspractice.domain.learning.dto.MyLearningDTO;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] MyLearningService 조립 테스트 — 시드 데이터로 본인 기록만 나오는지, 대기 번호·분반·남은 기간·탭 필터가 맞는지 확인
 * [담당] 팀원 2 · #11   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 시드에 없는 상황만 테스트 안에서 네이티브 SQL로
 *       만들고(차시 기록 지우기·새 수강 넣기), 클래스가 @Transactional이라 테스트가 끝나면 롤백된다.
 *       시드: learnerB = 대기열에서 확정된 수강 e1(강의 1 색연필화 기초, 3차시 중 1·2차시 완료·3차시 재생 중, 과제 미제출)
 *             · 평가 대기 e2(강의 4) · 수료 e3(강의 8) / learnerC = 평가 중 e4(강의 4) + 대기 1번(강의 1) / learnerA·newbie = 기록 없음
 * 반려·평가 중 같은 상태 규칙 자체는 LearningStateTest가 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class MyLearningServiceTest {

    @Autowired
    private MyLearningService myLearningService;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    @Test
    @DisplayName("본인 기록만 나온다 — learnerB는 자기 수강 3건, learnerC는 자기 수강 1건 + 대기 1건")
    void onlyOwnRecords() {
        List<MyCourseDTO> learnerB = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL);
        List<MyCourseDTO> learnerC = myLearningService.getMyCourses(userId("learnerC"), LearningFilter.ALL);

        assertThat(learnerB).extracting(MyCourseDTO::getEnrollmentId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(learnerC).extracting(MyCourseDTO::getEnrollmentId).containsExactlyInAnyOrder(null, 4L);  // 대기 카드는 수강 id가 없다
        assertThat(learnerB).extracting(MyCourseDTO::getEnrollmentId).doesNotContain(4L);
    }

    @Test
    @DisplayName("진행 중·대기 카드가 앞, 끝난(수료) 카드가 뒤")
    void ongoingCardsFirst() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL);

        assertThat(cards.get(cards.size() - 1).getState()).isEqualTo(LearningState.COMPLETED);
        assertThat(cards.subList(0, cards.size() - 1)).allSatisfy(card -> assertThat(card.isActive()).isTrue());
    }

    @Test
    @DisplayName("대기열에서 자동 확정된 수강은 대기 신청 시각이 붙고, 한 차시라도 끝냈으면 학습 중으로 나온다")
    void waitlistOriginCard() {
        MyCourseDTO card = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL).stream()
                .filter(c -> c.getEnrollmentId().equals(1L)).findFirst().orElseThrow();

        assertThat(card.getWaitlistAppliedAt()).isNotNull();
        // 시드: 수강 1은 대기열 출신(w1)이지만 3차시 중 1·2차시(id 3·2)를 이미 완료했고 과제는 아직 안 냈다 → "자동 확정 안내"가 아니라 학습 중.
        // 완료 차시가 없는 대기열 출신 수강만 AUTO_CONFIRMED가 된다 — 그 규칙은 LearningStateTest가 확인한다.
        assertThat(card.getCompletedLessonCount()).isEqualTo(2);
        assertThat(card.getState()).isEqualTo(LearningState.LEARNING);
        assertThat(card.getSectionName()).isEqualTo("A분반");
        assertThat(card.getCourseTitle()).isEqualTo("색연필화 기초");
        assertThat(card.getLevelLabel()).isEqualTo("입문");
    }

    @Test
    @DisplayName("대기 카드는 대기 번호·waitlistId를 가지고 수강 id는 없다 (learnerC: 강의 1 첫 대기)")
    void waitingCard() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learnerC"), LearningFilter.WAITING);

        assertThat(cards).hasSize(1);
        MyCourseDTO waiting = cards.get(0);
        assertThat(waiting.getState()).isEqualTo(LearningState.WAITING);
        assertThat(waiting.getWaitingNumber()).isEqualTo(1);
        assertThat(waiting.getWaitlistId()).isNotNull();
        assertThat(waiting.getEnrollmentId()).isNull();
        assertThat(waiting.getCourseTitle()).isEqualTo("색연필화 기초");
    }

    @Test
    @DisplayName("진행 중 카드는 남은 기간(1~30일)이 있고, 수료 카드는 남은 기간이 없다")
    void remainingDays() {
        List<MyCourseDTO> cards = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL);

        MyCourseDTO active = cards.stream().filter(c -> c.getEnrollmentId().equals(1L)).findFirst().orElseThrow();
        assertThat(active.getRemainingDays()).isBetween(0, 30);
        assertThat(active.getRemainingLabel()).startsWith("D-");

        // 시드: 수강 3은 수료 뒤 30일 수강 기간도 지났지만, 수료 카드라 남은 기간 자체를 보이지 않는다
        MyCourseDTO completed = cards.stream().filter(c -> c.getEnrollmentId().equals(3L)).findFirst().orElseThrow();
        assertThat(completed.getState()).isEqualTo(LearningState.COMPLETED);
        assertThat(completed.getRemainingDays()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("탭 필터: 수료는 1건, 만료·취소는 0건, 요약 숫자는 탭과 상관없이 같다")
    void filterAndSummary() {
        Long learner = userId("learnerB");

        MyLearningDTO completed = myLearningService.getMyLearning(learner, LearningFilter.COMPLETED);
        MyLearningDTO ended = myLearningService.getMyLearning(learner, LearningFilter.ENDED);
        MyLearningDTO all = myLearningService.getMyLearning(learner, LearningFilter.ALL);

        assertThat(completed.getCourses()).hasSize(1);
        assertThat(ended.getCourses()).isEmpty();
        assertThat(all.getCourses()).hasSize(3);
        assertThat(completed.getCompletedCount()).isEqualTo(all.getCompletedCount()).isEqualTo(1);
        assertThat(completed.getLearningCount()).isEqualTo(all.getLearningCount());
    }

    @Test
    @DisplayName("차시 칸에는 재생 기록이 없는 차시도 0%·미완료로 포함되어 항상 3차시가 보인다 (수강 1: 완료·완료·기록 없음)")
    void everyLessonIsListed() {
        // 시드의 수강 건은 모두 3개 차시에 기록이 있다 → 수강 1의 3차시(id 1) 재생 기록을 지워 "기록 없는 차시"를 만든다 (롤백됨)
        entityManager.createNativeQuery("DELETE FROM lesson_progresses WHERE enrollment_id = 1 AND lesson_id = 1").executeUpdate();
        entityManager.clear();

        MyCourseDTO card = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL).stream()
                .filter(c -> c.getEnrollmentId().equals(1L)).findFirst().orElseThrow();

        assertThat(card.getLessons()).extracting(lesson -> lesson.getLessonNo()).containsExactly(1, 2, 3);
        assertThat(card.getLessons()).extracting(lesson -> lesson.isCompleted()).containsExactly(true, true, false);
        assertThat(card.getLessons().get(2).getPercent()).isZero();  // 3차시는 재생 기록이 없다
    }

    @Test
    @DisplayName("이어서 학습은 처음으로 안 끝낸 차시를 연다 — 수강 1은 3차시(id 1), 막 확정돼 기록이 하나도 없는 수강은 1차시(강의 4 → id 12)")
    void continueLessonCard() {
        // 시드: 수강 1은 1·2차시(id 3·2) 완료, 3차시(id 1)는 재생 중(미완료)
        MyCourseDTO partway = myLearningService.getMyCourses(userId("learnerB"), LearningFilter.ALL).stream()
                .filter(c -> c.getEnrollmentId().equals(1L)).findFirst().orElseThrow();
        assertThat(partway.getContinueLessonId()).isEqualTo(1L);

        // 시드에는 진도 기록이 하나도 없는 수강이 없다 → learnerA를 강의 4 A분반(남은 자리 있음)에 막 확정된 수강으로 넣는다 (롤백됨)
        Long learnerA = userId("learnerA");
        entityManager.createNativeQuery("INSERT INTO enrollments (user_id, course_id, section_id, status, confirmed_at, expires_at) "
                + "VALUES (" + learnerA + ", 4, 4, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 30 DAY)").executeUpdate();
        entityManager.clear();

        // 이전에는 진도 기록이 없으면 이어서 학습 버튼이 비활성이었다(차시를 고를 목록이 비어서)
        MyCourseDTO brandNew = myLearningService.getMyCourses(learnerA, LearningFilter.ALL).stream()
                .filter(c -> c.getEnrollmentId() != null && c.getCourseId().equals(4L)).findFirst().orElseThrow();
        assertThat(brandNew.getContinueLessonId()).isEqualTo(12L);
        assertThat(brandNew.getLessons()).hasSize(3).allSatisfy(lesson -> assertThat(lesson.isCompleted()).isFalse());
    }

    @Test
    @DisplayName("기록이 없는 학습자는 빈 목록이다 (newbie)")
    void noRecords() {
        MyLearningDTO newbie = myLearningService.getMyLearning(userId("newbie"), LearningFilter.ALL);

        assertThat(newbie.getCourses()).isEmpty();
        assertThat(newbie.getLearningCount()).isZero();
    }
}
