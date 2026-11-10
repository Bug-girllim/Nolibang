package com.wanted.lmspractice.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] BadgeService 테스트 — 수준 배지, 마스터 배지, 수료 전(진행·취소) 배지 없음, C12 세 메서드
 * [담당] 팀원 3 · #15
 * [전제] 수강·강의 서비스는 Mockito 가짜로 대신한다(DB 없이 계산 규칙만 검사).
 */
class BadgeServiceTest {

    private static final Long USER = 2L;
    private EnrollmentService enrollmentService;
    private CourseService courseService;
    private BadgeService service;

    @BeforeEach
    void setUp() {
        enrollmentService = mock(EnrollmentService.class);
        courseService = mock(CourseService.class);
        service = new BadgeService(enrollmentService, courseService);
    }

    private MyEnrollmentDTO enrollment(long courseId, EnrollmentStatus status) {
        return new MyEnrollmentDTO(courseId * 10, courseId, status, "A분반", null, null, null, null, null);
    }

    private CourseBriefDTO course(long courseId, long hobbyId, CourseLevel level) {
        return new CourseBriefDTO(courseId, hobbyId, "취미" + hobbyId, true, level, "강의" + courseId);
    }

    private void given(List<MyEnrollmentDTO> enrollments, CourseBriefDTO... courses) {
        // [성능 개선] BadgeService는 수료한 강의 id만 받는다(EnrollmentService.getCompletedCourseIds) — 같은 입력에서 COMPLETED만 넘긴다
        when(enrollmentService.getCompletedCourseIds(USER)).thenReturn(enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED).map(MyEnrollmentDTO::getCourseId).toList());
        Map<Long, CourseBriefDTO> map = new HashMap<>();
        for (CourseBriefDTO c : courses) {
            map.put(c.getCourseId(), c);
        }
        when(courseService.getCourseBriefs(anyCollection())).thenReturn(map);
    }

    @Test
    @DisplayName("수료한 강의의 취미·수준 배지를 계산한다")
    void levelBadge() {
        given(List.of(enrollment(1, EnrollmentStatus.COMPLETED)), course(1, 1, CourseLevel.BASIC));

        List<BadgeDTO> badges = service.getBadges(USER);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLevel()).isEqualTo(CourseLevel.BASIC);
        assertThat(badges.get(0).isMaster()).isFalse();
        assertThat(badges.get(0).getLabel()).isEqualTo("취미1 입문");
    }

    @Test
    @DisplayName("한 취미의 입문·중급·고급을 모두 수료하면 마스터 배지가 더해진다")
    void masterBadge() {
        given(List.of(enrollment(1, EnrollmentStatus.COMPLETED), enrollment(2, EnrollmentStatus.COMPLETED),
                        enrollment(3, EnrollmentStatus.COMPLETED)),
                course(1, 1, CourseLevel.BASIC), course(2, 1, CourseLevel.INTERMEDIATE), course(3, 1, CourseLevel.ADVANCED));

        List<BadgeDTO> badges = service.getBadges(USER);

        assertThat(badges).hasSize(4);
        assertThat(badges.stream().filter(BadgeDTO::isMaster).count()).isEqualTo(1);
        assertThat(service.getHighestLevel(USER, 1L)).isEqualTo(CourseLevel.ADVANCED);
    }

    @Test
    @DisplayName("두 수준만 수료하면 마스터가 아니다")
    void notMasterWithTwoLevels() {
        given(List.of(enrollment(1, EnrollmentStatus.COMPLETED), enrollment(2, EnrollmentStatus.COMPLETED)),
                course(1, 1, CourseLevel.BASIC), course(2, 1, CourseLevel.ADVANCED));

        assertThat(service.getBadges(USER)).noneMatch(BadgeDTO::isMaster);
    }

    @Test
    @DisplayName("다른 취미의 수준은 마스터로 합쳐지지 않는다")
    void masterIsPerHobby() {
        given(List.of(enrollment(1, EnrollmentStatus.COMPLETED), enrollment(2, EnrollmentStatus.COMPLETED),
                        enrollment(3, EnrollmentStatus.COMPLETED)),
                course(1, 1, CourseLevel.BASIC), course(2, 2, CourseLevel.INTERMEDIATE), course(3, 1, CourseLevel.ADVANCED));

        assertThat(service.getBadges(USER)).noneMatch(BadgeDTO::isMaster);
        assertThat(service.getHighestLevel(USER, 2L)).isEqualTo(CourseLevel.INTERMEDIATE);
    }

    @Test
    @DisplayName("평가 대기 중(진행)·취소·만료 수강은 배지가 없다")
    void notCompletedHasNoBadge() {
        given(List.of(
                enrollment(1, EnrollmentStatus.ACTIVE), enrollment(2, EnrollmentStatus.CANCELLED),
                enrollment(3, EnrollmentStatus.EXPIRED)));

        assertThat(service.getBadges(USER)).isEmpty();
        assertThat(service.getHighestLevel(USER, 1L)).isNull();
        assertThat(service.hasBadge(USER, 1L, CourseLevel.BASIC)).isFalse();
    }

    @Test
    @DisplayName("hasBadge는 같은 취미·같은 수준만 true")
    void hasBadgeMatchesHobbyAndLevel() {
        given(List.of(enrollment(1, EnrollmentStatus.COMPLETED)), course(1, 1, CourseLevel.INTERMEDIATE));

        assertThat(service.hasBadge(USER, 1L, CourseLevel.INTERMEDIATE)).isTrue();
        assertThat(service.hasBadge(USER, 1L, CourseLevel.BASIC)).isFalse();
        assertThat(service.hasBadge(USER, 2L, CourseLevel.INTERMEDIATE)).isFalse();
    }
}
