package com.wanted.lmspractice.domain.course.service;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.SeatService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 탐색 화면에 필요한 "강의 + 정원·대기 + 내 신청 상태"를 한데 모아 화면용 DTO로 조립한다 (조회 전용)
 * [담당] 팀원 2 · #08   [화면] U-S02·U-S03·U-S04, A-S15   [API] HB-02·HB-03·CO-01·CO-02
 * [연결]
 *  - 호출받음: HobbyController(취미 상세), CourseController(강의 목록·상세), AdminHobbyController(관리자 집계)
 *  - 호출함:   HobbyService(취미·노출 규칙), CourseService(강의 데이터),
 *             SeatService.getSeatSummary(정원·대기 — 수강 도메인 조회 계약), EnrollmentService.getStatusForUser(내 신청 상태 — #09 계약)
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
        for (CourseDTO course : courses) {
            course.setSeats(seatService.getSeatSummary(course.getId()));
            course.setEnrollStatus(statusFor(course.getId(), hobbyOperating, userIdOrNull, viewerRole));
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

    /**
     * 강의 상세의 신청 영역 상태 (CO-02 모델 enrollStatus). 호출: CourseController.detail
     */
    @Transactional(readOnly = true)
    public EnrollmentStatusDTO getEnrollStatus(CourseDetailDTO course, Long userIdOrNull, Role viewerRole) {
        boolean hobbyOperating = hobbyService.getHobbySummary(course.getHobbyId(), viewerRole).isOperating();
        return statusFor(course.getId(), hobbyOperating, userIdOrNull, viewerRole);
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
     * 내 신청 상태를 정한다. #08의 노출 규칙을 먼저 적용하고, 학습자에게만 수강 도메인(#09)에 물어본다.
     *  1) 운영 중이 아닌 취미(준비 중)는 누구에게나 신청 불가 — 05 §3
     *  2) 비회원은 "로그인 후 신청"
     *  3) 강사·관리자는 신청 대상이 아니다(학습자 전용)
     */
    private EnrollmentStatusDTO statusFor(Long courseId, boolean hobbyOperating, Long userIdOrNull, Role viewerRole) {
        if (!hobbyOperating) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.UNAVAILABLE);
        }
        if (userIdOrNull == null || viewerRole == null) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.LOGIN_REQUIRED);
        }
        if (viewerRole != Role.USER) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.NOT_LEARNER);
        }
        return enrollmentService.getStatusForUser(userIdOrNull, courseId);
    }
}
