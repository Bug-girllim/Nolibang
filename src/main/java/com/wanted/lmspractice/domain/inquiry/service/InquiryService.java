package com.wanted.lmspractice.domain.inquiry.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.inquiry.dto.InquiryDTO;
import com.wanted.lmspractice.domain.inquiry.dto.InquiryMessageDTO;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 학습자 ↔ 담당 강사 1:1 문의 — 스레드 생성·메시지 작성·답변, 담당 공백 시 작성 불가(D-31), 알림 연결, 화면용 목록·스레드 조립
 * [담당] 팀원 4 · #21   [화면] U-S18·T-S06   [API] IQ-01~04, II-01~03   [기획] FR-042, D-08·D-31
 * [연결]
 *  - 호출받음: InquiryController(학습자), InstructorInquiryController(강사)
 *  - 호출함:   EnrollmentService.getOwnedEnrollment·getMyEnrollments(C6·C18), SectionService.getCurrentInstructorId(C6, 담당 조회),
 *             CourseService(강의 이름), InstructorAccountService.getInstructor(강사 이름), NotificationService.notify(C7)
 *             InstructorInquiryQuery(강사의 담당 분반 학습자 스레드)
 * 규칙
 *  - 문의는 수강 건 하나에 스레드 하나(강의별). 수강 중(ACTIVE)이거나 수료(COMPLETED)한 본인 수강만 쓸 수 있다. 대기·취소·만료는 INQUIRY_NOT_ALLOWED.
 *  - 현재 담당 강사는 스레드에 저장하지 않고 매번 분반에서 조회한다 → 담당이 바뀌면 새 담당이 스레드를 이어받는다(D-08).
 *  - 담당 강사가 없으면 작성 불가(D-31). 강사의 일방 발송은 없다(답변만).
 * 알림: 학습자 메시지 → 담당 강사 INQUIRY_RECEIVED, 강사 답변 → 학습자 INQUIRY_ANSWERED. 메시지마다 알리려고 dedupeKey에 메시지 id를 붙인다.
 * [기술] 🟢 JPA·@Transactional, 🟢 Java 컬렉션
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    /** ❓ 임시값: 문의·답변 글자 수 상한(기획 [미정]) — #01 확정 필요. DB는 TEXT라 길이 제한이 없다 */
    public static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 200;       // inquiry_threads.title VARCHAR(200)
    private static final int DERIVED_TITLE_LENGTH = 30;    // 제목을 안 쓰면 내용 앞부분으로 만든다

    private final InquiryThreadRepository threadRepository;
    private final InquiryMessageRepository messageRepository;
    private final InstructorInquiryQuery instructorQuery;
    private final EnrollmentService enrollmentService;
    private final SectionService sectionService;
    private final CourseService courseService;
    private final InstructorAccountService instructorAccountService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------
    // 쓰기
    // ------------------------------------------------------------------

    /**
     * 문의 작성 — 이 수강의 스레드가 없으면 만들고, 있으면 이어 쓴다. 담당 강사에게 INQUIRY_RECEIVED 알림.
     * 호출: InquiryController.write (POST /inquiries, API IQ-02)
     * @param title 새 스레드일 때의 제목. 비어 있으면 내용 앞부분으로 만든다
     * @return 스레드 id (화면 이동용)
     * @throws BusinessException ENROLLMENT_NOT_FOUND(없거나 남의 수강), INQUIRY_NOT_ALLOWED(수강 중·수료가 아니거나 담당 공백), INVALID_INPUT(빈 내용)
     * 트랜잭션: 스레드·메시지·알림이 한 트랜잭션이라 알림 실패 시 함께 취소된다
     */
    @Transactional
    public Long writeMessage(Long userId, Long enrollmentId, String title, String content) {
        String body = requireContent(content);
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, enrollmentId);   // C6: 본인 확인
        Long instructorId = requireWritable(enrollment);
        InquiryThread thread = threadRepository.findFirstByEnrollmentIdOrderByIdAsc(enrollmentId)
                .orElseGet(() -> threadRepository.save(InquiryThread.create(
                        enrollment.getId(), userId, enrollment.getCourseId(), titleOf(title, body), LocalDateTime.now())));
        return appendLearnerMessage(thread, userId, instructorId, body);
    }

    /** 이슈 입력 규격 writeMessage(userId, enrollmentId, content) — 제목은 내용 앞부분으로 만든다 */
    @Transactional
    public Long writeMessage(Long userId, Long enrollmentId, String content) {
        return writeMessage(userId, enrollmentId, null, content);
    }

    /**
     * 이미 있는 스레드에 학습자가 메시지 추가.
     * 호출: InquiryController.addMessage (POST /inquiries/{threadId}/messages, API IQ-04)
     * @throws BusinessException NOT_FOUND(없거나 남의 스레드), INQUIRY_NOT_ALLOWED, INVALID_INPUT
     */
    @Transactional
    public Long addMessage(Long userId, Long threadId, String content) {
        String body = requireContent(content);
        InquiryThread thread = threadRepository.findById(threadId)
                .filter(t -> t.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));   // 남의 스레드는 존재를 숨긴다
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, thread.getEnrollmentId());
        Long instructorId = requireWritable(enrollment);
        return appendLearnerMessage(thread, userId, instructorId, body);
    }

    /**
     * 담당 강사 답변 — 스레드 상태 OPEN → ANSWERED, 학습자에게 INQUIRY_ANSWERED 알림.
     * 호출: InstructorInquiryController.answer (POST /instructor/inquiries/{threadId}/answer, API II-03)
     * @throws BusinessException NOT_FOUND(없는 스레드), NOT_ASSIGNED_INSTRUCTOR(현재 담당 분반 학습자의 스레드가 아님), INVALID_INPUT
     */
    @Transactional
    public void answer(Long instructorId, Long threadId, String content) {
        String body = requireContent(content);
        InquiryThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireAssigned(thread, instructorId);
        InquiryMessage saved = messageRepository.save(InquiryMessage.create(thread.getId(), instructorId, body));
        thread.markAnswered(LocalDateTime.now());
        notificationService.notify(thread.getLearnerId(), NotificationKind.INQUIRY_ANSWERED, thread.getId(),
                "문의 답변", "「" + thread.getTitle() + "」 문의에 담당 강사가 답변했어요.",
                "INQUIRY_ANSWERED:" + thread.getId() + ":" + saved.getId());
    }

    // ------------------------------------------------------------------
    // 읽기 (화면용)
    // ------------------------------------------------------------------

    /**
     * 학습자 문의 화면 — 문의할 수 있는(수강 중·수료) 수강과 이미 스레드가 있는 수강을 한 줄씩, 선택한 항목의 메시지.
     * 호출: InquiryController.list·thread (GET /inquiries, /inquiries/{threadId}, API IQ-01·03)
     * @param threadId 선택한 스레드(없으면 null)   @param enrollmentId 아직 스레드가 없는 수강을 골랐을 때(없으면 null)
     * 선택이 없으면 가장 최근 항목을 고른다.
     * @throws BusinessException NOT_FOUND(내 목록에 없는 스레드)
     */
    @Transactional(readOnly = true)
    public InquiryPageDTO getLearnerPage(Long userId, Long threadId, Long enrollmentId) {
        Map<Long, InquiryThread> threadByEnrollment = new HashMap<>();
        for (InquiryThread t : threadRepository.findByLearnerIdOrderByLastMessageAtDescIdDesc(userId)) {
            threadByEnrollment.putIfAbsent(t.getEnrollmentId(), t);
        }
        // 스레드가 있거나(지난 문의 열람) 지금 문의할 수 있는 수강
        List<MyEnrollmentDTO> enrollments = enrollmentService.getMyEnrollments(userId).stream()
                .filter(e -> threadByEnrollment.containsKey(e.getEnrollmentId()) || isWritableStatus(e.getStatus()))
                .toList();
        Map<Long, CourseBriefDTO> briefs = courseService.getCourseBriefs(
                enrollments.stream().map(MyEnrollmentDTO::getCourseId).distinct().toList());

        Map<Long, String> instructorNames = new HashMap<>();
        List<InquiryDTO> withThread = new ArrayList<>();
        List<InquiryDTO> withoutThread = new ArrayList<>();
        for (MyEnrollmentDTO e : enrollments) {
            InquiryThread thread = threadByEnrollment.get(e.getEnrollmentId());
            Long instructorId = sectionService.getCurrentInstructorId(e.getEnrollmentId());   // C6: 담당은 분반에서 조회(D-08)
            String instructorName = instructorId == null ? null
                    : instructorNames.computeIfAbsent(instructorId, this::instructorNameOf);
            CourseBriefDTO brief = briefs.get(e.getCourseId());
            boolean vacant = instructorId == null;
            InquiryDTO dto = new InquiryDTO(
                    thread == null ? null : thread.getId(), e.getEnrollmentId(),
                    brief == null ? "" : brief.getTitle(), brief == null ? "" : brief.getLevelLabel(),
                    e.getSectionName(), instructorName, null,
                    thread == null ? null : thread.getTitle(), thread == null ? null : thread.getStatus(),
                    thread == null ? null : thread.getLastMessageAt(),
                    vacant, !vacant && isWritableStatus(e.getStatus()));
            (thread == null ? withoutThread : withThread).add(dto);
        }
        withThread.addAll(withoutThread);   // 스레드 있는 항목(최근 순)을 위에, 문의 없음은 아래에

        InquiryDTO selected = pickLearnerSelection(withThread, threadId, enrollmentId);
        List<InquiryMessageDTO> messages = new ArrayList<>();
        if (selected != null && selected.isHasThread()) {
            String instructorLabel = "담당 전문 강사" + (selected.getInstructorName() == null ? "" : " " + selected.getInstructorName());
            for (InquiryMessage m : messageRepository.findByThreadIdOrderByIdAsc(selected.getThreadId())) {
                boolean mine = m.getAuthorId().equals(userId);
                messages.add(InquiryMessageDTO.of(m, mine, mine ? "나" : instructorLabel));
            }
        }
        return new InquiryPageDTO(withThread, selected, messages, 0);
    }

    /**
     * 강사 문의 화면 — 현재 담당 분반 학습자의 스레드만, 선택한 스레드의 메시지.
     * 호출: InstructorInquiryController.list·thread (GET /instructor/inquiries[/{threadId}], API II-01·02)
     * @param unansweredOnly true면 답변 대기만 (waitingCount는 필터와 관계없이 전체 대기 수)
     * @throws BusinessException NOT_FOUND(없는 스레드), NOT_ASSIGNED_INSTRUCTOR(내 담당이 아닌 스레드)
     */
    @Transactional(readOnly = true)
    public InquiryPageDTO getInstructorPage(Long instructorId, Long threadId, boolean unansweredOnly) {
        List<InstructorInquiryQuery.Row> rows = instructorQuery.findThreads(instructorId);
        long waiting = rows.stream().filter(r -> r.getStatus() == InquiryStatus.OPEN).count();
        Map<Long, CourseBriefDTO> briefs = courseService.getCourseBriefs(
                rows.stream().map(InstructorInquiryQuery.Row::getCourseId).distinct().toList());

        List<InquiryDTO> all = rows.stream().map(r -> {
            CourseBriefDTO brief = briefs.get(r.getCourseId());
            return new InquiryDTO(r.getThreadId(), null, brief == null ? "" : brief.getTitle(),
                    brief == null ? "" : brief.getLevelLabel(), null, null, r.getLearnerName(),
                    r.getTitle(), r.getStatus(), r.getLastMessageAt(), false, false);
        }).toList();

        InquiryDTO selected = null;
        if (threadId != null) {
            selected = all.stream().filter(t -> t.getThreadId().equals(threadId)).findFirst().orElse(null);
            if (selected == null) {
                // 내 목록에 없다: 스레드가 없으면 NOT_FOUND, 있으면 내 담당이 아니다
                throw new BusinessException(threadRepository.existsById(threadId)
                        ? ErrorCode.NOT_ASSIGNED_INSTRUCTOR : ErrorCode.NOT_FOUND);
            }
        }
        List<InquiryDTO> shown = unansweredOnly ? all.stream().filter(t -> !t.isAnswered()).toList() : all;
        if (selected == null && !shown.isEmpty()) {
            selected = shown.get(0);
        }

        List<InquiryMessageDTO> messages = new ArrayList<>();
        if (selected != null) {
            Long learnerId = threadRepository.findById(selected.getThreadId()).map(InquiryThread::getLearnerId).orElse(null);
            String instructorLabel = "강사 " + instructorNameOf(instructorId);
            for (InquiryMessage m : messageRepository.findByThreadIdOrderByIdAsc(selected.getThreadId())) {
                boolean fromLearner = m.getAuthorId().equals(learnerId);
                messages.add(InquiryMessageDTO.of(m, !fromLearner, fromLearner ? "수강생 " + selected.getLearnerName() : instructorLabel));
            }
        }
        return new InquiryPageDTO(shown, selected, messages, waiting);
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /** 수강 중·수료한 본인 수강이고 담당 강사가 있어야 쓸 수 있다. 담당 강사 id를 돌려준다(D-31) */
    private Long requireWritable(Enrollment enrollment) {
        if (!isWritableStatus(enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_ALLOWED);
        }
        Long instructorId = sectionService.getCurrentInstructorId(enrollment.getId());
        if (instructorId == null) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_ALLOWED);   // D-31: 담당 공백
        }
        return instructorId;
    }

    private void requireAssigned(InquiryThread thread, Long instructorId) {
        if (!instructorQuery.isAssigned(thread.getId(), instructorId)) {
            throw new BusinessException(ErrorCode.NOT_ASSIGNED_INSTRUCTOR);
        }
    }

    private Long appendLearnerMessage(InquiryThread thread, Long userId, Long instructorId, String body) {
        InquiryMessage saved = messageRepository.save(InquiryMessage.create(thread.getId(), userId, body));
        thread.receiveLearnerMessage(LocalDateTime.now());
        notificationService.notify(instructorId, NotificationKind.INQUIRY_RECEIVED, thread.getId(),
                "새 문의", courseService.getCourseTitle(thread.getCourseId()) + " 수강생 문의가 새로 들어왔어요.",
                "INQUIRY_RECEIVED:" + thread.getId() + ":" + saved.getId());
        return thread.getId();
    }

    private InquiryDTO pickLearnerSelection(List<InquiryDTO> items, Long threadId, Long enrollmentId) {
        if (threadId != null) {
            return items.stream().filter(t -> threadId.equals(t.getThreadId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));   // 남의 스레드는 존재를 숨긴다
        }
        if (enrollmentId != null) {
            Optional<InquiryDTO> chosen = items.stream().filter(t -> enrollmentId.equals(t.getEnrollmentId())).findFirst();
            if (chosen.isPresent()) {
                return chosen.get();
            }
        }
        return items.isEmpty() ? null : items.get(0);
    }

    private boolean isWritableStatus(EnrollmentStatus status) {
        return status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED;
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return content.strip();
    }

    /** 제목: 입력이 있으면 그것(200자까지), 없으면 내용 앞부분 */
    private String titleOf(String title, String body) {
        String base = (title == null || title.isBlank()) ? body.replaceAll("\\s+", " ") : title.strip();
        int limit = (title == null || title.isBlank()) ? DERIVED_TITLE_LENGTH : MAX_TITLE_LENGTH;
        return base.length() > limit ? base.substring(0, limit) : base;
    }

    /** 강사 이름 — 계정을 찾을 수 없으면 빈 문자열 */
    private String instructorNameOf(Long instructorId) {
        try {
            return instructorAccountService.getInstructor(instructorId).getRealName();
        } catch (BusinessException e) {
            return "";
        }
    }
}
