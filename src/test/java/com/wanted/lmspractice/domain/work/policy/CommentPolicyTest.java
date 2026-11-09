package com.wanted.lmspractice.domain.work.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.service.BadgeService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] CommentPolicy 단위 테스트 — 배지 수준별 댓글 권한, 취미 권한 강사, 작성자 답글 예외, 관리자 불가
 * [담당] 팀원 4 · #18   [전제] 스프링·DB 없이 BadgeService·InstructorAccountService 만 mock 으로 대체한다.
 * 🟡 미학습 기술: Mockito mock (배지·강사 권한 상태를 만들기 위한 테스트 전용 사용)
 */
class CommentPolicyTest {

    private static final Long HOBBY = 1L, OTHER_HOBBY = 2L;
    private static final Long AUTHOR = 10L, BASIC_USER = 11L, MID_USER = 12L, ADV_USER = 13L, NO_BADGE = 14L, OTHER_HOBBY_USER = 15L;
    private static final Long GRANTED = 20L, NOT_GRANTED = 21L, ADMIN = 1L;

    private CommentPolicy policy;

    @BeforeEach
    void setUp() {
        BadgeService badges = mock(BadgeService.class);
        when(badges.getHighestLevel(BASIC_USER, HOBBY)).thenReturn(CourseLevel.BASIC);
        when(badges.getHighestLevel(MID_USER, HOBBY)).thenReturn(CourseLevel.INTERMEDIATE);
        when(badges.getHighestLevel(ADV_USER, HOBBY)).thenReturn(CourseLevel.ADVANCED);
        when(badges.getHighestLevel(OTHER_HOBBY_USER, OTHER_HOBBY)).thenReturn(CourseLevel.ADVANCED);   // 다른 취미 배지는 소용없다
        InstructorAccountService instructors = mock(InstructorAccountService.class);
        when(instructors.hasHobbyGrant(GRANTED, HOBBY)).thenReturn(true);
        policy = new CommentPolicy(badges, instructors);
    }

    private Work work(CourseLevel level) {
        return Work.createFinal(AUTHOR, 100L, HOBBY, level);
    }

    @Test
    @DisplayName("입문 배지는 입문 작품만, 중급은 입문·중급, 고급은 전체에 댓글을 쓴다")
    void badgeLevels() {
        assertThat(policy.canWriteComment(work(CourseLevel.BASIC), BASIC_USER, Role.USER)).isTrue();
        assertThat(policy.canWriteComment(work(CourseLevel.INTERMEDIATE), BASIC_USER, Role.USER)).isFalse();
        assertThat(policy.canWriteComment(work(CourseLevel.ADVANCED), BASIC_USER, Role.USER)).isFalse();

        assertThat(policy.canWriteComment(work(CourseLevel.BASIC), MID_USER, Role.USER)).isTrue();
        assertThat(policy.canWriteComment(work(CourseLevel.INTERMEDIATE), MID_USER, Role.USER)).isTrue();
        assertThat(policy.canWriteComment(work(CourseLevel.ADVANCED), MID_USER, Role.USER)).isFalse();

        assertThat(policy.canWriteComment(work(CourseLevel.ADVANCED), ADV_USER, Role.USER)).isTrue();
    }

    @Test
    @DisplayName("배지가 없거나 다른 취미 배지뿐이면 댓글도 답글도 못 쓴다")
    void noBadge() {
        Work work = work(CourseLevel.BASIC);

        assertThat(policy.canWriteComment(work, NO_BADGE, Role.USER)).isFalse();
        assertThat(policy.canWriteComment(work, OTHER_HOBBY_USER, Role.USER)).isFalse();
        assertThat(policy.canWriteReply(work, NO_BADGE, Role.USER)).isFalse();
        assertThatThrownBy(() -> policy.checkCanWrite(work, NO_BADGE, Role.USER, false))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("작품 작성자는 배지가 없어도 답글은 쓸 수 있지만, 새 댓글은 배지 규칙을 따른다")
    void authorReplyException() {
        Work work = work(CourseLevel.ADVANCED);

        assertThat(policy.canWriteReply(work, AUTHOR, Role.USER)).isTrue();
        assertThat(policy.canWriteComment(work, AUTHOR, Role.USER)).isFalse();
        policy.checkCanWrite(work, AUTHOR, Role.USER, true);   // 예외 없음
        assertThatThrownBy(() -> policy.checkCanWrite(work, AUTHOR, Role.USER, false))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("강사는 해당 취미 권한이 있을 때만 댓글·답글을 쓸 수 있다 (수준과 무관)")
    void instructorGrant() {
        Work work = work(CourseLevel.ADVANCED);

        assertThat(policy.canWriteComment(work, GRANTED, Role.INSTRUCTOR)).isTrue();
        assertThat(policy.canWriteReply(work, GRANTED, Role.INSTRUCTOR)).isTrue();
        assertThat(policy.canWriteComment(work, NOT_GRANTED, Role.INSTRUCTOR)).isFalse();
        assertThat(policy.canWriteReply(work, NOT_GRANTED, Role.INSTRUCTOR)).isFalse();
    }

    @Test
    @DisplayName("관리자는 댓글을 쓰지 않는다")
    void adminNever() {
        Work work = work(CourseLevel.BASIC);

        assertThat(policy.canWriteComment(work, ADMIN, Role.ADMIN)).isFalse();
        assertThat(policy.canWriteReply(work, ADMIN, Role.ADMIN)).isFalse();
    }
}
