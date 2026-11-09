package com.wanted.lmspractice.domain.enrollment.controller;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 관리자 강의별 대기열 조회 화면(A-S12) — 조회 전용. 순번 변경·수동 확정 버튼은 없다(D-53·D-59).
 *        화면 제목의 "초대"는 구 기준 문구 — 초대·수락 없이 자리가 나면 1순위를 시스템이 즉시 확정한다.
 * [담당] 팀원 2 · #10   [화면] A-S12   [기획] FR-053, D-53   [API] WL-01
 * [연결]
 *  - 호출받음: 브라우저(GET /admin/waitlists) — ADMIN만(SecurityConfig /admin/**)
 *  - 호출함:   CourseCatalogService.getAdminCourses(강의별 대기 인원), WaitlistService(대기 목록·최근 확정),
 *             SectionService.getSectionsForAdmin(분반별 남은 자리)
 *  - 화면:     templates/admin/waitlists.html
 * [기술] 🟢 @Controller, @RequestParam, Model (강의 선택은 GET 폼)
 */
@Controller
@RequestMapping("/admin/waitlists")
@RequiredArgsConstructor
public class AdminWaitlistController {

    private final CourseCatalogService courseCatalogService;
    private final WaitlistService waitlistService;
    private final SectionService sectionService;

    /**
     * 강의별 대기열 (WL-01). courseId가 없으면 대기자가 있는 첫 강의(없으면 첫 강의)를 보여 준다 — 시안은 항상 한 강의가 선택된 화면.
     * 모델: courses, selectedCourse, waitlist, sections, recentConfirmed, recentConfirmedCount
     *
     * @throws com.wanted.lmspractice.global.exception.BusinessException COURSE_NOT_FOUND
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long courseId, Model model) {
        List<CourseDTO> courses = courseCatalogService.getAdminCourses();
        model.addAttribute("courses", courses);

        Long selectedId = courseId != null ? courseId : defaultCourseId(courses);
        if (selectedId == null) {
            return "admin/waitlists";  // 강의가 하나도 없음
        }
        model.addAttribute("sections", sectionService.getSectionsForAdmin(selectedId));  // 없는 강의면 COURSE_NOT_FOUND
        model.addAttribute("selectedCourse", courses.stream()
                .filter(course -> course.getId().equals(selectedId)).findFirst().orElse(null));
        model.addAttribute("waitlist", waitlistService.getWaitlist(selectedId));
        model.addAttribute("recentConfirmed", waitlistService.getRecentConfirmed(selectedId));
        model.addAttribute("recentConfirmedCount", waitlistService.countRecentConfirmed(selectedId));
        return "admin/waitlists";
    }

    /** 처음 들어왔을 때 보여 줄 강의 — 대기자가 있는 첫 강의, 없으면 첫 강의 */
    private static Long defaultCourseId(List<CourseDTO> courses) {
        return courses.stream()
                .filter(course -> course.getSeats() != null && course.getSeats().getWaitingCount() > 0)
                .findFirst()
                .or(() -> courses.stream().findFirst())
                .map(CourseDTO::getId)
                .orElse(null);
    }
}
