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
    // 오류 화면에 실제 이유로 보이는 문구(GlobalExceptionHandler reason) — 무엇이 문제인지와 다음에 할 일을 함께 적는다
    HOBBY_NOT_FOUND(HttpStatus.NOT_FOUND, "취미를 찾을 수 없어요. 지금은 공개되지 않았거나 없는 취미예요."),
    HOBBY_NOT_OPEN(HttpStatus.FORBIDDEN, "운영 중인 취미가 아닙니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없어요. 비공개로 바뀌었거나 없는 강의예요."),
    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "차시를 찾을 수 없습니다."),
    // 05 §3: 운영 중 대분류에는 운영 중인 세부 취미가 1개 이상 있어야 한다 (관리자 대분류 추가·편집·상태 변경)
    CATEGORY_NEEDS_OPERATING_HOBBY(HttpStatus.BAD_REQUEST, "운영 중인 세부 취미가 1개 이상 있어야 대분류를 운영 중으로 바꿀 수 있어요."),

    /* 수강 */
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 수강 중인 강의입니다."),
    ALREADY_WAITING(HttpStatus.CONFLICT, "이미 대기 중인 강의입니다."),
    CANNOT_REAPPLY(HttpStatus.CONFLICT, "다시 신청할 수 없는 강의입니다."),
    NO_ASSIGNABLE_SECTION(HttpStatus.CONFLICT, "신청 가능한 분반이 없습니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 정보를 찾을 수 없어요. 나의 학습에서 강의를 다시 선택해 주세요."),
    ENROLLMENT_EXPIRED(HttpStatus.FORBIDDEN, "수강 기간이 만료되었습니다."),
    CANCEL_NOT_ALLOWED_DURING_REVIEW(HttpStatus.CONFLICT, "평가 대기·진행 중에는 수강을 취소할 수 없습니다."),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "분반을 찾을 수 없습니다."),
    CAPACITY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "정원은 1~30명입니다."),
    CAPACITY_BELOW_ACTIVE(HttpStatus.CONFLICT, "현재 수강 인원보다 정원을 줄일 수 없습니다."),
    INSTRUCTOR_NOT_GRANTED(HttpStatus.FORBIDDEN, "해당 취미 권한이 없는 강사입니다."),

    /* 학습 */
    VIDEO_ACCESS_DENIED(HttpStatus.FORBIDDEN, "수강 기간이 끝났거나 취소한 강의라 영상을 볼 수 없어요. 수료한 강의는 기간과 관계없이 다시 볼 수 있어요."),

    /* 과제·평가 */
    LESSONS_NOT_COMPLETED(HttpStatus.CONFLICT, "3차시를 모두 완료해야 제출할 수 있습니다."),
    SUBMISSION_LOCKED(HttpStatus.CONFLICT, "평가가 시작되어 수정할 수 없습니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 평가 대기 중인 과제가 있습니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지는 1~5장이어야 합니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 한 장은 50MB 이하여야 합니다."),
    FILE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),
    INVALID_VIDEO_FILE(HttpStatus.BAD_REQUEST, "MP4 영상 파일만 올릴 수 있습니다."),   // 관리자 영상 업로드 (VideoStorage)
    VIDEO_TOO_LARGE(HttpStatus.BAD_REQUEST, "영상은 500MB 이하여야 합니다."),
    VIDEO_DURATION_UNKNOWN(HttpStatus.BAD_REQUEST, "영상 길이를 읽을 수 없어 차시에 연결할 수 없습니다."),
    VIDEO_IN_USE(HttpStatus.CONFLICT, "차시에 연결된 영상은 삭제할 수 없습니다. 다른 영상으로 바꾼 뒤 삭제해 주세요."),
    VIDEO_LINK_DENIED(HttpStatus.FORBIDDEN, "권한이 있는 취미의 차시에만 영상을 연결할 수 있습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 정보를 찾을 수 없습니다."),
    NOT_ASSIGNED_INSTRUCTOR(HttpStatus.FORBIDDEN, "담당 강사만 평가할 수 있습니다."),
    INVALID_REVIEW_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요. 이미 처리되었거나 아직 평가가 끝나지 않았을 수 있어요. 화면을 새로 고쳐 확인해 주세요."),
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
