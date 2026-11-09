package com.wanted.lmspractice.domain.instructor.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.service.InstructorStatService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 강사 통계(T-S05) 화면 연결 — 현재 숫자만(D-58). 새로고침 버튼은 같은 URL을 다시 GET한다.
 * [담당] 팀원 1 · #07   [화면] T-S05   [기획] FR-051, D-58   [API] IN-04
 * [연결]
 *  - 호출받음: 브라우저 GET /instructor/stats (INSTRUCTOR만 — 학습자는 SecurityConfig가 403)
 *  - 호출함:   InstructorStatService.getSectionStats(본인 확인 포함), summarize
 *  - 뷰:       templates/instructor/stats.html
 */
@Controller
@RequestMapping("/instructor/stats")
@RequiredArgsConstructor
public class InstructorStatController {

    private final InstructorStatService instructorStatService;

    /** 모델: stats(분반별 줄), total(합계 — 수강 중·평가 대기·수료·반려) */
    @GetMapping
    public String stats(@AuthenticationPrincipal AuthDetails details, Model model) {
        Long me = details.getUserId();
        List<SectionStatDTO> stats = instructorStatService.getSectionStats(me, details.getRole(), me);
        model.addAttribute("stats", stats);
        model.addAttribute("total", instructorStatService.summarize(stats));
        return "instructor/stats";
    }
}
