package com.wanted.lmspractice.global.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.notification.dto.NotificationDTO;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.dto.LoginUserDTO;

import lombok.RequiredArgsConstructor;

/**
 * [역할] 모든 화면이 공통으로 쓰는 모델(loginUser·unreadCount·headerNotifications)을 한 곳에서 넣는다.
 * [담당] 팀원 1 · #02   [화면] 공통 헤더(common/layout.html gnb)   [API] §1-8
 * [연결]
 *  - 호출받음: Spring MVC (모든 @Controller 요청 직전, 오류 화면 포함)
 *  - 호출함:   NotificationService.countUnread·getHeaderNotifications (#17)
 *  - 사용처:   templates/common/layout.html (gnb 조각), templates/main/home.html
 * [기술] 🟢 @ControllerAdvice, @ModelAttribute, @AuthenticationPrincipal
 * [주의] 컨트롤러가 같은 이름("loginUser" 등)으로 model.addAttribute 하면 그 값이 우선한다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    /** 비로그인이면 null → 헤더가 비로그인 메뉴를 보여 준다. 세션 DTO라 DB 조회는 하지 않는다. */
    @ModelAttribute("loginUser")
    public LoginUserDTO loginUser(@AuthenticationPrincipal AuthDetails authDetails) {
        return authDetails == null ? null : authDetails.getLoginUser();
    }

    /** 안 읽은 알림 수. 비로그인은 0. */
    @ModelAttribute("unreadCount")
    public long unreadCount(@AuthenticationPrincipal AuthDetails authDetails) {
        return authDetails == null ? 0L : notificationService.countUnread(authDetails.getUserId());
    }

    /**
     * 헤더 알림 미리보기 목록 — 최근 5건(NotificationDTO: id·title·body·readAt·createdAt). 비로그인은 빈 목록.
     * [#17 변경 — 팀원 1 확인 필요] 스텁(빈 목록)을 NotificationService.getHeaderNotifications 호출로 바꿨다.
     */
    @ModelAttribute("headerNotifications")
    public List<NotificationDTO> headerNotifications(@AuthenticationPrincipal AuthDetails authDetails) {
        return authDetails == null ? List.of() : notificationService.getHeaderNotifications(authDetails.getUserId());
    }
}
