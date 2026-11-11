package com.wanted.lmspractice.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.notification.dto.HeaderNotificationDTO;
import com.wanted.lmspractice.domain.notification.dto.NotificationDTO;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.dto.LoginUserDTO;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.controller.GlobalModelAdvice;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * [역할] 성능 개선(2단계) 회귀 테스트 — 머리글 알림 값(unreadCount·headerNotifications)을 화면을 그리는 요청에만 조회하는지,
 *        화면에 들어가는 값·이름·우선순위는 기존 GlobalModelAdvice와 같은지 확인한다
 * [전제] DB·Spring 컨텍스트 없이 MockMvc standalone(실제 DispatcherServlet 흐름) + 가짜 NotificationService.
 *        NotificationService 호출 1번 = SQL 1번(countUnread → COUNT, getHeaderNotifications → 최근 5건 SELECT).
 */
class HeaderNotificationInterceptorTest {

    private static final Long USER_ID = 2L;

    private NotificationService notificationService;
    private MockMvc mvc;
    private final List<NotificationDTO> latest = List.of(new NotificationDTO());

    @Controller
    static class TestController {
        @GetMapping("/t/page")
        String page() {
            return "t/page";
        }

        @GetMapping("/t/page-own-count")
        String pageWithOwnCount(Model model) {
            model.addAttribute("unreadCount", 99L);   // 컨트롤러 값이 우선해야 한다
            return "t/page";
        }

        @PostMapping("/t/redirect")
        String redirect() {
            return "redirect:/t/page";
        }

        @GetMapping("/t/redirect-view")
        ModelAndView redirectView() {
            return new ModelAndView(new RedirectView("/t/page"));
        }

        @GetMapping("/t/forward")
        String forward() {
            return "forward:/t/page";
        }

        @GetMapping("/t/json")
        @ResponseBody
        long json() {
            return 1L;
        }

        @GetMapping("/t/entity")
        ResponseEntity<String> entity() {
            return ResponseEntity.ok("bytes");   // 영상 구간·파일 미리보기와 같은 본문 응답
        }
    }

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        when(notificationService.countUnread(USER_ID)).thenReturn(3L);
        when(notificationService.getHeaderNotifications(USER_ID)).thenReturn(latest);
        when(notificationService.getHeaderSummary(USER_ID)).thenReturn(new HeaderNotificationDTO(3L, latest));
        View noOpView = (model, request, response) -> { };
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalModelAdvice())
                .addInterceptors(new HeaderNotificationInterceptor(notificationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                // 실제 ThymeleafViewResolver처럼 redirect: 는 RedirectView로, 그 밖의 이름은 화면(여기서는 아무것도 그리지 않는 뷰)으로
                .setViewResolvers((viewName, locale) -> viewName.startsWith("redirect:")
                        ? new RedirectView(viewName.substring("redirect:".length()))
                        : noOpView)
                .build();
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsLearner() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getUserId()).thenReturn(USER_ID);
        when(details.getLoginUser()).thenReturn(new LoginUserDTO(USER_ID, "learner", "차분한수달", Role.USER, null, false));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @Test
    @DisplayName("로그인 + 화면 렌더링: 기존과 같은 이름·값(loginUser·unreadCount·headerNotifications)이 모델에 들어간다 — 한 트랜잭션(getHeaderSummary) 1번")
    void loggedIn_renderedPage_hasSameModel() throws Exception {
        loginAsLearner();

        MvcResult result = mvc.perform(get("/t/page")).andExpect(status().isOk()).andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("unreadCount")).isEqualTo(3L);
        assertThat(model.get("headerNotifications")).isSameAs(latest);
        assertThat(((LoginUserDTO) model.get("loginUser")).getUserId()).isEqualTo(USER_ID);
        verify(notificationService, times(1)).getHeaderSummary(USER_ID);
        verify(notificationService, never()).countUnread(anyLong());              // 따로 부르면 트랜잭션·COMMIT 이 2번
        verify(notificationService, never()).getHeaderNotifications(anyLong());
    }

    @Test
    @DisplayName("로그인 + POST 후 redirect / RedirectView / forward: 알림을 조회하지 않는다 (기존: 요청마다 2번)")
    void loggedIn_redirectOrForward_noQuery() throws Exception {
        loginAsLearner();

        mvc.perform(post("/t/redirect")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/t/redirect-view")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/t/forward")).andExpect(status().isOk());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("로그인 + @ResponseBody·ResponseEntity(JSON·영상·파일): 알림을 조회하지 않는다 (기존: 요청마다 2번)")
    void loggedIn_bodyResponse_noQuery() throws Exception {
        loginAsLearner();

        mvc.perform(get("/t/json")).andExpect(status().isOk());
        mvc.perform(get("/t/entity")).andExpect(status().isOk());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("컨트롤러가 같은 이름으로 넣은 값은 덮어쓰지 않고, 그 값은 조회하지도 않는다")
    void controllerValue_wins() throws Exception {
        loginAsLearner();

        mvc.perform(get("/t/page-own-count")).andExpect(model().attribute("unreadCount", 99L));

        verify(notificationService, never()).countUnread(anyLong());
        verify(notificationService, times(1)).getHeaderNotifications(USER_ID);
    }

    @Test
    @DisplayName("비로그인 화면: 기존과 같이 0 / 빈 목록, DB 조회 없음")
    void guest_defaults() throws Exception {
        mvc.perform(get("/t/page"))
                .andExpect(model().attribute("unreadCount", 0L))
                .andExpect(model().attribute("headerNotifications", List.of()))
                .andExpect(model().attribute("loginUser", (Object) null));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("rendersView 판별: 뷰 이름·뷰 객체·redirect·forward·본문 응답(null)")
    void rendersView_rules() {
        assertThat(HeaderNotificationInterceptor.rendersView(new ModelAndView("course/list"))).isTrue();
        assertThat(HeaderNotificationInterceptor.rendersView(new ModelAndView("redirect:/x"))).isFalse();
        assertThat(HeaderNotificationInterceptor.rendersView(new ModelAndView("forward:/x"))).isFalse();
        assertThat(HeaderNotificationInterceptor.rendersView(new ModelAndView(new RedirectView("/x")))).isFalse();
        assertThat(HeaderNotificationInterceptor.rendersView(null)).isFalse();
    }
}
