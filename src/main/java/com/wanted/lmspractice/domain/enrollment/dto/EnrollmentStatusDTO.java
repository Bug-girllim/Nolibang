package com.wanted.lmspractice.domain.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의별 "내 신청 상태" — 강의 목록·상세·세부 취미 화면의 신청 영역이 이 값으로 버튼·안내를 바꾼다
 * [담당] 팀원 2 · #09 (#08에서 화면이 쓸 수 있도록 필드·값을 먼저 정의)   [화면] U-S01~U-S04   [API] CO-02 EnrollmentStatusDTO.state
 * [연결]
 *  - 만든 곳: EnrollmentService.getStatusForUser (#09에서 구현, 그 전엔 스텁), CourseCatalogService(준비 중 취미·강사·관리자 등 #08 규칙)
 *  - 쓰는 곳: templates/course/detail.html(신청 영역 fragment), course/list.html, hobby/detail.html
 * [기술] 🟢 DTO
 * state 값은 API 명세 CO-02 표 기준이며, 비회원(LOGIN_REQUIRED)·강사/관리자(NOT_LEARNER)는 #08에서 추가했다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class EnrollmentStatusDTO {

    /** 비회원 — "로그인 후 신청" */
    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    /** 빈자리 있음 — [수강 신청] */
    public static final String CAN_APPLY = "CAN_APPLY";
    /** 만석 — [대기 신청] */
    public static final String CAN_WAIT = "CAN_WAIT";
    /** 대기 중 — "대기 n번" + [대기 취소] */
    public static final String WAITING = "WAITING";
    /** 수강 중 — [이어서 학습] */
    public static final String LEARNING = "LEARNING";
    /** 수료 — "수료 완료" + [다시 보기] */
    public static final String COMPLETED = "COMPLETED";
    /** 취소·만료한 강의 — 다시 신청할 수 없음 (D-55) */
    public static final String CANNOT_REAPPLY = "CANNOT_REAPPLY";
    /** 준비 중 취미·담당 강사 있는 분반 없음 — 신청 불가 */
    public static final String UNAVAILABLE = "UNAVAILABLE";
    /** 강사·관리자 계정 — 수강 신청은 학습자만 가능 */
    public static final String NOT_LEARNER = "NOT_LEARNER";

    private String state;
    /** state가 WAITING일 때의 대기 순번(1부터). 그 밖에는 null */
    private Integer waitingNumber;

    /** 순번이 필요 없는 상태 */
    public static EnrollmentStatusDTO of(String state) {
        return new EnrollmentStatusDTO(state, null);
    }

    /** 화면에 보여 줄 짧은 상태 이름 (칩·안내 문구용) */
    public String getLabel() {
        return switch (state == null ? "" : state) {
            case LOGIN_REQUIRED -> "로그인 후 신청";
            case CAN_APPLY -> "신청 가능";
            case CAN_WAIT -> "대기 신청 가능";
            case WAITING -> waitingNumber == null ? "대기 중" : "대기 " + waitingNumber + "번";
            case LEARNING -> "수강 중";
            case COMPLETED -> "수료 완료";
            case CANNOT_REAPPLY -> "다시 신청할 수 없음";
            case NOT_LEARNER -> "학습자 전용";
            default -> "신청 불가";
        };
    }

    /** 수강 신청·대기 신청 버튼을 보여 줄 상태인지 */
    public boolean isApplyOpen() {
        return CAN_APPLY.equals(state) || CAN_WAIT.equals(state);
    }
}
