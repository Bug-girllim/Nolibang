package com.wanted.lmspractice.domain.learning.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] [이어서 학습]이 열 차시를 고르는 규칙 테스트 — DB 없이 순수 계산만 확인한다
 * [담당] 팀원 2 · #11 후속
 * 차시 id는 lesson_no 순서와 다르다(시드: 1차시=6, 2차시=5, 3차시=4)는 점을 일부러 반영해, id 크기로 고르는 실수를 잡는다.
 */
class ContinueLessonPolicyTest {

    /** 시드 course 2처럼 id가 차시 순서의 역순인 차시 목록 (1차시=6, 2차시=5, 3차시=4) */
    private static final List<Long> ORDERED = List.of(6L, 5L, 4L);

    private LessonProgressDTO progress(long lessonId, int lessonNo, boolean completed) {
        return new LessonProgressDTO(lessonId, lessonNo, "차시", completed ? 100 : 25, null, completed);
    }

    @Test
    @DisplayName("재생 기록이 하나도 없으면(새로 확정된 수강) 1차시를 연다")
    void noProgressOpensFirstLesson() {
        assertThat(ContinueLessonPolicy.pick(ORDERED, List.of())).isEqualTo(6L);
    }

    @Test
    @DisplayName("1차시를 끝냈으면 2차시, 2차시도 끝냈으면 3차시")
    void firstUnfinishedLesson() {
        assertThat(ContinueLessonPolicy.pick(ORDERED, List.of(progress(6, 1, true)))).isEqualTo(5L);
        assertThat(ContinueLessonPolicy.pick(ORDERED, List.of(progress(6, 1, true), progress(5, 2, true)))).isEqualTo(4L);
    }

    @Test
    @DisplayName("재생 기록이 없는 앞 차시를 건너뛰지 않는다 — 2차시만 끝냈어도 1차시부터")
    void doesNotSkipUnplayedLesson() {
        // 진도 계약(C10)은 재생 기록이 있는 차시만 주므로, 2차시 기록만 있어도 아직 안 본 1차시가 먼저다
        assertThat(ContinueLessonPolicy.pick(ORDERED, List.of(progress(5, 2, true)))).isEqualTo(6L);
    }

    @Test
    @DisplayName("재생 중(미완료)인 차시는 끝낸 것이 아니므로 그 차시를 다시 연다")
    void inProgressLessonIsNotCompleted() {
        assertThat(ContinueLessonPolicy.pick(ORDERED, List.of(progress(6, 1, true), progress(5, 2, false)))).isEqualTo(5L);
    }

    @Test
    @DisplayName("3차시를 모두 끝냈으면 마지막 차시를 연다 (수료 후 다시 보기)")
    void allCompletedOpensLast() {
        List<LessonProgressDTO> all = List.of(progress(6, 1, true), progress(5, 2, true), progress(4, 3, true));
        assertThat(ContinueLessonPolicy.pick(ORDERED, all)).isEqualTo(4L);
    }

    @Test
    @DisplayName("강의에 차시가 없으면 null")
    void noLessonsReturnsNull() {
        assertThat(ContinueLessonPolicy.pick(List.of(), List.of())).isNull();
    }
}
