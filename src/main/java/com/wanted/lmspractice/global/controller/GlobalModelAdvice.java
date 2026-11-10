package com.wanted.lmspractice.global.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.dto.LoginUserDTO;

/**
 * [역할] 모든 화면이 공통으로 쓰는 모델 loginUser를 한 곳에서 넣는다. (DB를 읽지 않는 값만 여기에 둔다)
 * [담당] 팀원 1 · #02   [화면] 공통 헤더(common/layout.html gnb)   [API] §1-8
 * [연결]
 *  - 호출받음: Spring MVC (모든 @Controller 요청 직전, 오류 화면 포함)
 *  - 사용처:   templates/common/layout.html (gnb 조각), templates/main/home.html
 *  - [성능 개선 — 팀원 1 확인 필요] 알림 값 unreadCount·headerNotifications(DB 조회 2번)는 HeaderNotificationInterceptor로 옮겼다.
 *    @ModelAttribute는 redirect·JSON·영상 구간 요청처럼 화면을 그리지 않는 요청에서도 돌기 때문이다. 템플릿의 모델 이름·값은 그대로다.
 * [기술] 🟢 @ControllerAdvice, @ModelAttribute, @AuthenticationPrincipal
 * [주의] 컨트롤러가 같은 이름("loginUser" 등)으로 model.addAttribute 하면 그 값이 우선한다.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /** 비로그인이면 null → 헤더가 비로그인 메뉴를 보여 준다. 세션 DTO라 DB 조회는 하지 않는다. */
    @ModelAttribute("loginUser")
    public LoginUserDTO loginUser(@AuthenticationPrincipal AuthDetails authDetails) {
        return authDetails == null ? null : authDetails.getLoginUser();
    }
}
