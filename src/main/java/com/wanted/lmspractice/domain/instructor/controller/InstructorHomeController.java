package com.wanted.lmspractice.domain.instructor.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.instructor.service.InstructorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 강사 홈·담당 분반(T-S01) 화면 연결
 * [담당] 팀원 1 · #07   [화면] T-S01   [기획] FR-051, 05 §7-0   [API] IN-01
 * [연결]
 *  - 호출받음: 브라우저 GET /instructor (SecurityConfig: INSTRUCTOR만)
 *  - 호출함:   InstructorDashboardService.getHome
 *  - 뷰:       templates/instructor/home.html
 * [기술] 🟢 @Controller, @GetMapping, @AuthenticationPrincipal, Model
 */
@Controller
@RequestMapping("/instructor")
@RequiredArgsConstructor
public class InstructorHomeController {

    private final InstructorDashboardService instructorDashboardService;

    /** 모델: home(InstructorHomeDTO) — 분반 표·KPI·기한 초과 경고 */
    @GetMapping
    public String home(@AuthenticationPrincipal AuthDetails details, Model model) {
        model.addAttribute("home", instructorDashboardService.getHome(details.getUserId()));
        return "instructor/home";
    }
}
