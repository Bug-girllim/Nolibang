package com.wanted.lmspractice.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;

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

    /** 업로드 용량 초과 안내 문구 — 이미지 장당 50MB(D-22)·한 번에 최대 5장(D-18), 관리자 영상 500MB(application.yaml 파일당 500MB·요청 510MB) */
    static final String UPLOAD_TOO_LARGE_MESSAGE =
            "올린 파일이 너무 커요. 이미지는 한 장당 50MB 이하, 한 번에 최대 5장까지, 영상은 500MB 이하로 올릴 수 있어요.";

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

    /**
     * 주소·폼 값의 글자 인코딩이 깨진 요청(예: UTF-8 이 아닌 %인코딩) → 400.
     * 서버(Tomcat)가 파라미터를 읽다가 던지는 예외라, 이 핸들러가 없으면 아래 Exception 핸들러가 500으로 바꿔 버린다.
     * 요청이 잘못된 것이지 서버 고장이 아니므로 오류 로그(ERROR)를 남기지 않는다.
     */
    @ExceptionHandler(InvalidParameterException.class)
    public Object handleInvalidParameter(InvalidParameterException e, HttpServletRequest request) {
        log.warn("잘못된 요청 파라미터(글자 인코딩): {}", request.getRequestURI());
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

    /**
     * 업로드 용량 초과(파일당 500MB·요청 전체 510MB — application.yaml multipart 설정) → 400 FILE_TOO_LARGE.
     * 이 예외는 요청이 컨트롤러에 닿기 전(서블릿의 multipart 해석 단계)에 나서 컨트롤러 안의 BusinessException 처리로는 받을 수 없다.
     * 그래서 여기서, 사진을 올리던 화면(Referer)으로 되돌려 errorMessage 로 안내한다(API §1-6 flash). JSON 요청이거나
     * 되돌아갈 같은 사이트 화면을 알 수 없으면 400 오류 화면을 보인다. 고른 사진은 브라우저 보안상 다시 골라야 한다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("업로드 용량 초과: {}", e.getMessage());
        String back = wantsJson(request) ? null : sameSitePathOf(request.getHeader("Referer"), request);
        if (back == null) {
            return respond(HttpStatus.BAD_REQUEST, ErrorCode.FILE_TOO_LARGE.name(), UPLOAD_TOO_LARGE_MESSAGE, request);
        }
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", UPLOAD_TOO_LARGE_MESSAGE);
        return "redirect:" + back;
    }

    /** 같은 사이트의 Referer 만 "경로?쿼리"로 돌려준다 — 외부 주소로 보내는 오픈 리다이렉트를 막는다. 쓸 수 없으면 null */
    private String sameSitePathOf(String referer, HttpServletRequest request) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(referer);
            boolean sameHost = uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName());
            String path = uri.getRawPath();
            if (!sameHost || path == null || !path.startsWith("/") || path.startsWith("//")) {
                return null;
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (URISyntaxException ex) {
            return null;
        }
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
