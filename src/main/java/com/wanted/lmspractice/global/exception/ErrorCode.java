package com.wanted.lmspractice.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 예외 종류 목록 (HTTP 상태 + 사용자 메시지)
 * 담당: 팀원 1 · #02
 * 작업분배 문서 #02 초안을 그대로 옮긴 제안값. #01에서 확정하고, 추가가 필요하면 팀원 1에게 요청한다.
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
public enum ErrorCode {

    /* 공통 */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 페이지를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    /* 계정 */
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "인증 정보를 찾을 수 없습니다."),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "인증 기간이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호 변경이 필요합니다."),
    INVALID_NEW_PASSWORD(HttpStatus.BAD_REQUEST, "사용할 수 없는 비밀번호입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),  // #06 계정 설정 비밀번호 변경
    WITHDRAW_BLOCKED_ACTIVE_ENROLLMENT(HttpStatus.CONFLICT, "수강 중인 강의가 있어 탈퇴할 수 없습니다. 수료하거나 수강을 취소한 뒤 다시 시도해 주세요."),

    /* 탐색 */
    HOBBY_NOT_FOUND(HttpStatus.NOT_FOUND, "취미를 찾을 수 없습니다."),
    HOBBY_NOT_OPEN(HttpStatus.FORBIDDEN, "운영 중인 취미가 아닙니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "차시를 찾을 수 없습니다."),

    /* 수강 */
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 수강 중인 강의입니다."),
    ALREADY_WAITING(HttpStatus.CONFLICT, "이미 대기 중인 강의입니다."),
    CANNOT_REAPPLY(HttpStatus.CONFLICT, "다시 신청할 수 없는 강의입니다."),
    NO_ASSIGNABLE_SECTION(HttpStatus.CONFLICT, "신청 가능한 분반이 없습니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 정보를 찾을 수 없습니다."),
    ENROLLMENT_EXPIRED(HttpStatus.FORBIDDEN, "수강 기간이 만료되었습니다."),
    CANCEL_NOT_ALLOWED_DURING_REVIEW(HttpStatus.CONFLICT, "평가 대기·진행 중에는 수강을 취소할 수 없습니다."),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "분반을 찾을 수 없습니다."),
    CAPACITY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "정원은 1~30명입니다."),
    CAPACITY_BELOW_ACTIVE(HttpStatus.CONFLICT, "현재 수강 인원보다 정원을 줄일 수 없습니다."),
    INSTRUCTOR_NOT_GRANTED(HttpStatus.FORBIDDEN, "해당 취미 권한이 없는 강사입니다."),

    /* 학습 */
    VIDEO_ACCESS_DENIED(HttpStatus.FORBIDDEN, "영상을 볼 수 있는 기간이 아닙니다."),

    /* 과제·평가 */
    LESSONS_NOT_COMPLETED(HttpStatus.CONFLICT, "3차시를 모두 완료해야 제출할 수 있습니다."),
    SUBMISSION_LOCKED(HttpStatus.CONFLICT, "평가가 시작되어 수정할 수 없습니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 평가 대기 중인 과제가 있습니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지는 1~5장이어야 합니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 한 장은 50MB 이하여야 합니다."),
    FILE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 정보를 찾을 수 없습니다."),
    NOT_ASSIGNED_INSTRUCTOR(HttpStatus.FORBIDDEN, "담당 강사만 평가할 수 있습니다."),
    INVALID_REVIEW_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다."),
    FEEDBACK_TOO_SHORT(HttpStatus.BAD_REQUEST, "피드백·반려 사유는 50자 이상이어야 합니다."),
    CANNOT_CANCEL_START(HttpStatus.CONFLICT, "평가 내용을 작성한 뒤에는 시작을 취소할 수 없습니다."),
    RESUBMIT_NOT_ALLOWED(HttpStatus.CONFLICT, "재제출할 수 없습니다."),

    /* 작품 */
    WORK_NOT_FOUND(HttpStatus.NOT_FOUND, "작품을 찾을 수 없습니다."),
    WORK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "열람 권한이 없는 작품입니다."),
    HOBBY_NOT_OPERATING(HttpStatus.BAD_REQUEST, "운영 중인 취미만 선택할 수 있습니다."),
    LEVEL_TAG_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "보유한 배지 수준 이하만 선택할 수 있습니다."),
    COMMENT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "댓글 작성 권한이 없습니다."),
    ALREADY_HIDDEN(HttpStatus.CONFLICT, "이미 숨김 처리된 작품입니다."),
    NOT_HIDDEN(HttpStatus.CONFLICT, "숨김 상태가 아닌 작품입니다."),

    /* 문의 */
    INQUIRY_NOT_ALLOWED(HttpStatus.FORBIDDEN, "현재 문의를 작성할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
