package com.wanted.lmspractice.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * [역할] 컨트롤러에서 난 예외를 오류 화면(error/400·403·404·405·500)으로 바꾼다.
 *        JSON을 요청한 경우(Accept: application/json, API §1-7)에는 { code, message } 본문으로 응답한다.
 * [담당] 팀원 1 · #02   [화면] E-400~E-500   [API] §1-6, §1-7
 * [연결]
 *  - 호출받음: Spring MVC (모든 컨트롤러·서비스의 예외)
 *  - 호출함:   ErrorCode(상태·메시지), templates/error/{status}.html
 *  - 모델:     status(int), message(String)
 * [기술] 🟢 @ControllerAdvice (미니 프로젝트 구조). 필터에서 나는 403은 SecurityConfig의 accessDeniedPage가 맡는다
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 업무 규칙 위반 — ErrorCode의 상태·메시지를 그대로 사용 */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return respond(code.getStatus(), code.name(), code.getMessage(), request);
    }

    /** @Valid 실패(BindException, MethodArgumentNotValidException 포함)·검증 오류 → 400 */
    @ExceptionHandler({BindException.class, HandlerMethodValidationException.class})
    public Object handleValidation(Exception e, HttpServletRequest request) {
        return respondWith(ErrorCode.INVALID_INPUT, request);
    }

    /** 필수 파라미터 누락·타입 불일치 → 400 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Object handleBadRequest(Exception e, HttpServletRequest request) {
        return respondWith(ErrorCode.INVALID_INPUT, request);
    }

    /** UNIQUE·FK 위반 등 DB 제약 위반 → 400 (원문 메시지에 칼럼·값이 있을 수 있어 화면에 내지 않는다) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("DB 제약 위반: {}", e.getClass().getSimpleName());
        return respondWith(ErrorCode.INVALID_INPUT, request);
    }

    /** 컨트롤러 안(@PreAuthorize 등)에서 난 권한 오류 → 403. 이 핸들러가 없으면 아래 Exception 핸들러가 500으로 바꿔 버린다 */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return respondWith(ErrorCode.ACCESS_DENIED, request);
    }

    /** 없는 URL → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        return respondWith(ErrorCode.NOT_FOUND, request);
    }

    /** 허용하지 않는 HTTP 메서드 → 405 (ErrorCode에 전용 값이 없어 상태만 직접 지정) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않는 요청 방식입니다.", request);
    }

    /** 그 밖의 모든 예외 → 500. 상세 원인은 로그에만 남기고 화면에는 일반 문구만 보인다 */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외", e);
        return respondWith(ErrorCode.INTERNAL_ERROR, request);
    }

    private Object respondWith(ErrorCode code, HttpServletRequest request) {
        return respond(code.getStatus(), code.name(), code.getMessage(), request);
    }

    /** JSON 요청이면 { code, message }, 아니면 error/{status} 화면 */
    private Object respond(HttpStatus status, String code, String message, HttpServletRequest request) {
        if (wantsJson(request)) {
            return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
        }
        ModelAndView mav = new ModelAndView("error/" + viewStatus(status));
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        return mav;
    }

    /** ❓ 임시 판별 방식: fetch 호출 시 Accept 헤더에 application/json을 넣는다 (API §1-7 방식 확정 필요) */
    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    /** 오류 템플릿은 400·403·404·405·500만 있다. 409 등 나머지 4xx는 400 화면, 5xx는 500 화면을 쓴다 */
    private int viewStatus(HttpStatus status) {
        return switch (status.value()) {
            case 400, 403, 404, 405, 500 -> status.value();
            default -> status.is5xxServerError() ? 500 : 400;
        };
    }
}
