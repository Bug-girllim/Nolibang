package com.wanted.lmspractice.domain.enrollment.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 수강 신청(EN-01) 결과 — 즉시 확정(CONFIRMED + 분반·만료일) 또는 대기 등록(WAITING + 순번)
 * [담당] 팀원 2 · #09   [화면] U-S05(강의 상세 신청 영역의 완료 안내)   [API] EN-01 ApplyResultDTO.result
 * [연결]
 *  - 만든 곳: EnrollmentService.apply(confirmed), WaitlistService.join(waiting)
 *  - 쓰는 곳: EnrollmentController.apply → flash "message"(getMessage) → templates/course/enroll-area.html
 * [기술] 🟢 DTO + 정적 팩토리
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class ApplyResultDTO {

    /** 남은 자리 최대 분반에 바로 수강 확정 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 대기열 끝에 등록 (만석이거나 앞선 대기자가 있음 — D-52) */
    public static final String WAITING = "WAITING";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private String result;
    /** CONFIRMED일 때 배정된 분반 이름 (예: A분반) */
    private String sectionName;
    /** CONFIRMED일 때 수강 만료 시각 (확정 + 30일) */
    private LocalDateTime expiresAt;
    /** WAITING일 때 대기 순번 (1부터) */
    private Integer waitingNumber;

    /** 즉시 확정 결과. 호출: EnrollmentService.apply */
    public static ApplyResultDTO confirmed(String sectionName, LocalDateTime expiresAt) {
        return new ApplyResultDTO(CONFIRMED, sectionName, expiresAt, null);
    }

    /** 대기 등록 결과. 호출: WaitlistService.join */
    public static ApplyResultDTO waiting(int waitingNumber) {
        return new ApplyResultDTO(WAITING, null, null, waitingNumber);
    }

    public boolean isConfirmed() {
        return CONFIRMED.equals(result);
    }

    /** 완료 안내 문구 — API EN-01 "메시지 예" 그대로 */
    public String getMessage() {
        if (isConfirmed()) {
            return "수강이 확정되었습니다. (" + sectionName + ", " + expiresAt.format(DATE) + "까지)";
        }
        return "대기 " + waitingNumber + "번으로 등록되었습니다. 자리가 나면 자동으로 수강이 확정됩니다.";
    }
}
