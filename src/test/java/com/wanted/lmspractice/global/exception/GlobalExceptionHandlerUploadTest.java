package com.wanted.lmspractice.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;

/**
 * [역할] GlobalExceptionHandler 의 업로드 용량 초과 처리 단위 테스트 — 사진을 올리던 화면으로 되돌리고 안내, 외부·없는 Referer 는 400 화면, JSON 은 FILE_TOO_LARGE
 * [담당] 팀원 4 · #16 후속(업로드 용량 초과 안내)   [전제] 스프링·DB 없이 핸들러를 직접 호출한다(MockHttpServletRequest).
 * 🟡 미학습 기술: Spring Test MockHttpServletRequest (컨트롤러 계층 테스트 도구가 프로젝트에 없어 핸들러만 직접 검증)
 */
class GlobalExceptionHandlerUploadTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;
    private FlashMap flash;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/my/works");
        request.setServerName("localhost");
        flash = new FlashMap();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flash);   // DispatcherServlet 이 넣어 주는 flash 저장소
    }

    private Object handle() {
        return handler.handleMaxUploadSize(new MaxUploadSizeExceededException(50L * 1024 * 1024), request);
    }

    @Test
    @DisplayName("같은 사이트의 Referer 가 있으면 그 화면(쿼리 포함)으로 redirect 하고 flash errorMessage 를 남긴다")
    void redirectsBackWithFlash() {
        request.addHeader("Referer", "http://localhost:8080/assignments?enrollmentId=3");

        Object result = handle();

        assertThat(result).isEqualTo("redirect:/assignments?enrollmentId=3");
        assertThat(flash.get("errorMessage")).isEqualTo(GlobalExceptionHandler.UPLOAD_TOO_LARGE_MESSAGE);
    }

    @Test
    @DisplayName("Referer 가 없거나 외부 주소·이상한 값이면 redirect 하지 않고 400 오류 화면 (오픈 리다이렉트 방지)")
    void noRedirectForUnsafeReferer() {
        for (String referer : new String[] {null, "", "http://evil.com/phish", "//evil.com/x", "http://localhost//evil.com/x",
                "javascript:alert(1)", "not a uri %%"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/my/works");
            req.setServerName("localhost");
            req.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
            if (referer != null) {
                req.addHeader("Referer", referer);
            }

            Object result = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1L), req);

            assertThat(result).as("Referer=%s", referer).isInstanceOf(ModelAndView.class);
            ModelAndView mav = (ModelAndView) result;
            assertThat(mav.getViewName()).isEqualTo("error/400");
            assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(mav.getModel().get("message")).isEqualTo(GlobalExceptionHandler.UPLOAD_TOO_LARGE_MESSAGE);
        }
    }

    @Test
    @DisplayName("JSON 요청(Accept: application/json)은 Referer 가 있어도 { code: FILE_TOO_LARGE } 400")
    void jsonRequest() {
        request.addHeader("Accept", "application/json");
        request.addHeader("Referer", "http://localhost:8080/my/works/new");

        Object result = handle();

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo("FILE_TOO_LARGE");
        assertThat(flash.isEmpty()).isTrue();
    }
}
