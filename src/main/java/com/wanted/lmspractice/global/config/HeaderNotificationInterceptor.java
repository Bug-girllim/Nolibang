package com.wanted.lmspractice.global.config;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.notification.dto.HeaderNotificationDTO;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.View;

/**
 * [역할] 공통 머리글의 알림 값(unreadCount·headerNotifications)을 "화면을 그리는 요청"에만 넣는다 — 성능 개선(2단계)
 * [담당] 팀원 1 · #02 공통 모델에서 분리 (원래 GlobalModelAdvice의 @ModelAttribute 두 개)   [화면] 공통 헤더(common/layout.html gnb)   [API] §1-8
 * [연결]
 *  - 호출받음: Spring MVC (컨트롤러 실행 "후", 화면 렌더링 직전 postHandle), 등록 위치 WebConfig.addInterceptors
 *  - 호출함:   NotificationService.getHeaderSummary(두 값 모두 필요할 때 — 한 트랜잭션), countUnread·getHeaderNotifications(한쪽만 필요할 때) (#17)
 *  - 사용처:   templates/common/layout.html (gnb 알림 배지·미리보기), notification/list.html·instructor-list.html(unreadCount)
 * [왜 옮겼나]
 *  - @ControllerAdvice의 @ModelAttribute는 컨트롤러 실행 "전"에 모든 요청에서 돈다. 그래서 화면을 그리지 않는 요청
 *    (POST 후 redirect, @ResponseBody·ResponseEntity — 영상 구간 요청·진도 보고·좋아요·파일 미리보기 등)에서도
 *    로그인 사용자마다 알림 조회 2번(안 읽은 수 + 최근 5건)이 실행됐다.
 *  - postHandle은 컨트롤러가 돌려준 ModelAndView를 보고 판단할 수 있다: 뷰를 그릴 때만 조회하고, redirect·forward·본문 응답이면 건너뛴다.
 *  - 값은 같다: 화면을 그리면서 알림을 읽음 처리하는 GET 화면이 없으므로(읽음 처리는 redirect하는 open·readAll뿐) 컨트롤러 전/후 어느 쪽에서 세어도 같다.
 * [주의] 컨트롤러가 같은 이름으로 model.addAttribute 하면 그 값을 그대로 둔다(기존 @ModelAttribute와 같은 우선순위).
 *        비로그인은 기존과 같이 0 / 빈 목록을 넣는다(DB 조회 없음).
 *        예외 처리 화면(GlobalExceptionHandler의 ModelAndView)에는 기존에도 이 값들이 들어가지 않았다 — 그대로 둔다.
 * [기술] 🟢 HandlerInterceptor.postHandle + WebMvcConfigurer (chap05)
 */
@Component
@RequiredArgsConstructor
public class HeaderNotificationInterceptor implements HandlerInterceptor {

    static final String UNREAD_COUNT = "unreadCount";
    static final String HEADER_NOTIFICATIONS = "headerNotifications";

    private final NotificationService notificationService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (!rendersView(modelAndView)) {
            return;  // 본문 응답(@ResponseBody·ResponseEntity)·redirect·forward — 머리글을 그리지 않으므로 조회하지 않는다
        }
        Long userId = loginUserId();
        boolean needCount = !modelAndView.getModel().containsKey(UNREAD_COUNT);
        boolean needList = !modelAndView.getModel().containsKey(HEADER_NOTIFICATIONS);
        if (userId != null && needCount && needList) {
            // [성능 개선] 둘 다 필요하면(대부분의 화면) 한 트랜잭션으로 같이 읽는다 — 따로 부르면 COMMIT 왕복이 1번 더 든다
            HeaderNotificationDTO summary = notificationService.getHeaderSummary(userId);
            modelAndView.addObject(UNREAD_COUNT, summary.getUnreadCount());
            modelAndView.addObject(HEADER_NOTIFICATIONS, summary.getNotifications());
            return;
        }
        if (needCount) {
            modelAndView.addObject(UNREAD_COUNT, userId == null ? 0L : notificationService.countUnread(userId));
        }
        if (needList) {
            modelAndView.addObject(HEADER_NOTIFICATIONS,
                    userId == null ? List.of() : notificationService.getHeaderNotifications(userId));
        }
    }

    /** 이 요청이 화면(뷰)을 그리는지 — ModelAndView가 없거나(본문 응답) redirect:·forward:면 그리지 않는다 */
    static boolean rendersView(ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.wasCleared()) {
            return false;
        }
        String viewName = modelAndView.getViewName();
        if (viewName != null) {
            return !viewName.startsWith("redirect:") && !viewName.startsWith("forward:");
        }
        View view = modelAndView.getView();
        return view != null && !(view instanceof SmartView smartView && smartView.isRedirectView());
    }

    /** 로그인 사용자 id — 비로그인이면 null (GlobalModelAdvice와 같은 AuthDetails를 세션 인증 정보에서 읽는다) */
    private Long loginUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
