package com.wanted.lmspractice.domain.instructor.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.instructor.dto.SectionSummaryDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentStatus;
import com.wanted.lmspractice.domain.instructor.service.InstructorDashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 담당 학습자 관리(T-S02) 화면 연결 — 분반·이름·상태 필터는 GET 쿼리로 다시 그린다
 * [담당] 팀원 1 · #07   [화면] T-S02   [기획] 05 §7-0, NFR-04   [API] IN-02
 * [연결]
 *  - 호출받음: 브라우저 GET /instructor/students?sectionId=&name=&status= (INSTRUCTOR만)
 *  - 호출함:   InstructorDashboardService.getStudents / getAssignedSections (다른 분반 sectionId는 Service가 404로 차단)
 *  - 뷰:       templates/instructor/students.html
 * [기술] 🟢 @RequestParam, Model
 */
@Controller
@RequestMapping("/instructor/students")
@RequiredArgsConstructor
public class InstructorStudentController {

    private final InstructorDashboardService instructorDashboardService;

    /** 모델: students, sections(분반 선택), statuses(상태 선택), sectionId·name·status(입력값 유지) */
    @GetMapping
    public String students(@AuthenticationPrincipal AuthDetails details,
                           @RequestParam(required = false) Long sectionId,
                           @RequestParam(required = false) String name,
                           @RequestParam(required = false) StudentStatus status,
                           Model model) {
        Long instructorId = details.getUserId();
        // [성능 개선] 담당 분반 목록을 먼저 읽어 분반 선택과 "내 분반인지" 확인에 함께 쓴다(담당 분반 집계 2번 → 1번)
        List<SectionSummaryDTO> sections = instructorDashboardService.getAssignedSections(instructorId);
        model.addAttribute("students", instructorDashboardService.getStudents(instructorId, sections, sectionId, status, name));
        model.addAttribute("sections", sections);
        model.addAttribute("statuses", StudentStatus.values());
        model.addAttribute("sectionId", sectionId);
        model.addAttribute("name", name);
        model.addAttribute("status", status);
        return "instructor/students";
    }
}
