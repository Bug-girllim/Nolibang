package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.service.WorkBoardService;
import com.wanted.lmspractice.domain.work.service.WorkModerationService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 관리자 작품 관리 — 공개 작품 목록(A-S08 표 / A-S14 카드 게시판), 숨김·복구, 조치 이력
 * [담당] 팀원 4 · #20   [화면] A-S08, A-S14   [API] AW-01·AW-02·AW-03
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/works(?view=board 면 A-S14), POST /admin/works/{id}/hide, /restore (ADMIN만 — SecurityConfig /admin/**)
 *  - 호출함:   WorkBoardService.searchForAdmin(목록 재사용), WorkModerationService(요약·선택 패널·이력·숨김·복구)
 * [동작] 시안의 탭·검색·정렬·"상세 · 숨김" 선택은 GET 쿼리(hidden, tab=history, selected, keyword …)로 서버가 다시 그리고,
 *        숨김·복구 버튼은 POST + redirect(PRG) 로 처리한다.
 * [화면 규칙] A-S08(기본)은 hidden 이 없으면 "공개 중" 탭(hidden=false)을 연다. A-S14(view=board)는 없으면 전체(API 그대로).
 * [오류] 숨김·복구의 409/400 업무 오류는 API 명세의 "flash" 규칙대로 목록으로 되돌아가 errorMessage 로 안내하고,
 *        그 밖(404 없는 작품)은 공통 오류 화면(GlobalExceptionHandler)을 쓴다 — EnrollmentController 와 같은 방식.
 */
@Controller
@RequestMapping("/admin/works")
@RequiredArgsConstructor
public class AdminWorkController {

    public static final String VIEW_BOARD = "board";
    public static final String TAB_HISTORY = "history";

    private final WorkBoardService workBoardService;
    private final WorkModerationService workModerationService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /** AW-01 목록 — A-S08(표 + 선택 패널 + 조치 이력 탭) 또는 A-S14(카드 게시판) */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails details,
                       @ModelAttribute("cond") WorkSearchDTO cond,
                       @RequestParam(required = false) String view,
                       @RequestParam(required = false) String tab,
                       @RequestParam(required = false) Long selected,
                       Model model) {
        boolean board = VIEW_BOARD.equals(view);
        boolean history = !board && TAB_HISTORY.equals(tab);
        if (!board && cond.getHidden() == null) {
            cond.setHidden(false);   // A-S08 기본 탭 = 공개 중
        }
        model.addAttribute("summary", workModerationService.getSummary(details.getUserId()));
        model.addAttribute("hobbies", workBoardService.getHobbyOptions(Role.ADMIN));
        model.addAttribute("levels", CourseLevel.values());
        model.addAttribute("history", history);
        model.addAttribute("selected", selected);
        if (history) {
            model.addAttribute("logs", workModerationService.getAllHistory());
            return "admin/works";
        }
        model.addAttribute("works", workBoardService.searchForAdmin(cond, details.getUserId()));
        if (selected != null && !board) {
            model.addAttribute("target", workModerationService.getTarget(selected));
        }
        return board ? "admin/works-board" : "admin/works";
    }

    /** AW-02 공개 작품 숨김 — 사유 필수 */
    @PostMapping("/{workId}/hide")
    public String hide(@AuthenticationPrincipal AuthDetails details,
                       @PathVariable Long workId,
                       @RequestParam String reason,
                       @RequestParam(required = false) String view,
                       RedirectAttributes redirectAttributes) {
        workModerationService.hide(details.getUserId(), details.getRole(), workId, reason);
        redirectAttributes.addFlashAttribute("message", "작품을 숨겼습니다. 작성자에게 사유가 알림으로 전달돼요.");
        return backTo(view);
    }

    /** AW-03 숨김 복구 — 복구 사유는 선택(작성자 화면에 표시) */
    @PostMapping("/{workId}/restore")
    public String restore(@AuthenticationPrincipal AuthDetails details,
                          @PathVariable Long workId,
                          @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String view,
                          RedirectAttributes redirectAttributes) {
        workModerationService.restore(details.getUserId(), details.getRole(), workId, reason);
        redirectAttributes.addFlashAttribute("message", "숨김을 해제했습니다. 작성자가 공개로 둔 작품만 게시판에 다시 보여요.");
        return backTo(view);
    }

    /**
     * 업무 오류 처리 — API 의 flash 오류(이미 숨김·숨김 아님·입력 오류)는 목록으로 되돌려 errorMessage 로 안내하고,
     * 나머지(WORK_NOT_FOUND 404 등)는 공통 오류 화면 규칙을 그대로 쓴다.
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        ErrorCode code = e.getErrorCode();
        boolean flash = code == ErrorCode.ALREADY_HIDDEN || code == ErrorCode.NOT_HIDDEN || code == ErrorCode.INVALID_INPUT;
        if (!flash || !"POST".equals(request.getMethod())) {
            return globalExceptionHandler.handleBusiness(e, request);
        }
        redirectAttributes.addFlashAttribute("errorMessage", messageOf(code));
        return backTo(request.getParameter("view"));
    }

    private String messageOf(ErrorCode code) {
        return code == ErrorCode.INVALID_INPUT
                ? "숨김 사유는 " + WorkModerationService.MIN_HIDE_REASON_LENGTH + "자 이상 입력해 주세요. 비공개 작품은 숨길 수 없어요."
                : code.getMessage();
    }

    private String backTo(String view) {
        return VIEW_BOARD.equals(view) ? "redirect:/admin/works?view=board" : "redirect:/admin/works";
    }
}
