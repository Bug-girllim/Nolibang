package com.wanted.lmspractice.domain.admin.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.instructor.dto.InstructorStatSummaryDTO;
import com.wanted.lmspractice.domain.instructor.service.InstructorStatService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 관리자 강사 통계 조회(A-S13) — InstructorStatService 재사용. 조회 전용.
 * [담당] 팀원 1 · #07   [화면] A-S13   [기획] FR-051, D-58   [API] AD-02
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/stats?instructorId=&detailId= (ADMIN만)
 *  - 호출함:   InstructorStatService.getInstructorStats / getSectionStats / summarizeInstructors
 *  - 뷰:       templates/admin/stats.html
 * [동작] 시안의 "조회" 버튼 = instructorId 필터 GET, "분반별 보기" = detailId 링크, "닫기" = detailId 없는 링크
 */
@Controller
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatController {

    private final InstructorStatService instructorStatService;

    /**
     * 모델: instructors(선택 목록), rows(강사별 줄), total(합계), instructorId(필터 유지),
     *       detail(분반별 보기 대상 강사, 없으면 null), detailSections(그 강사의 분반별 숫자)
     */
    @GetMapping
    public String stats(@AuthenticationPrincipal AuthDetails details,
                        @RequestParam(required = false) Long instructorId,
                        @RequestParam(required = false) Long detailId,
                        Model model) {
        List<InstructorStatSummaryDTO> rows = instructorStatService.getInstructorStats(instructorId);
        model.addAttribute("instructors", instructorStatService.getAllInstructors());
        model.addAttribute("rows", rows);
        model.addAttribute("total", instructorStatService.summarizeInstructors(rows));
        model.addAttribute("instructorId", instructorId);

        if (detailId != null) {
            List<InstructorStatSummaryDTO> detail = instructorStatService.getInstructorStats(detailId);
            if (!detail.isEmpty()) {   // 강사가 아닌 id는 상세를 열지 않는다
                model.addAttribute("detail", detail.get(0));
                model.addAttribute("detailSections",
                        instructorStatService.getSectionStats(details.getUserId(), details.getRole(), detailId));
            }
        }
        return "admin/stats";
    }
}
