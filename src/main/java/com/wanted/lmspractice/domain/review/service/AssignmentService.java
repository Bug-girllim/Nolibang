package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.dto.AssignmentSubmitDTO;
import com.wanted.lmspractice.domain.review.dto.AssignmentViewDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.policy.SubmissionPolicy;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 최종 과제 제출(평가 대기)·평가 전 수정(마감 불변)·반려 후 정식 재제출(새 버전 + 새 회차 + 새 7일) 흐름을 조립하고,
 *        최종 과제 화면(U-S07) 값을 만든다. 제출해도 수료가 아니다 — 수료는 #14 승인에서만 일어난다.
 * [담당] 팀원 3 · #13   [화면] U-S07   [기획] FR-021·022·026, D-41   [API] AS-01~AS-04
 * [연결]
 *  - 호출받음: AssignmentController(화면·제출·수정·재제출)
 *  - 호출함:   EnrollmentService(C6 getOwnedEnrollment, [#13 요청] lockOwnedEnrollment), CourseService(강의 정보·과제 제목),
 *             ProgressService(C10 차시 완료), SubmissionPolicy(제출 규칙),
 *             WorkService(C8 saveFinalWorkVersion·updateFinalWorkVersion — 작품·버전·이미지·파일 저장은 전부 팀원 4 몫),
 *             SectionService(C6 getCurrentInstructorId — 담당 강사), NotificationService(C7 notify REVIEW_SUBMITTED),
 *             ReviewRoundRepository
 *  - 이어서 쓰는 곳: #14 ReviewService가 이 서비스가 만든 ReviewRound(WAITING)를 평가한다
 * [트랜잭션] 제출·재제출·수정은 한 트랜잭션 — 이미지 한 장이라도 실패하면 작품·버전·평가 회차·알림이 모두 롤백된다.
 *           잠금 순서: 강의(분반) → 수강 건 → 평가 회차. #14에서 회차를 잠근 뒤 수강 건을 바꿀 때도 이 순서를 지켜야 교착이 없다.
 * [기술] 🟢 @Transactional, 🟢 List&lt;MultipartFile&gt;(chap06), 🟢 native FOR UPDATE
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final String SUBMITTED_TITLE = "새 최종 과제 제출";
    private static final String RESUBMITTED_TITLE = "최종 과제 재제출";

    private final ReviewRoundRepository reviewRoundRepository;
    private final SubmissionPolicy submissionPolicy;
    private final EnrollmentService enrollmentService;
    private final SectionService sectionService;
    private final CourseService courseService;
    private final ProgressService progressService;
    private final WorkService workService;
    private final NotificationService notificationService;

    /**
     * 최종 과제 화면 값 — 화면 상태(3차시 미완료·미제출·평가 대기·평가 중·반려·수료·기간 종료), 차시 완료 수, 제출 이력, 23일 경과 안내.
     * 호출: AssignmentController.view (GET /assignments?enrollmentId=, API AS-01), 입력 오류로 같은 화면을 다시 그릴 때
     * @throws BusinessException ENROLLMENT_NOT_FOUND — 없거나 남의 수강 건, COURSE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public AssignmentViewDTO getAssignmentView(Long userId, Long enrollmentId) {
        LocalDateTime now = LocalDateTime.now();
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, enrollmentId);
        CourseBriefDTO course = getCourse(enrollment.getCourseId());
        List<ReviewRound> rounds = reviewRoundRepository.findByEnrollmentIdOrderByRoundNoDesc(enrollmentId);
        long completedLessons = progressService.getLessonStatuses(enrollmentId).stream()
                .filter(LessonProgressDTO::isCompleted)
                .count();
        Long firstLessonId = courseService.getLessonsOfCourse(enrollment.getCourseId()).stream()
                .findFirst()
                .map(Lesson::getId)
                .orElse(null);
        AssignmentViewDTO view = AssignmentViewDTO.of(enrollment, course, courseService.getAssignmentTitle(enrollment.getCourseId()),
                firstLessonId, rounds, completedLessons, ProgressService.REQUIRED_LESSONS,
                submissionPolicy.isPeriodOpen(enrollment, now), progressService.isAllCompleted(enrollmentId), now);
        workService.findFinalWork(userId, enrollmentId).ifPresent(view::setSubmittedWork);   // 제출한 뒤에 무엇을 냈는지 화면에서 확인
        return view;
    }

    /**
     * 최초 제출 → 1회차 평가 대기(마감 = 제출 + 7일), 담당 강사에게 알림. 수료가 아니다.
     * 호출: AssignmentController.submit (POST /assignments, API AS-02)
     * @return 새 평가 회차 id
     * @throws BusinessException ENROLLMENT_NOT_FOUND, ALREADY_SUBMITTED, INVALID_REVIEW_STATE, ENROLLMENT_EXPIRED,
     *                           LESSONS_NOT_COMPLETED, INVALID_IMAGE_COUNT, (WorkService) INVALID_IMAGE_FILE·FILE_TOO_LARGE·FILE_SAVE_FAILED
     * 트랜잭션: 수강 건을 잠근 뒤 회차를 잠금 읽기 → 규칙 확인 → 파일·작품 저장 → 회차 저장 → 알림 (연타해도 평가 대기 1건)
     */
    @Transactional
    public Long submit(Long userId, Long enrollmentId, AssignmentSubmitDTO dto, List<MultipartFile> images) {
        LocalDateTime now = LocalDateTime.now();
        Enrollment enrollment = enrollmentService.lockOwnedEnrollment(userId, enrollmentId);
        List<ReviewRound> rounds = reviewRoundRepository.findByEnrollmentIdForUpdate(enrollmentId);
        List<MultipartFile> files = selectedFiles(images);

        submissionPolicy.checkCanSubmit(enrollment, rounds, files.size(), now);
        return saveNewRound(userId, enrollment, 1, dto, files, now, SUBMITTED_TITLE);
    }

    /**
     * 반려 후 정식 재제출 → 새 버전 + 새 회차(직전 + 1회차, 새 7일 마감 — DB 확정 11-06). 횟수 제한 없음.
     * 호출: AssignmentController.resubmit (POST /assignments/resubmit, API AS-04)
     * @return 새 평가 회차 id
     * @throws BusinessException ENROLLMENT_NOT_FOUND, ALREADY_SUBMITTED, RESUBMIT_NOT_ALLOWED, ENROLLMENT_EXPIRED,
     *                           INVALID_IMAGE_COUNT, (WorkService) 이미지 관련 코드
     */
    @Transactional
    public Long resubmit(Long userId, Long enrollmentId, AssignmentSubmitDTO dto, List<MultipartFile> images) {
        LocalDateTime now = LocalDateTime.now();
        Enrollment enrollment = enrollmentService.lockOwnedEnrollment(userId, enrollmentId);
        List<ReviewRound> rounds = reviewRoundRepository.findByEnrollmentIdForUpdate(enrollmentId);
        List<MultipartFile> files = selectedFiles(images);

        submissionPolicy.checkCanResubmit(enrollment, rounds, files.size(), now);
        return saveNewRound(userId, enrollment, rounds.get(0).nextRoundNo(), dto, files, now, RESUBMITTED_TITLE);
    }

    /**
     * 평가 시작 전 수정 — 같은 버전을 고친다(새 버전·새 회차를 만들지 않음). 회차 행을 바꾸지 않으므로 마감이 그대로다.
     * 호출: AssignmentController.edit (POST /assignments/{reviewRoundId}/edit, API AS-03)
     * @param images 비어 있으면 기존 이미지 유지, 있으면 전체 교체(1~5장)
     * @return 이 회차의 수강 건 id (수정 뒤 돌아갈 화면)
     * @throws BusinessException REVIEW_NOT_FOUND(없는 회차), ENROLLMENT_NOT_FOUND(남의 회차 — 둘 다 404로 존재를 숨김),
     *                           SUBMISSION_LOCKED(평가 시작됨), INVALID_REVIEW_STATE, ENROLLMENT_EXPIRED, INVALID_IMAGE_COUNT
     */
    @Transactional
    public Long edit(Long userId, Long reviewRoundId, AssignmentSubmitDTO dto, List<MultipartFile> images) {
        LocalDateTime now = LocalDateTime.now();
        // 회차를 먼저 잠근다 — 강사의 평가 시작(#14)과 겹치면 늦게 온 쪽이 바뀐 상태를 본다
        ReviewRound round = reviewRoundRepository.findByIdForUpdate(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, round.getEnrollmentId());
        List<MultipartFile> files = selectedFiles(images);

        submissionPolicy.checkCanEdit(enrollment, round, files.size(), now);
        workService.updateFinalWorkVersion(userId, round.getWorkVersionId(), dto.getTitle(), dto.getDescription(), files);
        return enrollment.getId();
    }

    /**
     * 새 회차 저장 공통(최초 제출·재제출).
     * 순서 이유: 규칙 확인이 끝난 뒤에만 파일을 저장한다(규칙 위반이면 파일이 생기지 않음). 회차는 저장 즉시 flush해서
     * DB 제약(UNIQUE·CHECK) 위반이 알림 전에 드러나게 한다 — 어느 단계든 실패하면 트랜잭션 전체가 롤백된다.
     */
    private Long saveNewRound(Long userId, Enrollment enrollment, int roundNo, AssignmentSubmitDTO dto,
                              List<MultipartFile> files, LocalDateTime now, String notifyTitle) {
        CourseBriefDTO course = getCourse(enrollment.getCourseId());
        // 계약 C8: 작품(FINAL)·새 버전·이미지 저장은 WorkService 한곳에서만 — 실패 시 저장한 파일을 지우고 예외(팀원 4)
        Long workVersionId = workService.saveFinalWorkVersion(userId, enrollment.getId(), course.getHobbyId(),
                course.getLevel(), dto.getTitle(), dto.getDescription(), files);
        ReviewRound round = reviewRoundRepository.saveAndFlush(
                ReviewRound.create(enrollment.getId(), workVersionId, roundNo, now));

        // 담당 강사가 없는 분반(D-52)도 제출은 계속 가능하다(Q-05) — 알림 받을 사람이 없으면 알림만 건너뛴다
        Long instructorId = sectionService.getCurrentInstructorId(enrollment.getId());
        if (instructorId != null) {
            notificationService.notify(instructorId, NotificationKind.REVIEW_SUBMITTED, round.getId(), notifyTitle,
                    course.getTitle() + " 최종 과제가 제출되었습니다. 7일 안에 평가해 주세요.");
        }
        return round.getId();
    }

    /** 강의 한 줄 정보(취미·수준·강의명). 본인 수강 건으로 이미 권한을 확인한 뒤라 노출 규칙 없는 조회를 쓴다 */
    private CourseBriefDTO getCourse(Long courseId) {
        CourseBriefDTO course = courseService.getCourseBriefs(List.of(courseId)).get(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /**
     * 실제로 고른 파일만 남긴다. 파일 칸을 비워 보내도 브라우저는 빈 파일 1개를 보내므로, 그대로 세면 "0장"이 "1장"이 된다.
     */
    private List<MultipartFile> selectedFiles(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }
}
