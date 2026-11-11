package com.wanted.lmspractice.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * [역할] GlobalExceptionHandler 가 글자 인코딩이 깨진 요청(Tomcat InvalidParameterException)을 500이 아니라 400으로 돌려주는지 확인
 * [근거] 체크리스트 ① 검증 중 발견 — 깨진 %인코딩 주소(GET /signup/check-nickname?nickname=%B4...)가 500 오류 화면으로 보였다
 * [전제] 스프링·DB 없이 핸들러를 직접 호출한다(MockHttpServletRequest). MockMvc는 Tomcat 파라미터 해석을 거치지 않아 이 예외를 재현할 수 없다.
 * 🟡 미학습 기술: Spring Test MockHttpServletRequest (GlobalExceptionHandlerUploadTest 와 같은 방식)
 */
class GlobalExceptionHandlerBadRequestTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("화면 요청이면 400 오류 화면(error/400)과 INVALID_INPUT 문구")
    void htmlRequestGets400Page() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/signup/check-nickname");

        Object result = handler.handleInvalidParameter(new InvalidParameterException("Character decoding failed"), request);

        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) result;
        assertThat(mav.getViewName()).isEqualTo("error/400");
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mav.getModel().get("message")).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("JSON 요청이면 400 + { code: INVALID_INPUT }")
    void jsonRequestGets400Json() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/signup/check-nickname");
        request.addHeader("Accept", "application/json");

        Object result = handler.handleInvalidParameter(new InvalidParameterException("Character decoding failed"), request);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo("INVALID_INPUT");
    }
}
