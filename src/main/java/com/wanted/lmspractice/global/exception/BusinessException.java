package com.wanted.lmspractice.global.exception;

/**
 * 도메인·비즈니스 규칙 위반 시 던지는 예외 (계약 C2)
 * 담당: 팀원 1 · #02
 * 사용법: throw new BusinessException(ErrorCode.X)
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
