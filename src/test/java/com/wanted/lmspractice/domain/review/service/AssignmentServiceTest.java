package com.wanted.lmspractice.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.learning.policy.VideoAccessPolicy;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.dto.AssignmentSubmitDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.policy.SubmissionPolicy;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] AssignmentService 흐름 테스트 — 최초 제출(평가 대기·새 7일·알림), 평가 전 수정(같은 버전·마감 불변),
 *        반려 후 재제출(새 버전·round_no + 1·새 7일), 실패 시 회차를 저장하지 않음
 * [담당] 팀원 3 · #13
 * [전제] 작품 저장(C8 #16)·담당 강사(C6 #10)·알림(C7 #17)이 아직 스텁이라 Mockito 가짜로 대신한다.
 *       DB 롤백(이미지 실패 시 작품·회차가 남지 않음)과 잠금(연타)은 #16·#10 merge 후 DB 연결 테스트로 다시 확인한다.
 */
class AssignmentServiceTest {

    private ReviewRoundRepository repository;
    private ProgressService progressService;
    private EnrollmentService enrollmentService;
    private SectionService sectionService;
    private CourseService courseService;
    private WorkService workService;
    private NotificationService notificationService;
    private AssignmentService service;

    private final AssignmentSubmitDTO form = new AssignmentSubmitDTO("색연필화 기초 — 최종 과제", "사과 정물");

    @BeforeEach
    void setUp() {
        repository = mock(ReviewRoundRepository.class);
        progressService = mock(ProgressService.class);
        enrollmentService = mock(EnrollmentService.class);
        sectionService = mock(SectionService.class);
        courseService = mock(CourseService.class);
        workService = mock(WorkService.class);
        notificationService = mock(NotificationService.class);
        service = new AssignmentService(repository, new SubmissionPolicy(progressService), enrollmentService,
                sectionService, courseService, progressService, workService, notificationService, mock(ReviewQuery.class),
                new VideoAccessPolicy());

        when(progressService.isAllCompleted(10L)).thenReturn(true);
        when(courseService.getCourseBriefs(List.of(1L))).thenReturn(Map.of(1L,
                new CourseBriefDTO(1L, 7L, "색연필화", true, CourseLevel.BASIC, "색연필화 기초")));
        when(sectionService.getCurrentInstructorId(10L)).thenReturn(6L);
        when(repository.saveAndFlush(any(ReviewRound.class))).thenAnswer(inv -> {
            ReviewRound saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    private Enrollment enrollment() {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "userId", 2L);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", EnrollmentStatus.ACTIVE);
        ReflectionTestUtils.setField(e, "confirmedAt", LocalDateTime.now().minusDays(10));
        ReflectionTestUtils.setField(e, "expiresAt", LocalDateTime.now().plusDays(20));
        return e;
    }

    private ReviewRound round(ReviewStatus status, int roundNo, LocalDateTime submittedAt) {
        ReviewRound r = ReviewRound.create(10L, 100L + roundNo, roundNo, submittedAt);
        ReflectionTestUtils.setField(r, "id", (long) roundNo);
        ReflectionTestUtils.setField(r, "status", status);
        return r;
    }

    private List<MultipartFile> images(int count) {
        List<MultipartFile> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new MockMultipartFile("images", "a" + i + ".png", "image/png", new byte[]{1, 2, 3}));
        }
        return list;
    }

    private void lockReturns(List<ReviewRound> rounds) {
        when(enrollmentService.lockOwnedEnrollment(2L, 10L)).thenReturn(enrollment());
        when(repository.findByEnrollmentIdForUpdate(10L)).thenReturn(rounds);
    }

    @Test
    @DisplayName("담당 강사가 없는 분반도 제출은 저장되고 알림만 건너뛴다 (Q-05)")
    void submitWithoutInstructorSkipsNotification() {
        lockReturns(List.of());
        when(sectionService.getCurrentInstructorId(10L)).thenReturn(null);
        when(workService.saveFinalWorkVersion(eq(2L), eq(10L), eq(7L), eq(CourseLevel.BASIC), anyString(), anyString(), anyList()))
                .thenReturn(501L);

        Long roundId = service.submit(2L, 10L, form, images(2));

        assertThat(roundId).isEqualTo(99L);
        verify(repository).saveAndFlush(any(ReviewRound.class));
        verify(notificationService, never()).notify(any(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("최초 제출: 작품 버전을 저장하고 1회차 평가 대기(마감 = 제출 + 7일)를 만든 뒤 담당 강사에게 알린다")
    void submitCreatesWaitingRound() {
        lockReturns(List.of());
        when(workService.saveFinalWorkVersion(eq(2L), eq(10L), eq(7L), eq(CourseLevel.BASIC), anyString(), anyString(), anyList()))
                .thenReturn(501L);

        Long roundId = service.submit(2L, 10L, form, images(2));

        ArgumentCaptor<ReviewRound> saved = ArgumentCaptor.forClass(ReviewRound.class);
        verify(repository).saveAndFlush(saved.capture());
        ReviewRound round = saved.getValue();
        assertThat(roundId).isEqualTo(99L);
        assertThat(round.getRoundNo()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(ReviewStatus.WAITING);
        assertThat(round.getWorkVersionId()).isEqualTo(501L);
        assertThat(round.getDueAt()).isEqualTo(round.getSubmittedAt().plusDays(7));
        verify(notificationService).notify(eq(6L), eq(NotificationKind.REVIEW_SUBMITTED), eq(99L), anyString(), anyString());
        verify(enrollmentService, never()).complete(anyLong());  // 제출 즉시 수료가 아니다
    }

    @Test
    @DisplayName("빈 파일 칸만 보내면 0장으로 세어 INVALID_IMAGE_COUNT, 작품 저장을 부르지 않는다")
    void submitWithEmptyFileRejected() {
        lockReturns(List.of());
        List<MultipartFile> emptyOnly = List.of(new MockMultipartFile("images", "", "application/octet-stream", new byte[0]));

        assertThatThrownBy(() -> service.submit(2L, 10L, form, emptyOnly)).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_COUNT);
        verify(workService, never()).saveFinalWorkVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연타 두 번째 요청: 잠금 읽기에서 평가 대기를 보면 ALREADY_SUBMITTED, 파일·회차를 만들지 않는다")
    void doubleSubmitBlocked() {
        lockReturns(List.of(round(ReviewStatus.WAITING, 1, LocalDateTime.now())));

        assertThatThrownBy(() -> service.submit(2L, 10L, form, images(1))).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.ALREADY_SUBMITTED);
        verify(workService, never()).saveFinalWorkVersion(any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미지 저장이 실패하면 예외가 그대로 나가고 평가 회차·알림을 만들지 않는다(트랜잭션 롤백)")
    void imageFailureCreatesNoRound() {
        lockReturns(List.of());
        when(workService.saveFinalWorkVersion(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_IMAGE_FILE));

        assertThatThrownBy(() -> service.submit(2L, 10L, form, images(2))).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
        verify(repository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("평가 전 수정: 같은 버전을 고치고 회차(마감)는 건드리지 않는다")
    void editKeepsDueAt() {
        LocalDateTime submittedAt = LocalDateTime.now().minusDays(2);
        ReviewRound waiting = round(ReviewStatus.WAITING, 1, submittedAt);
        LocalDateTime dueBefore = waiting.getDueAt();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(waiting));
        when(enrollmentService.getOwnedEnrollment(2L, 10L)).thenReturn(enrollment());

        Long enrollmentId = service.edit(2L, 1L, form, List.of());

        assertThat(enrollmentId).isEqualTo(10L);
        verify(workService).updateFinalWorkVersion(2L, 101L, form.getTitle(), form.getDescription(), List.of());
        verify(workService, never()).saveFinalWorkVersion(any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
        assertThat(waiting.getDueAt()).isEqualTo(dueBefore);
        assertThat(waiting.getSubmittedAt()).isEqualTo(submittedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("평가 중 수정은 SUBMISSION_LOCKED")
    void editLockedInReview() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(round(ReviewStatus.IN_REVIEW, 1, LocalDateTime.now())));
        when(enrollmentService.getOwnedEnrollment(2L, 10L)).thenReturn(enrollment());

        assertThatThrownBy(() -> service.edit(2L, 1L, form, List.of())).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.SUBMISSION_LOCKED);
        verify(workService, never()).updateFinalWorkVersion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("없는 회차 수정은 REVIEW_NOT_FOUND")
    void editUnknownRound() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(2L, 1L, form, List.of())).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("반려 후 재제출: 새 버전 + 직전 + 1회차 + 새 7일 마감(이전 회차 마감과 무관)")
    void resubmitCreatesNextRound() {
        ReviewRound rejected = round(ReviewStatus.REJECTED, 1, LocalDateTime.now().minusDays(6));
        lockReturns(List.of(rejected));
        when(workService.saveFinalWorkVersion(any(), any(), any(), any(), any(), any(), any())).thenReturn(502L);
        LocalDateTime before = LocalDateTime.now();

        service.resubmit(2L, 10L, form, images(1));

        ArgumentCaptor<ReviewRound> saved = ArgumentCaptor.forClass(ReviewRound.class);
        verify(repository).saveAndFlush(saved.capture());
        ReviewRound next = saved.getValue();
        assertThat(next.getRoundNo()).isEqualTo(2);
        assertThat(next.getWorkVersionId()).isEqualTo(502L);
        assertThat(next.getSubmittedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(next.getDueAt()).isEqualTo(next.getSubmittedAt().plusDays(7));
        assertThat(rejected.getStatus()).isEqualTo(ReviewStatus.REJECTED);  // 이전 회차는 그대로(새 행으로 쌓는다)
    }

    @Test
    @DisplayName("직전 회차가 반려가 아니면 재제출할 수 없다")
    void resubmitNotAllowed() {
        lockReturns(List.of(round(ReviewStatus.APPROVED, 1, LocalDateTime.now().minusDays(6))));

        assertThatThrownBy(() -> service.resubmit(2L, 10L, form, images(1))).isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.RESUBMIT_NOT_ALLOWED);
    }
}
