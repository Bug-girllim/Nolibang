package com.wanted.lmspractice.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * [역할] 오류 화면에 "실제 이유"(reason)를 넘기는 규칙 단위 테스트 — 구체적인 업무 오류만 이유를 보여 주고,
 *        제목과 같은 일반 문구(ACCESS_DENIED·NOT_FOUND·INVALID_INPUT·INTERNAL_ERROR)는 화면 기본 문구를 쓴다.
 * [근거] 체크리스트 ③ 검증 후 팀 결정 — 오류 화면이 고정 문구라 "영상을 볼 수 있는 기간이 아님" 같은 이유가 보이지 않았다
 * [전제] 스프링·DB 없이 핸들러를 직접 호출한다 (GlobalExceptionHandlerUploadTest 와 같은 방식)
 * 🟡 미학습 기술: Spring Test MockHttpServletRequest
 */
class GlobalExceptionHandlerReasonTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ModelAndView handle(ErrorCode code) {
        return (ModelAndView) handler.handleBusiness(new BusinessException(code), new MockHttpServletRequest("GET", "/x"));
    }

    @Test
    @DisplayName("구체적인 업무 오류는 그 문구를 reason 으로 넘긴다 (403 영상 기간, 404 강의, 409 평가 상태)")
    void specificErrorsCarryReason() {
        ModelAndView video = handle(ErrorCode.VIDEO_ACCESS_DENIED);
        assertThat(video.getViewName()).isEqualTo("error/403");
        assertThat(video.getModel().get("reason")).isEqualTo(ErrorCode.VIDEO_ACCESS_DENIED.getMessage());

        assertThat(handle(ErrorCode.COURSE_NOT_FOUND).getModel().get("reason"))
                .isEqualTo(ErrorCode.COURSE_NOT_FOUND.getMessage());

        ModelAndView conflict = handle(ErrorCode.INVALID_REVIEW_STATE);
        assertThat(conflict.getViewName()).isEqualTo("error/400");   // 409는 400 화면을 쓰고, 화면이 status 409로 제목을 바꾼다
        assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getModel().get("reason")).isEqualTo(ErrorCode.INVALID_REVIEW_STATE.getMessage());
    }

    @Test
    @DisplayName("일반 오류(접근 권한 없음·페이지 없음·입력 오류·서버 오류)는 reason 이 없어 화면 기본 문구가 보인다")
    void genericErrorsHaveNoReason() {
        for (ErrorCode code : new ErrorCode[] {ErrorCode.ACCESS_DENIED, ErrorCode.NOT_FOUND,
                ErrorCode.INVALID_INPUT, ErrorCode.INTERNAL_ERROR}) {
            assertThat(handle(code).getModel()).as(code.name()).doesNotContainKey("reason");
        }
        ModelAndView unexpected = (ModelAndView) handler.handleException(
                new IllegalStateException("내부 정보"), new MockHttpServletRequest("GET", "/x"));
        assertThat(unexpected.getModel()).doesNotContainKey("reason");
    }
}
