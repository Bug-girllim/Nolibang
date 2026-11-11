package com.wanted.lmspractice.domain.course.service;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.dto.LessonDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SeatService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.learning.policy.ContinueLessonPolicy;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 탐색 화면에 필요한 "강의 + 정원·대기 + 내 신청 상태"를 한데 모아 화면용 DTO로 조립한다 (조회 전용)
 * [담당] 팀원 2 · #08   [화면] U-S02·U-S03·U-S04, A-S15   [API] HB-02·HB-03·CO-01·CO-02
 * [연결]
 *  - 호출받음: HobbyController(취미 상세), CourseController(강의 목록·상세), AdminHobbyController(관리자 집계),
 *             AdminSectionController·AdminWaitlistController(#10 관리자 강의 선택 — getAdminCourses)
 *  - 호출함:   HobbyService(취미·노출 규칙), CourseService(강의 데이터),
 *             SeatService.getSeatSummary·getSeatSummaries(정원·대기 — 수강 도메인 조회 계약, 여러 강의는 일괄 조회),
 *             EnrollmentService.getStatusesForUser(내 신청 상태 — #09 계약 C17과 같은 규칙의 일괄 조회),
 *             ProgressService.getLessonStatuses(차시 진도 — 계약 C10, #12: [이어서 학습]이 열 차시 고르기)
 *  - 왜 따로 두나: CourseService가 수강 도메인을 직접 부르면, #09에서 EnrollmentService가 강의·취미 확인을 위해 CourseService를 쓰는 순간
 *             서로를 주입하는 순환이 되어 앱이 뜨지 않는다. 강의·취미(위쪽)와 수강(아래쪽)을 이 클래스 한 곳에서만 연결해
 *             의존 방향을 course → hobby, catalog → enrollment로 고정한다. (이슈는 CourseService.getCourseDetail에 두었으나 같은 효과를
 *             내면서 순환을 막으려고 조립만 이 클래스로 옮겼다 — 보고 사항)
 * [기술] 🟢 @Service·@Transactional(readOnly)
 */
@Service
@RequiredArgsConstructor
public class CourseCatalogService {

    private final HobbyService hobbyService;
    private final CourseService courseService;
    private final SeatService seatService;
    private final EnrollmentService enrollmentService;
    private final ProgressService progressService;

    /**
     * 한 세부 취미의 강의 목록 + 정원·대기·내 신청 상태 (U-S03).
     * 호출: CourseController.list (GET /courses?hobbyId=&level=, API CO-01)
     *
     * @param levelOrNull 수준 필터 — null이면 전체
     * @param userIdOrNull 로그인한 회원 id — 비회원이면 null
     * @param viewerRole   보는 사람의 역할 — 비회원이면 null
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getCourses(Long hobbyId, CourseLevel levelOrNull, Long userIdOrNull, Role viewerRole) {
        boolean hobbyOperating = hobbyService.getHobbySummary(hobbyId, viewerRole).isOperating();
        List<CourseDTO> courses = courseService.getCourses(hobbyId, levelOrNull, viewerRole);
        // [성능 개선] 정원 현황·신청 상태를 강의마다 따로 읽지 않고 한 번에 읽는다 (강의 수와 관계없이 쿼리 수 고정)
        List<Long> courseIds = courses.stream().map(CourseDTO::getId).toList();
        Map<Long, SeatSummaryDTO> seats = seatService.getSeatSummaries(courseIds);
        Map<Long, EnrollmentStatusDTO> statuses = statusesFor(courseIds, seats, hobbyOperating, userIdOrNull, viewerRole);
        for (CourseDTO course : courses) {
            course.setSeats(seats.get(course.getId()));
            course.setEnrollStatus(statuses.get(course.getId()));
            if (isLearningOrCompleted(course.getEnrollStatus())) {  // 그 밖의 카드는 차시를 조회하지 않는다
                course.setContinueLessonId(continueLessonId(course.getEnrollStatus(),
                        courseService.getLessonsOfCourse(course.getId()).stream().map(Lesson::getId).toList()));
            }
        }
        return courses;
    }

    /**
     * 세부 취미 상세 — 기본 정보에 수준(입문·중급·고급)별 강의 카드와 신청 상태를 붙인다 (U-S02).
     * 호출: HobbyController.detail (GET /hobbies/{hobbyId}, API HB-02)
     */
    @Transactional(readOnly = true)
    public HobbyDetailDTO getHobbyDetail(Long hobbyId, Long userIdOrNull, Role viewerRole) {
        HobbyDetailDTO hobby = hobbyService.getHobby(hobbyId, viewerRole);
        hobby.setLevels(getCourses(hobbyId, null, userIdOrNull, viewerRole));
        return hobby;
    }

    /**
     * 강의 상세 — 목표·준비물·차시에 정원 현황(잔여 정원·대기 인원)을 붙인다 (U-S04).
     * 호출: CourseController.detail (GET /courses/{courseId}, API CO-02) — 비회원도 호출한다(D-08)
     */
    @Transactional(readOnly = true)
    public CourseDetailDTO getCourseDetail(Long courseId, Role viewerRole) {
        CourseDetailDTO course = courseService.getCourseDetail(courseId, viewerRole);
        course.setSeats(seatService.getSeatSummary(courseId));
        return course;
    }

    /** 기록이 있는 학습자에게 보여 주는 강의 상세와 그 학습자의 신청 상태 (getEnrolledCourseView 결과) */
    public record EnrolledCourseView(CourseDetailDTO course, EnrollmentStatusDTO status) { }

    /**
     * 신규 탐색에는 숨겨진 강의(닫힌 강의, 준비 중·비공개 취미의 강의)를 그 강의에 수강·대기 기록이 있는 학습자에게만 보여 준다 (D-20).
     * 나의 학습 카드·학습 화면 사이드 메뉴가 강의 상세로 연결되는데, 강의가 숨겨지면 이 링크가 404가 되던 문제를 막는다.
     * 기록이 없으면 지금처럼 COURSE_NOT_FOUND — 신규 탐색(목록·취미 상세·신청)은 그대로 막힌다.
     * 신청 상태는 노출 규칙을 거치지 않고 학습자의 기록 그대로(수강 중·수료·대기 n번·재신청 불가)를 쓴다.
     * 호출: CourseController.detail — getCourseDetail 이 COURSE_NOT_FOUND 일 때만 (학습자 계정)
     *
     * @throws BusinessException COURSE_NOT_FOUND — 없는 강의이거나 이 학습자에게 수강·대기 기록이 없음
     */
    @Transactional(readOnly = true)
    public EnrolledCourseView getEnrolledCourseView(Long courseId, Long userId) {
        EnrollmentStatusDTO status = enrollmentService.getStatusForUser(userId, courseId);
        if (!hasRecord(status)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        CourseDetailDTO course = courseService.getCourseDetailForEnrolled(courseId);
        course.setSeats(seatService.getSeatSummary(courseId));
        return new EnrolledCourseView(course, status);
    }

    /** 수강·대기 기록이 있는 상태인지 — 기록이 없으면 정원 현황으로 정한 상태(신청 가능·대기 신청·신청 불가)가 나온다 */
    private boolean hasRecord(EnrollmentStatusDTO status) {
        return switch (status.getState()) {
            case EnrollmentStatusDTO.LEARNING, EnrollmentStatusDTO.COMPLETED,
                 EnrollmentStatusDTO.WAITING, EnrollmentStatusDTO.CANNOT_REAPPLY -> true;
            default -> false;
        };
    }

    /**
     * 강의 상세의 신청 영역 상태 (CO-02 모델 enrollStatus). 호출: CourseController.detail
     */
    @Transactional(readOnly = true)
    public EnrollmentStatusDTO getEnrollStatus(CourseDetailDTO course, Long userIdOrNull, Role viewerRole) {
        boolean hobbyOperating = hobbyService.getHobbySummary(course.getHobbyId(), viewerRole).isOperating();
        // [성능 개선] getCourseDetail이 이미 읽은 정원 현황을 다시 쓴다(같은 요청에서 정원 현황을 두 번 읽지 않음)
        Map<Long, SeatSummaryDTO> knownSeats = course.getSeats() == null ? Map.of() : Map.of(course.getId(), course.getSeats());
        return statusesFor(List.of(course.getId()), knownSeats, hobbyOperating, userIdOrNull, viewerRole).get(course.getId());
    }

    /**
     * 관리자 강의 선택 목록 — 전체 강의(닫힌 강의 포함)에 정원 현황(남은 자리·대기 인원)을 붙인다 (#10 SE-01·WL-01 모델 courses).
     * 호출: AdminSectionController.list, AdminWaitlistController.list
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getAdminCourses() {
        List<CourseDTO> courses = courseService.getAllCoursesForAdmin();
        // [성능 개선] 전체 강의의 정원 현황을 한 번에 읽는다
        Map<Long, SeatSummaryDTO> seats = seatService.getSeatSummaries(courses.stream().map(CourseDTO::getId).toList());
        courses.forEach(course -> course.setSeats(seats.get(course.getId())));
        return courses;
    }

    /**
     * 강의 상세의 [이어서 학습]·[다시 보기]가 열 차시 id — 수강 중·수료 상태가 아니면 null.
     * 강의 상세는 이미 차시 목록(1·2·3차시 순서)을 가지고 있어 그것을 순서 기준으로 쓴다.
     * 호출: CourseController.detail
     */
    @Transactional(readOnly = true)
    public Long getContinueLessonId(CourseDetailDTO course, EnrollmentStatusDTO enrollStatus) {
        return continueLessonId(enrollStatus, course.getLessons().stream().map(LessonDTO::getId).toList());
    }

    /**
     * 관리자 취미 조회(A-S15) — 비공개 포함 전체 대분류·세부 취미에 공개/전체 강의 수와 차시 수를 붙인다 (조회 전용, D-59).
     * 호출: AdminHobbyController.list (GET /admin/hobbies, API HB-03)
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAdminCategories() {
        List<CategoryDTO> categories = hobbyService.getCategories(Role.ADMIN);
        categories.forEach(category -> category.getHobbies().forEach(courseService::fillCourseCounts));
        return categories;
    }

    /**
     * 수강 중이거나 수료한 학습자에게만, 이어서 볼 차시를 고른다(ContinueLessonPolicy).
     * 진도(C10)는 "끝낸 차시인지"만 확인하는 데 쓰고, 차시 순서는 강의의 전체 차시 목록에서 받는다.
     */
    private Long continueLessonId(EnrollmentStatusDTO status, List<Long> orderedLessonIds) {
        if (!isLearningOrCompleted(status) || status.getEnrollmentId() == null) {
            return null;
        }
        return ContinueLessonPolicy.pick(orderedLessonIds, progressService.getLessonStatuses(status.getEnrollmentId()));
    }

    /** 수강 중이거나 수료한 상태인지 — 영상을 볼 수 있는(D-42) 두 상태 */
    private boolean isLearningOrCompleted(EnrollmentStatusDTO status) {
        return EnrollmentStatusDTO.LEARNING.equals(status.getState())
                || EnrollmentStatusDTO.COMPLETED.equals(status.getState());
    }

    /**
     * 내 신청 상태를 정한다. #08의 노출 규칙을 먼저 적용하고, 학습자에게만 수강 도메인(#09)에 물어본다.
     *  1) 운영 중이 아닌 취미(준비 중)는 누구에게나 신청 불가 — 05 §3
     *  2) 비회원은 "로그인 후 신청"
     *  3) 강사·관리자는 신청 대상이 아니다(학습자 전용)
     */
    private Map<Long, EnrollmentStatusDTO> statusesFor(List<Long> courseIds, Map<Long, SeatSummaryDTO> seats,
                                                       boolean hobbyOperating, Long userIdOrNull, Role viewerRole) {
        String fixedState = null;
        if (!hobbyOperating) {
            fixedState = EnrollmentStatusDTO.UNAVAILABLE;
        } else if (userIdOrNull == null || viewerRole == null) {
            fixedState = EnrollmentStatusDTO.LOGIN_REQUIRED;
        } else if (viewerRole != Role.USER) {
            fixedState = EnrollmentStatusDTO.NOT_LEARNER;
        }
        if (fixedState == null) {
            // 학습자: 수강 도메인(#09)에 여러 강의를 한 번에 묻는다 (판단 규칙은 getStatusForUser와 같다)
            return enrollmentService.getStatusesForUser(userIdOrNull, courseIds, seats);
        }
        Map<Long, EnrollmentStatusDTO> statuses = new HashMap<>();
        for (Long courseId : courseIds) {
            statuses.put(courseId, EnrollmentStatusDTO.of(fixedState));  // 카드마다 따로 만든다(DTO가 변경 가능하므로 공유하지 않음)
        }
        return statuses;
    }
}
