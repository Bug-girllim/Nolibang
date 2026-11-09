package com.wanted.lmspractice.domain.work.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] WorkAccessPolicy 단위 테스트 — 공개/비공개/관리자 숨김 × 작성자·다른 학습자·담당 강사·다른 강사·관리자·비로그인
 * [담당] 팀원 4 · #16   [전제] 스프링·DB 없이 SectionService 만 mock 으로 대체한다(담당 강사 판단은 #10 구현 전이라 스텁).
 * 🟡 미학습 기술: Mockito mock, ReflectionTestUtils (공개·숨김 상태를 바꾸는 메서드는 #18·#20 소관이라 필드를 직접 세팅)
 */
class WorkAccessPolicyTest {

    private static final Long AUTHOR = 2L, OTHER_LEARNER = 3L, ASSIGNED = 6L, OTHER_INSTRUCTOR = 7L, ADMIN = 1L;
    private static final Long ENROLLMENT = 10L;

    private WorkAccessPolicy policy;

    @BeforeEach
    void setUp() {
        SectionService sectionService = mock(SectionService.class);
        when(sectionService.getCurrentInstructorId(ENROLLMENT)).thenReturn(ASSIGNED);
        policy = new WorkAccessPolicy(sectionService);
    }

    private Work work(Visibility visibility, boolean hidden) {
        Work work = Work.createFinal(AUTHOR, ENROLLMENT, 1L, CourseLevel.BASIC);
        ReflectionTestUtils.setField(work, "visibility", visibility);
        ReflectionTestUtils.setField(work, "adminHidden", hidden);
        return work;
    }

    @Test
    @DisplayName("공개 + 숨김 아님: 로그인한 누구나 볼 수 있고, 비로그인은 못 본다")
    void publicWork() {
        Work work = work(Visibility.PUBLIC, false);

        assertThat(policy.canView(work, OTHER_LEARNER, Role.USER)).isTrue();
        assertThat(policy.canView(work, OTHER_INSTRUCTOR, Role.INSTRUCTOR)).isTrue();
        assertThat(policy.canView(work, null, null)).isFalse();
    }

    @Test
    @DisplayName("비공개: 작성자·현재 담당 강사·관리자만 — 다른 학습자·담당 외 강사는 403")
    void privateWork() {
        Work work = work(Visibility.PRIVATE, false);

        assertThat(policy.canView(work, AUTHOR, Role.USER)).isTrue();
        assertThat(policy.canView(work, ASSIGNED, Role.INSTRUCTOR)).isTrue();
        assertThat(policy.canView(work, ADMIN, Role.ADMIN)).isTrue();
        assertThat(policy.canView(work, OTHER_LEARNER, Role.USER)).isFalse();
        assertThat(policy.canView(work, OTHER_INSTRUCTOR, Role.INSTRUCTOR)).isFalse();
        assertThat(policy.canView(work, null, null)).isFalse();
        assertThatThrownBy(() -> policy.checkCanView(work, OTHER_LEARNER, Role.USER))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("관리자 숨김: 공개 작품이어도 일반 사용자는 막고, 작성자·담당 강사·관리자는 허용한다")
    void hiddenWork() {
        Work work = work(Visibility.PUBLIC, true);

        assertThat(policy.canView(work, OTHER_LEARNER, Role.USER)).isFalse();
        assertThat(policy.canView(work, AUTHOR, Role.USER)).isTrue();
        assertThat(policy.canView(work, ASSIGNED, Role.INSTRUCTOR)).isTrue();
        assertThat(policy.canView(work, ADMIN, Role.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("삭제된 작품은 작성자도 볼 수 없다")
    void deletedWork() {
        Work work = work(Visibility.PUBLIC, false);
        ReflectionTestUtils.setField(work, "deletedAt", LocalDateTime.now());

        assertThat(policy.canView(work, AUTHOR, Role.USER)).isFalse();
        assertThat(policy.canView(work, ADMIN, Role.ADMIN)).isFalse();
    }
}
