package com.wanted.lmspractice.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.inquiry.dto.InquiryPageDTO;
import com.wanted.lmspractice.domain.inquiry.entity.InquiryMessage;
import com.wanted.lmspractice.domain.inquiry.entity.InquiryStatus;
import com.wanted.lmspractice.domain.inquiry.entity.InquiryThread;
import com.wanted.lmspractice.domain.inquiry.repository.InquiryMessageRepository;
import com.wanted.lmspractice.domain.inquiry.repository.InquiryThreadRepository;
import com.wanted.lmspractice.domain.inquiry.repository.InstructorInquiryQuery;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] InquiryService 테스트 — 스레드 생성·이어쓰기, 알림 두 방향, 담당 공백·수강 이력 없음·종료 수강·빈 내용 거부, 담당 외 강사·남의 스레드 차단
 * [담당] 팀원 4 · #21
 * [전제] 의존 서비스·Repository는 Mockito 가짜로 대신한다. 담당 조회(SectionService, C6)가 #10 전까지 스텁이라 DB 연결 흐름은 #10 merge 후에 확인한다.
 */
class InquiryServiceTest {

    private static final Long LEARNER = 2L;
    private static final Long INSTRUCTOR = 6L;

    private InquiryThreadRepository threadRepository;
    private InquiryMessageRepository messageRepository;
    private InstructorInquiryQuery instructorQuery;
    private EnrollmentService enrollmentService;
    private SectionService sectionService;
    private CourseService courseService;
    private NotificationService notificationService;
    private InstructorAccountService instructorAccountService;
    private InquiryService service;

