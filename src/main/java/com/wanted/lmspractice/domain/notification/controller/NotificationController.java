package com.wanted.lmspractice.domain.notification.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.notification.dto.NotificationPageDTO;
import com.wanted.lmspractice.domain.notification.dto.NotificationTab;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 학습자 알림함(U-S15), 강사 알림함(T-S09) — 사이트 알림만(D-57)
 * 담당: 팀원 4 · #17   [API] NT-01~NT-04
 * [연결] 호출함: NotificationService / 화면: notification/list(학습자), notification/instructor-list(강사)
 * [❓ 결정] API 명세 제안대로 URL은 /notifications 하나만 쓰고 역할(강사 여부)에 따라 화면을 고른다.
 *  헤더 종·강사 사이드 메뉴가 이미 /notifications를 가리킨다. 분리(/instructor/notifications)는 #01 확정 필요(API §10 #20).
 * [기술] 🟢 @Controller, redirect: + flash, @ResponseBody(NT-04)
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 알림함 (NT-01). 모델: notifications, page, totalPages, hasPrev, hasNext, tab, tabs, unreadCount */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails auth,
                       @RequestParam(required = false) String tab,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        boolean instructor = auth.getRole() == Role.INSTRUCTOR;
        NotificationTab current = NotificationTab.of(tab);
        NotificationPageDTO result = notificationService.getPage(auth.getUserId(), current, page);
        model.addAttribute("notifications", result.getItems());
        model.addAttribute("page", result.getPage());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("hasPrev", result.isHasPrev());
        model.addAttribute("hasNext", result.isHasNext());
        model.addAttribute("tab", current);
        model.addAttribute("tabs", instructor ? INSTRUCTOR_TABS : LEARNER_TABS);
        return instructor ? "notification/instructor-list" : "notification/list";
    }

    private static final NotificationTab[] LEARNER_TABS = {
        NotificationTab.ALL, NotificationTab.ENROLLMENT, NotificationTab.RESULT, NotificationTab.ACTIVITY};
    private static final NotificationTab[] INSTRUCTOR_TABS = {
        NotificationTab.ALL, NotificationTab.TEACHER_INQUIRY, NotificationTab.TEACHER_REVIEW, NotificationTab.TEACHER_OPERATION};

    /** 알림 열기 (NT-02): 읽음 처리 후 대상 화면으로. 대상이 사라졌으면 알림함으로 돌아가 안내한다. */
    @GetMapping("/{notificationId}")
    public String open(@AuthenticationPrincipal AuthDetails auth,
                       @PathVariable Long notificationId,
                       RedirectAttributes redirectAttributes) {
        return notificationService.open(auth.getUserId(), notificationId)
                .map(url -> "redirect:" + url)
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "알림이 가리키는 내용이 삭제되었거나 지금은 볼 수 없어요.");
                    return "redirect:/notifications";
                });
    }

    /** 모두 읽음 (NT-03) */
    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal AuthDetails auth, RedirectAttributes redirectAttributes) {
        int count = notificationService.readAll(auth.getUserId());
        redirectAttributes.addFlashAttribute("message", count + "개의 알림을 읽음으로 바꿨어요.");
        return "redirect:/notifications";
    }

    /** 안 읽은 알림 수 (NT-04, 💡 선택) — 헤더는 공통 모델 unreadCount를 쓰므로 필요할 때만 호출한다 */
    @GetMapping("/unread-count")
    @ResponseBody
    public long unreadCount(@AuthenticationPrincipal AuthDetails auth) {
        return notificationService.countUnread(auth.getUserId());
    }
}
