package com.wanted.lmspractice.domain.admin.controller;

import com.wanted.lmspractice.domain.admin.dto.AdminSummaryDTO;
import com.wanted.lmspractice.domain.admin.service.AdminDashboardService;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 관리자 대시보드(A-S01) 화면 연결 — 담당 공백 분반은 대시보드에서 바로 담당 강사를 지정할 수 있다
 * [담당] 팀원 1 · #07   [화면] A-S01   [기획] D-59   [API] AD-01
 * [연결]
 *  - 호출받음: 브라우저 GET /admin (SecurityConfig: ADMIN만)
 *  - 호출함:   AdminDashboardService.getSummary, SectionService.getSection·getCandidates(담당 공백 분반의 지정 가능한 강사)
 *  - 뷰:       templates/admin/home.html — [지정] 은 분반 관리와 같은 POST /admin/sections/{sectionId}/instructor (from=dashboard 로 대시보드 복귀)
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHomeController {

    private final AdminDashboardService adminDashboardService;
    private final SectionService sectionService;

    /** 모델: summary(AdminSummaryDTO), candidates(분반 id → 지정 가능한 강사 — 활성·그 강의 취미 권한 있는 강사) */
    @GetMapping
    public String home(Model model) {
        AdminSummaryDTO summary = adminDashboardService.getSummary();
        Map<Long, List<InstructorDTO>> candidates = new HashMap<>();
        for (AdminSummaryDTO.Alert alert : summary.getAlerts()) {
            if (alert.getSectionId() != null) {
                Long courseId = sectionService.getSection(alert.getSectionId()).getCourseId();
                candidates.put(alert.getSectionId(), sectionService.getCandidates(courseId));
            }
        }
        model.addAttribute("summary", summary);
        model.addAttribute("candidates", candidates);
        return "admin/home";
    }
}
