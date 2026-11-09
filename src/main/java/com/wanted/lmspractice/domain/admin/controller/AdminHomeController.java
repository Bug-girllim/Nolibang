package com.wanted.lmspractice.domain.admin.controller;

import com.wanted.lmspractice.domain.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 관리자 대시보드(A-S01) 화면 연결
 * [담당] 팀원 1 · #07   [화면] A-S01   [기획] D-59   [API] AD-01
 * [연결]
 *  - 호출받음: 브라우저 GET /admin (SecurityConfig: ADMIN만)
 *  - 호출함:   AdminDashboardService.getSummary
 *  - 뷰:       templates/admin/home.html
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHomeController {

    private final AdminDashboardService adminDashboardService;

    /** 모델: summary(AdminSummaryDTO) */
    @GetMapping
    public String home(Model model) {
        model.addAttribute("summary", adminDashboardService.getSummary());
        return "admin/home";
    }
}
