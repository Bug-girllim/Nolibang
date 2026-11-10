package com.wanted.lmspractice.domain.course.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 수준별 강의 목록(U-S03)·강의 상세(U-S04) 화면 — 조회 전용, 비회원도 강의 상세까지 열람(D-08)
 * [담당] 팀원 2 · #08 (신청 영역은 #09)   [화면] U-S03·U-S04   [기획] FR-010·013, D-08   [API] CO-01·CO-02
 * [연결]
 *  - 호출받음: 브라우저(GET /courses?hobbyId=&level=, GET /courses/{courseId}) — SecurityConfig가 /courses/**를 공개로 연다
 *  - 호출함:   HobbyService(강의 목록 머리의 취미·사이드 메뉴), CourseCatalogService(강의 + 정원·대기 + 내 신청 상태)
 *  - 이동:     강의 상세의 [수강 신청] → POST /enrollments (EnrollmentController, #09)
 *  - 화면:     templates/course/list.html · detail.html(신청 영역은 th:fragment "enrollArea"로 분리 — #09에서 채움)
 * [기술] 🟢 @Controller, @GetMapping, @PathVariable·@RequestParam, @ModelAttribute, @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final HobbyService hobbyService;
    private final CourseCatalogService courseCatalogService;

    /** 사이드 메뉴("취미 탐색")에 쓰는 대분류·세부 취미 목록. 이 컨트롤러의 모든 요청 앞에서 한 번 만든다 */
    @ModelAttribute("menuCategories")
    public List<CategoryDTO> menuCategories(@AuthenticationPrincipal AuthDetails details) {
        return hobbyService.getCategories(roleOf(details));
    }

    /** 강의 목록 한 덩어리 — 세부 취미 하나와 그 취미의 (수준 필터가 적용된) 강의들. 화면은 이 덩어리를 차례로 그린다 */
    public record CourseGroup(HobbyDTO hobby, List<CourseDTO> courses) { }

    /**
     * 수준별 강의 목록. hobbyId가 없으면 보이는 모든 세부 취미(전체 취미)의 강의를, 있으면 그 취미의 강의만 보여 준다.
     * level이 없으면 전체 수준. 수준 탭·세부 취미 선택은 서로의 선택(level·hobbyId)을 유지한 채 이동한다.
     * 호출: GET /courses?hobbyId=&level= (API CO-01)
     * 모델: groups(List&lt;CourseGroup&gt; — 취미별 강의, 정원·대기·내 신청 상태 포함), hobby(HobbyDTO, 전체 취미면 null),
     *       selectedLevel(CourseLevel 또는 null), levels(수준 탭용)
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long hobbyId,
                       @RequestParam(required = false) CourseLevel level,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model) {
        Role role = roleOf(details);
        Long userId = userIdOf(details);
        List<CourseGroup> groups;
        HobbyDTO hobby = null;
        Long sideCategoryId = null;
        if (hobbyId != null) {
            hobby = hobbyService.getHobbySummary(hobbyId, role);  // 보이지 않는 취미면 HOBBY_NOT_FOUND
            groups = List.of(new CourseGroup(hobby, courseCatalogService.getCourses(hobbyId, level, userId, role)));
            sideCategoryId = hobby.getCategoryId();
        } else {
            // 전체 취미: 사이드 메뉴와 같은 "보이는 세부 취미"만, 메뉴 순서대로
            // [성능 개선] @ModelAttribute("menuCategories")가 같은 역할로 이미 만든 목록을 다시 쓴다(getCategories 중복 조회 제거)
            groups = menuCategoriesOf(model).stream()
                    .flatMap(category -> category.getHobbies().stream())
                    .map(item -> new CourseGroup(hobbyService.getHobbySummary(item.getId(), role),
                            courseCatalogService.getCourses(item.getId(), level, userId, role)))
                    .filter(group -> !group.courses().isEmpty())
                    .toList();
        }
        model.addAttribute("groups", groups);
        model.addAttribute("hobby", hobby);
        model.addAttribute("selectedLevel", level);
        model.addAttribute("levels", CourseLevel.values());
        model.addAttribute("sideActive", "courses");
        model.addAttribute("sideCategoryId", sideCategoryId);
        model.addAttribute("sideHobbyId", hobbyId);
        return "course/list";
    }

    /**
     * 강의 상세 — 목표·준비물·필수 3차시·최종 과제·수강 기간·정원 현황. 없는 강의는 COURSE_NOT_FOUND(404).
     * 호출: GET /courses/{courseId} (API CO-02)
     * 모델: course(CourseDetailDTO), enrollStatus(EnrollmentStatusDTO — 신청 영역),
     *       continueLessonId(이어서 학습·다시 보기가 열 차시 id — 수강 중·수료일 때만, 그 밖에는 null)
     */
    @GetMapping("/{courseId}")
    public String detail(@PathVariable Long courseId,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model) {
        CourseDetailDTO course = courseCatalogService.getCourseDetail(courseId, roleOf(details));
        EnrollmentStatusDTO enrollStatus = courseCatalogService.getEnrollStatus(course, userIdOf(details), roleOf(details));
        model.addAttribute("course", course);
        model.addAttribute("enrollStatus", enrollStatus);
        model.addAttribute("continueLessonId", courseCatalogService.getContinueLessonId(course, enrollStatus));
        model.addAttribute("sideActive", "course");
        model.addAttribute("sideHobbyId", course.getHobbyId());
        return "course/detail";
    }

    /** 이 요청 앞에서 menuCategories(@ModelAttribute)가 모델에 넣어 둔 사이드 메뉴 목록 */
    @SuppressWarnings("unchecked")
    private List<CategoryDTO> menuCategoriesOf(Model model) {
        return (List<CategoryDTO>) model.getAttribute("menuCategories");
    }

    /** 비회원이면 null */
    private Role roleOf(AuthDetails details) {
        return details == null ? null : details.getRole();
    }

    /** 비회원이면 null */
    private Long userIdOf(AuthDetails details) {
        return details == null ? null : details.getUserId();
    }
}
