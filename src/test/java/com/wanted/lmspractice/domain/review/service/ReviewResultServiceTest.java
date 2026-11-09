package com.wanted.lmspractice.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDetailDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] ReviewResultService 테스트 — 승인·반려 결과, 평가 대기 중 차단, 다른 사람 결과 접근, 재제출 가능 여부, 승인 배지
 * [담당] 팀원 3 · #15
 * [전제] 의존 서비스·Repository는 Mockito 가짜로 대신한다(화면 렌더링·DB 흐름은 별도 확인 필요).
 */
class ReviewResultServiceTest {

    private ReviewRoundRepository repository;
    private EnrollmentService enrollmentService;
    private CourseService courseService;
    private BadgeService badgeService;
    private InstructorAccountService instructorService;
    private ReviewResultService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReviewRoundRepository.class);
        enrollmentService = mock(EnrollmentService.class);
        courseService = mock(CourseService.class);
        badgeService = mock(BadgeService.class);
        instructorService = mock(InstructorAccountService.class);
        service = new ReviewResultService(repository, enrollmentService, courseService, badgeService, instructorService);

        when(courseService.getCourseBriefs(anyCollection())).thenReturn(
                Map.of(1L, new CourseBriefDTO(1L, 7L, "색연필화", true, CourseLevel.INTERMEDIATE, "색연필화 응용")));
        when(courseService.getLessonsOfCourse(1L)).thenReturn(List.of());
        when(enrollmentService.getMyEnrollments(2L)).thenReturn(List.of());
        InstructorDTO instructor = new InstructorDTO();
        ReflectionTestUtils.setField(instructor, "realName", "강사 A");
        when(instructorService.getInstructor(6L)).thenReturn(instructor);
    }

    private Enrollment enrollment(EnrollmentStatus status, LocalDateTime expiresAt) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "userId", 2L);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        ReflectionTestUtils.setField(e, "confirmedAt", LocalDateTime.now().minusDays(20));
        ReflectionTestUtils.setField(e, "expiresAt", expiresAt);
        return e;
    }

    private ReviewRound round(long id, int no, ReviewStatus status) {
        ReviewRound r = new ReviewRound();
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "enrollmentId", 10L);
        ReflectionTestUtils.setField(r, "roundNo", no);
        ReflectionTestUtils.setField(r, "status", status);
        ReflectionTestUtils.setField(r, "submittedAt", LocalDateTime.now().minusDays(8));
        ReflectionTestUtils.setField(r, "dueAt", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(r, "feedbackBody", "피드백 원문");
        ReflectionTestUtils.setField(r, "reviewerId", 6L);
        if (status == ReviewStatus.APPROVED || status == ReviewStatus.REJECTED) {
            ReflectionTestUtils.setField(r, "finalizedAt", LocalDateTime.now().minusDays(1));
        }
        return r;
    }

    private void given(ReviewRound r, Enrollment e, ReviewRound... all) {
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        when(enrollmentService.getOwnedEnrollment(2L, 10L)).thenReturn(e);
        when(repository.findByEnrollmentIdOrderByRoundNoAsc(10L)).thenReturn(List.of(all));
    }

    @Test
    @DisplayName("승인 결과: 피드백·강사·수료일과 이 강의로 얻은 배지를 보여 준다")
    void approvedShowsFeedbackAndBadges() {
        ReviewRound r = round(1, 1, ReviewStatus.APPROVED);
        given(r, enrollment(EnrollmentStatus.COMPLETED, LocalDateTime.now().plusDays(5)), r);
        when(badgeService.getBadges(2L)).thenReturn(List.of(
                BadgeDTO.levelBadge(7L, "색연필화", CourseLevel.BASIC),
                BadgeDTO.levelBadge(7L, "색연필화", CourseLevel.INTERMEDIATE),
                BadgeDTO.levelBadge(8L, "수채화", CourseLevel.INTERMEDIATE)));

        ReviewDetailDTO dto = service.getResult(2L, 1L);

        assertThat(dto.isApproved()).isTrue();
        assertThat(dto.getFeedbackBody()).isEqualTo("피드백 원문");
        assertThat(dto.getInstructorName()).isEqualTo("강사 A");
        assertThat(dto.getFinalizedAt()).isNotNull();
        assertThat(dto.isCanResubmit()).isFalse();
        // 같은 취미·이 강의 수준의 배지만 (입문 배지·다른 취미 배지는 제외)
        assertThat(dto.getBadges()).hasSize(1);
        assertThat(dto.getBadges().get(0).getLevel()).isEqualTo(CourseLevel.INTERMEDIATE);
    }

    @Test
    @DisplayName("승인으로 세 수준을 모두 채우면 마스터 배지도 함께 보인다")
    void approvedShowsMasterBadge() {
        ReviewRound r = round(1, 1, ReviewStatus.APPROVED);
        given(r, enrollment(EnrollmentStatus.COMPLETED, LocalDateTime.now().plusDays(5)), r);
        when(badgeService.getBadges(2L)).thenReturn(List.of(
                BadgeDTO.levelBadge(7L, "색연필화", CourseLevel.BASIC),
                BadgeDTO.levelBadge(7L, "색연필화", CourseLevel.INTERMEDIATE),
                BadgeDTO.levelBadge(7L, "색연필화", CourseLevel.ADVANCED),
                BadgeDTO.masterBadge(7L, "색연필화")));

        assertThat(service.getResult(2L, 1L).getBadges()).anyMatch(BadgeDTO::isMaster);
    }

    @Test
    @DisplayName("반려 결과: 사유 원문과 재제출 링크(기간 내 최신 회차)")
    void rejectedWithinPeriodCanResubmit() {
        ReviewRound r = round(1, 1, ReviewStatus.REJECTED);
        given(r, enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().plusDays(10)), r);

        ReviewDetailDTO dto = service.getResult(2L, 1L);

        assertThat(dto.isRejected()).isTrue();
        assertThat(dto.getFeedbackBody()).isEqualTo("피드백 원문");
        assertThat(dto.isCanResubmit()).isTrue();
        assertThat(dto.getRemainingDays()).isBetween(9L, 10L);
        assertThat(dto.getBadges()).isEmpty();
    }

    @Test
    @DisplayName("반려라도 기간이 끝났거나 이미 재제출한(최신이 아닌) 회차는 재제출 링크가 없다")
    void rejectedCannotResubmitWhenExpiredOrNotLatest() {
        ReviewRound r1 = round(1, 1, ReviewStatus.REJECTED);
        ReviewRound r2 = round(2, 2, ReviewStatus.WAITING);
        given(r1, enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().plusDays(10)), r1, r2);
        assertThat(service.getResult(2L, 1L).isCanResubmit()).isFalse();

        ReviewRound r3 = round(3, 1, ReviewStatus.REJECTED);
        given(r3, enrollment(EnrollmentStatus.EXPIRED, LocalDateTime.now().minusDays(1)), r3);
        assertThat(service.getResult(2L, 3L).isCanResubmit()).isFalse();
    }

    @Test
    @DisplayName("평가 대기·진행 중에는 결과가 없다 (INVALID_REVIEW_STATE)")
    void notFinalizedRejected() {
        for (ReviewStatus s : List.of(ReviewStatus.WAITING, ReviewStatus.IN_REVIEW)) {
            ReviewRound r = round(1, 1, s);
            given(r, enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().plusDays(10)), r);
            assertThatThrownBy(() -> service.getResult(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.INVALID_REVIEW_STATE);
        }
    }

    @Test
    @DisplayName("다른 사람의 결과 URL이나 없는 평가는 REVIEW_NOT_FOUND로 존재를 숨긴다")
    void othersResultHidden() {
        ReviewRound r = round(1, 1, ReviewStatus.APPROVED);
        when(repository.findById(1L)).thenReturn(Optional.of(r));
        when(enrollmentService.getOwnedEnrollment(99L, 10L))
                .thenThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(99L, 1L))
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        assertThatThrownBy(() -> service.getResult(2L, 404L))
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }
}
