package com.wanted.lmspractice.domain.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.dto.ProgressReportDTO;
import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import com.wanted.lmspractice.domain.learning.policy.VideoAccessPolicy;
import com.wanted.lmspractice.domain.learning.repository.LessonProgressRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] ProgressService 흐름 테스트 — 본인 확인 위임, 다른 강의 차시 차단, 기간 만료 거부, 수료자 진도 불변, 3차시 완료 판정(C10)
 * [담당] 팀원 3 · #12
 * [전제] 의존하는 서비스·Repository는 Mockito 가짜로 대신한다. 수강 조회(C6, #09)가 아직 스텁이라 DB 연결 테스트는 #09 merge 후에 가능하다.
 *       누적 계산 자체는 LessonProgressTest가 검증한다.
 */
class ProgressServiceTest {

    private LessonProgressRepository repository;
    private EnrollmentService enrollmentService;
    private CourseService courseService;
    private ProgressService service;

    @BeforeEach
    void setUp() {
        repository = mock(LessonProgressRepository.class);
        enrollmentService = mock(EnrollmentService.class);
        courseService = mock(CourseService.class);
        service = new ProgressService(repository, enrollmentService, courseService, new VideoAccessPolicy());
    }

    private Enrollment enrollment(EnrollmentStatus status, LocalDateTime expiresAt) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "userId", 2L);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        ReflectionTestUtils.setField(e, "confirmedAt", LocalDateTime.now().minusDays(5));
        ReflectionTestUtils.setField(e, "expiresAt", expiresAt);
        return e;
    }

    private Lesson lesson(Long courseId) {
        Lesson l = new Lesson();
        ReflectionTestUtils.setField(l, "id", 100L);
        ReflectionTestUtils.setField(l, "courseId", courseId);
        ReflectionTestUtils.setField(l, "lessonNo", 1);
        ReflectionTestUtils.setField(l, "title", "1차시");
        ReflectionTestUtils.setField(l, "durationSec", new BigDecimal("600"));
        ReflectionTestUtils.setField(l, "videoKey", "videos/course-1/lesson-1.mp4");
        return l;
    }

    private ProgressReportDTO report(String key, long seq, boolean playing, String position) {
        return new ProgressReportDTO(10L, key, seq, playing, new BigDecimal(position));
    }

    private void stub(Enrollment e, Lesson l) {
        when(enrollmentService.getOwnedEnrollment(2L, 10L)).thenReturn(e);
        when(courseService.getLesson(100L)).thenReturn(l);
    }

    @Test
    @DisplayName("본인 확인은 수강 계약(getOwnedEnrollment)에 맡기고, 남의 수강 id면 그 예외가 그대로 나간다")
    void notOwnedEnrollmentPropagates() {
        when(enrollmentService.getOwnedEnrollment(2L, 10L))
                .thenThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        assertThatThrownBy(() -> service.report(2L, 100L, report("s1", 1, true, "0")))
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("수강한 강의가 아닌 차시 id로 보고하면 LESSON_NOT_FOUND")
    void lessonOfOtherCourseRejected() {
        stub(enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().plusDays(25)), lesson(99L));

        assertThatThrownBy(() -> service.report(2L, 100L, report("s1", 1, true, "0")))
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.LESSON_NOT_FOUND);
    }

    @Test
    @DisplayName("기간이 끝난 미수료 수강은 진도를 적립하지 못한다")
    void expiredEnrollmentRejected() {
        stub(enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().minusDays(1)), lesson(1L));
        when(repository.findForUpdate(10L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(2L, 100L, report("s1", 1, true, "0")))
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_EXPIRED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("수료자가 다시 봐도 진도는 바뀌지 않는다")
    void completedEnrollmentProgressUntouched() {
        stub(enrollment(EnrollmentStatus.COMPLETED, LocalDateTime.now().minusDays(1)), lesson(1L));
        LessonProgress existing = LessonProgress.create(10L, 100L, 1L);
        when(repository.findForUpdate(10L, 100L)).thenReturn(Optional.of(existing));

        LessonProgressDTO result = service.report(2L, 100L, report("s1", 1, true, "0"));

        assertThat(existing.getLastPingSeq()).isZero();
        assertThat(existing.getPlaybackStreamKey()).isNull();
        assertThat(result.getPercent()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("처음 재생하면 진도 행을 만들고 기준점을 기록한다")
    void firstReportCreatesRow() {
        stub(enrollment(EnrollmentStatus.ACTIVE, LocalDateTime.now().plusDays(25)), lesson(1L));
        when(repository.findForUpdate(10L, 100L)).thenReturn(Optional.empty());
        when(repository.save(any(LessonProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        LessonProgressDTO result = service.report(2L, 100L, report("s1", 1, true, "0"));

        assertThat(result.getPercent()).isZero();
        assertThat(result.isCompleted()).isFalse();
        verify(repository).save(any(LessonProgress.class));
    }

    @Test
    @DisplayName("3차시를 모두 완료해야 isAllCompleted가 true (C10)")
    void allCompletedNeedsThree() {
        when(repository.countByEnrollmentIdAndCompletedAtIsNotNull(10L)).thenReturn(2L, 3L);

        assertThat(service.isAllCompleted(10L)).isFalse();
        assertThat(service.isAllCompleted(10L)).isTrue();
    }

    @Test
    @DisplayName("재생한 적 없는 수강의 차시별 진도는 빈 목록 (C10)")
    void lessonStatusesEmptyWhenNoProgress() {
        when(repository.findByEnrollmentId(10L)).thenReturn(java.util.List.of());
        assertThat(service.getLessonStatuses(10L)).isEmpty();
    }
}