    @BeforeEach
    void setUp() {
        threadRepository = mock(InquiryThreadRepository.class);
        messageRepository = mock(InquiryMessageRepository.class);
        instructorQuery = mock(InstructorInquiryQuery.class);
        enrollmentService = mock(EnrollmentService.class);
        sectionService = mock(SectionService.class);
        courseService = mock(CourseService.class);
        notificationService = mock(NotificationService.class);
        instructorAccountService = mock(InstructorAccountService.class);
        com.wanted.lmspractice.domain.user.dto.InstructorDTO instructor = new com.wanted.lmspractice.domain.user.dto.InstructorDTO();
        ReflectionTestUtils.setField(instructor, "realName", "강사 A");
        when(instructorAccountService.getInstructor(INSTRUCTOR)).thenReturn(instructor);
        service = new InquiryService(threadRepository, messageRepository, instructorQuery, enrollmentService,
                sectionService, courseService, instructorAccountService, notificationService);

        when(courseService.getCourseTitle(1L)).thenReturn("색연필화 기초");
        when(threadRepository.save(any(InquiryThread.class))).thenAnswer(inv -> {
            InquiryThread t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 50L);
            }
            return t;
        });
        when(messageRepository.save(any(InquiryMessage.class))).thenAnswer(inv -> {
            InquiryMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 900L);
            return m;
        });
    }

    private Enrollment enrollment(EnrollmentStatus status) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "userId", LEARNER);
        ReflectionTestUtils.setField(e, "courseId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        return e;
    }

    private InquiryThread thread(long id, InquiryStatus status) {
        InquiryThread t = InquiryThread.create(10L, LEARNER, 1L, "명암 질문", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(t, "id", id);
        ReflectionTestUtils.setField(t, "status", status);
        return t;
    }

    private void learnerOk(EnrollmentStatus status, Long instructorId) {
        when(enrollmentService.getOwnedEnrollment(LEARNER, 10L)).thenReturn(enrollment(status));
        when(sectionService.getCurrentInstructorId(10L)).thenReturn(instructorId);
    }

    private BusinessException codeOf(Throwable t) {
        assertThat(t).isInstanceOf(BusinessException.class);
        return (BusinessException) t;
    }

    @Test
    @DisplayName("처음 문의하면 스레드를 만들고 메시지를 저장하며 담당 강사에게 INQUIRY_RECEIVED 알림")
    void firstInquiryCreatesThreadAndNotifies() {
        learnerOk(EnrollmentStatus.ACTIVE, INSTRUCTOR);
        when(threadRepository.findFirstByEnrollmentIdOrderByIdAsc(10L)).thenReturn(Optional.empty());

        Long threadId = service.writeMessage(LEARNER, 10L, "명암 질문", "2차시 명암이 어려워요.");

        assertThat(threadId).isEqualTo(50L);
        ArgumentCaptor<InquiryThread> threadCaptor = ArgumentCaptor.forClass(InquiryThread.class);
        verify(threadRepository).save(threadCaptor.capture());
        assertThat(threadCaptor.getValue().getTitle()).isEqualTo("명암 질문");
        assertThat(threadCaptor.getValue().getLearnerId()).isEqualTo(LEARNER);
        verify(notificationService).notify(eq(INSTRUCTOR), eq(NotificationKind.INQUIRY_RECEIVED), eq(50L),
                anyString(), anyString(), eq("INQUIRY_RECEIVED:50:900"));
    }

    @Test
    @DisplayName("제목을 안 쓰면 내용 앞부분으로 제목을 만든다")
    void titleDerivedFromContent() {
        learnerOk(EnrollmentStatus.ACTIVE, INSTRUCTOR);
        when(threadRepository.findFirstByEnrollmentIdOrderByIdAsc(10L)).thenReturn(Optional.empty());

        service.writeMessage(LEARNER, 10L, "준비물은 어떤 색연필을 쓰면 되는지 궁금합니다. 수채 색연필도 괜찮을까요?");

        ArgumentCaptor<InquiryThread> captor = ArgumentCaptor.forClass(InquiryThread.class);
        verify(threadRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(30).startsWith("준비물은 어떤");
    }

    @Test
    @DisplayName("이미 스레드가 있으면 새로 만들지 않고 이어 쓰며, 답변 완료였다면 다시 답변 대기가 된다")
    void continuesExistingThread() {
        learnerOk(EnrollmentStatus.COMPLETED, INSTRUCTOR);
        InquiryThread existing = thread(7L, InquiryStatus.ANSWERED);
        when(threadRepository.findFirstByEnrollmentIdOrderByIdAsc(10L)).thenReturn(Optional.of(existing));

        Long threadId = service.writeMessage(LEARNER, 10L, null, "한 가지 더 궁금해요.");

        assertThat(threadId).isEqualTo(7L);
        assertThat(existing.getStatus()).isEqualTo(InquiryStatus.OPEN);
        verify(threadRepository, never()).save(any(InquiryThread.class));
    }

    @Test
    @DisplayName("담당 강사가 없으면(담당 공백) 작성할 수 없다 (D-31)")
    void vacantInstructorNotAllowed() {
        learnerOk(EnrollmentStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.writeMessage(LEARNER, 10L, null, "질문"))
                .satisfies(t -> assertThat(codeOf(t).getErrorCode()).isEqualTo(ErrorCode.INQUIRY_NOT_ALLOWED));
        verify(messageRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("수강 중·수료가 아닌 수강(취소·만료)은 작성할 수 없다")
    void endedEnrollmentNotAllowed() {
        for (EnrollmentStatus status : List.of(EnrollmentStatus.CANCELLED, EnrollmentStatus.EXPIRED)) {
            learnerOk(status, INSTRUCTOR);
            assertThatThrownBy(() -> service.writeMessage(LEARNER, 10L, null, "질문"))
                    .satisfies(t -> assertThat(codeOf(t).getErrorCode()).isEqualTo(ErrorCode.INQUIRY_NOT_ALLOWED));
        }
    }

    @Test
    @DisplayName("수강 이력이 없거나 남의 수강이면 수강 계약의 ENROLLMENT_NOT_FOUND가 그대로 나간다")
    void notOwnedEnrollment() {
        when(enrollmentService.getOwnedEnrollment(LEARNER, 99L))
                .thenThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        assertThatThrownBy(() -> service.writeMessage(LEARNER, 99L, null, "질문"))
                .satisfies(t -> assertThat(codeOf(t).getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("빈 내용이나 공백만 있는 내용은 거부한다")
    void blankContentRejected() {
        for (String blank : new String[] {null, "", "   \n "}) {
            assertThatThrownBy(() -> service.writeMessage(LEARNER, 10L, null, blank))
                    .satisfies(t -> assertThat(codeOf(t).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        verify(threadRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 스레드에 메시지를 추가하려 하면 NOT_FOUND")
    void othersThreadHidden() {
        when(threadRepository.findById(7L)).thenReturn(Optional.of(thread(7L, InquiryStatus.OPEN)));

        assertThatThrownBy(() -> service.addMessage(99L, 7L, "내용"))
                .satisfies(t -> assertThat(codeOf(t).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("담당 강사의 답변: 상태 ANSWERED, 학습자에게 INQUIRY_ANSWERED 알림")
    void answerMarksAnsweredAndNotifies() {
        InquiryThread t = thread(7L, InquiryStatus.OPEN);
        when(threadRepository.findById(7L)).thenReturn(Optional.of(t));
        when(instructorQuery.isAssigned(7L, INSTRUCTOR)).thenReturn(true);

        service.answer(INSTRUCTOR, 7L, "다섯 단계로 나눠 보세요.");

        assertThat(t.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        verify(notificationService).notify(eq(LEARNER), eq(NotificationKind.INQUIRY_ANSWERED), eq(7L),
                anyString(), anyString(), eq("INQUIRY_ANSWERED:7:900"));
    }

    @Test
    @DisplayName("담당이 아닌 강사는 답변할 수 없다 (NOT_ASSIGNED_INSTRUCTOR)")
    void notAssignedInstructorRejected() {
        InquiryThread t = thread(7L, InquiryStatus.OPEN);
        when(threadRepository.findById(7L)).thenReturn(Optional.of(t));
        when(instructorQuery.isAssigned(7L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> service.answer(8L, 7L, "답변"))
                .satisfies(e -> assertThat(codeOf(e).getErrorCode()).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThat(t.getStatus()).isEqualTo(InquiryStatus.OPEN);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 스레드에 답변하면 NOT_FOUND, 빈 답변은 거부")
    void answerNotFoundAndBlank() {
        when(threadRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.answer(INSTRUCTOR, 404L, "답변"))
                .satisfies(e -> assertThat(codeOf(e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.answer(INSTRUCTOR, 7L, " "))
                .satisfies(e -> assertThat(codeOf(e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("강사 화면: 내 담당이 아닌 스레드를 열면 NOT_ASSIGNED_INSTRUCTOR, 없는 스레드면 NOT_FOUND")
    void instructorPageAccessErrors() {
        when(instructorQuery.findThreads(INSTRUCTOR)).thenReturn(List.of());
        when(courseService.getCourseBriefs(any())).thenReturn(java.util.Map.of());
        when(threadRepository.existsById(7L)).thenReturn(true);
        when(threadRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.getInstructorPage(INSTRUCTOR, 7L, false))
                .satisfies(e -> assertThat(codeOf(e).getErrorCode()).isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR));
        assertThatThrownBy(() -> service.getInstructorPage(INSTRUCTOR, 404L, false))
                .satisfies(e -> assertThat(codeOf(e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("강사 화면: 담당 학습자 스레드만 보이고 답변 대기 수를 센다")
    void instructorPageListsAssignedThreads() {
        when(instructorQuery.findThreads(INSTRUCTOR)).thenReturn(List.of(
                new InstructorInquiryQuery.Row(2L, 1L, "준비물 질문", InquiryStatus.OPEN, LocalDateTime.now(), "이하늘"),
                new InstructorInquiryQuery.Row(1L, 1L, "명암 질문", InquiryStatus.ANSWERED, LocalDateTime.now().minusDays(20), "김학습")));
        when(courseService.getCourseBriefs(any())).thenReturn(java.util.Map.of());
        when(messageRepository.findByThreadIdOrderByIdAsc(2L)).thenReturn(List.of());
        when(threadRepository.findById(2L)).thenReturn(Optional.of(thread(2L, InquiryStatus.OPEN)));

        InquiryPageDTO page = service.getInstructorPage(INSTRUCTOR, null, true);

        assertThat(page.getWaitingCount()).isEqualTo(1);
        assertThat(page.getThreads()).hasSize(1);   // 답변 대기만
        assertThat(page.getSelected().getLearnerName()).isEqualTo("이하늘");
    }
}
