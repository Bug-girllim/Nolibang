package com.wanted.lmspractice.domain.enrollment.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강의별 "내 신청 상태" — 강의 목록·상세·세부 취미 화면의 신청 영역이 이 값으로 버튼·안내를 바꾼다
 * [담당] 팀원 2 · #08(state 값 정의) · #09(수강·대기 기록 반영, 버튼에 필요한 id·기간 추가)
 *       [화면] U-S02~U-S05   [API] CO-02 EnrollmentStatusDTO.state
 * [연결]
 *  - 만든 곳: EnrollmentService.getStatusForUser(학습자), CourseCatalogService(준비 중 취미·비회원·강사/관리자 — #08 규칙)
 *  - 쓰는 곳: templates/course/enroll-area.html(신청 영역 fragment — 대기 취소·이어서 학습·수강 취소 폼), course/list.html, hobby/detail.html
 * [기술] 🟢 DTO + 정적 팩토리
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
    /** 만석이거나 대기자가 있음 — [대기 신청] (D-52) */
    public static final String CAN_WAIT = "CAN_WAIT";
    /** 대기 중 — "대기 n번" + [대기 취소] */
    public static final String WAITING = "WAITING";
    /** 수강 중 — [이어서 학습] + [수강 취소](평가 대기·진행 중이면 숨김) */
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
    /** WAITING일 때의 대기 순번(1부터). 그 밖에는 null */
    private Integer waitingNumber;
    /** WAITING일 때 대기 줄 id — [대기 취소] 폼(EN-02 /enrollments/waitlist/{waitlistId}/cancel) */
    private Long waitlistId;
    /** WAITING일 때 대기 신청 시각 */
    private LocalDateTime waitingSince;
    /** LEARNING·COMPLETED일 때 수강 건 id — [이어서 학습]·[다시 보기] 링크, [수강 취소] 폼(EN-03) */
    private Long enrollmentId;
    /** LEARNING일 때 배정 분반 이름 (예: A분반) */
    private String sectionName;
    /** LEARNING일 때 수강 만료 시각 (확정 + 30일) */
    private LocalDateTime expiresAt;
    /** LEARNING일 때 평가 대기·진행 중이라 수강 취소를 막아야 하는지 (U-S05 ⑨) */
    private boolean cancelBlocked;

    /** 순번·id가 필요 없는 상태 */
    public static EnrollmentStatusDTO of(String state) {
        EnrollmentStatusDTO dto = new EnrollmentStatusDTO();
        dto.state = state;
        return dto;
    }

    /** 대기 중 — 순번·대기 줄 id·신청 시각. 호출: EnrollmentService.getStatusForUser */
    public static EnrollmentStatusDTO waiting(int waitingNumber, Long waitlistId, LocalDateTime waitingSince) {
        EnrollmentStatusDTO dto = of(WAITING);
        dto.waitingNumber = waitingNumber;
        dto.waitlistId = waitlistId;
        dto.waitingSince = waitingSince;
        return dto;
    }

    /** 수강 중 — 수강 건 id·분반·만료 시각·취소 가능 여부. 호출: EnrollmentService.getStatusForUser */
    public static EnrollmentStatusDTO learning(Long enrollmentId, String sectionName, LocalDateTime expiresAt, boolean cancelBlocked) {
        EnrollmentStatusDTO dto = of(LEARNING);
        dto.enrollmentId = enrollmentId;
        dto.sectionName = sectionName;
        dto.expiresAt = expiresAt;
        dto.cancelBlocked = cancelBlocked;
        return dto;
    }

    /** 수료 — 다시 보기 링크용 수강 건 id. 호출: EnrollmentService.getStatusForUser */
    public static EnrollmentStatusDTO completed(Long enrollmentId) {
        EnrollmentStatusDTO dto = of(COMPLETED);
        dto.enrollmentId = enrollmentId;
        return dto;
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
