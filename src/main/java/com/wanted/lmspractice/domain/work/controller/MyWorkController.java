package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.work.dto.ModerationViewDTO;
import com.wanted.lmspractice.domain.work.service.WorkModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 내 작품 화면 — 일반 작품 등록(U-S11), 성장 타임라인(U-S12)은 #19, 관리자 조치 사유 확인(U-S16)은 #20
 * [담당] 팀원 4 · #19 (#20 은 U-S16 메서드만 추가)   [API] MW-08
 * [연결]
 *  - 호출받음: 브라우저 GET /my/works/{workId}/moderation (학습자 — SecurityConfig /my/**)
 *  - 호출함:   WorkModerationService.getAuthorView (본인 작품만, 숨김·복구 이력)
 * [참고] #19 구현 때 이 클래스에 서비스·핸들러를 이어서 추가한다(필드 추가 시 @RequiredArgsConstructor 가 생성자를 만들어 준다).
 */
@Controller
@RequestMapping("/my/works")
@RequiredArgsConstructor
public class MyWorkController {

    private final WorkModerationService workModerationService;

    /**
     * MW-08 관리자 조치 사유 확인 (U-S16) — 작성자 본인만, 숨김 중에도 작품 원문은 열람할 수 있다(Q-02)
     * @param log 표에서 고른 이력 id — 없으면 가장 최근 조치를 보여 준다
     * 모델: work(WorkDTO), logs(조치 이력 최신순), detail(상세로 보여 줄 이력 1건)
     */
    @GetMapping("/{workId}/moderation")
    public String moderation(@AuthenticationPrincipal AuthDetails details,
                             @PathVariable Long workId,
                             @RequestParam(required = false) Long log,
                             Model model) {
        ModerationViewDTO view = workModerationService.getAuthorView(details.getUserId(), workId);
        model.addAttribute("work", view.getWork());
        model.addAttribute("logs", view.getLogs());
        model.addAttribute("detail", view.getLogs().stream()
                .filter(l -> l.getId().equals(log)).findFirst().orElse(view.getLatest()));
        return "work/moderation";
    }
}
