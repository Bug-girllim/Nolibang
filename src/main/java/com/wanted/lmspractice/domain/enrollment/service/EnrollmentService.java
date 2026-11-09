package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 수강 신청(즉시 확정 or 대기)·수강 취소·수료/종료 처리
 * 담당: 팀원 2 · #09
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final SeatService seatService;

    /** 계약 C5: 수료 승인 시 호출 (호출: 팀원 3 ReviewService) — 내부에서 SeatService.fillVacancies() */
    public void complete(Long enrollmentId) {
        // TODO #09: 구현 (현재는 스텁)
    }

    /** 계약 C5: 수강기간 만료 후 반려 확정 시 호출 (호출: 팀원 3 ReviewService) */
    public void closeAfterRejection(Long enrollmentId) {
        // TODO #09: 구현 (현재는 스텁)
    }

    /** 계약 C6: 본인 수강 건 조회 (본인 확인 포함, 사용: 팀원 3·4) */
    public Enrollment getOwnedEnrollment(Long userId, Long enrollmentId) {
        throw new UnsupportedOperationException("TODO #09");
    }

    /**
     * 계약 C17(#08 이슈 명시): 한 학습자의 강의별 신청 상태 — 신청 가능/대기 n번/학습 중/수료/재신청 불가.
     * 호출: CourseCatalogService (강의 목록·상세·세부 취미 화면의 신청 영역)
     * 강의·취미 자체의 노출 규칙(준비 중 취미, 강사·관리자 계정)은 호출한 쪽(#08)이 먼저 걸러서 이 메서드에는 학습자만 온다.
     */
    public EnrollmentStatusDTO getStatusForUser(Long userId, Long courseId) {
        // TODO #09: 구현 — 이 학습자의 수강(ACTIVE·COMPLETED·CANCELLED·EXPIRED)·대기(WAITING) 기록을 먼저 보고
        //          LEARNING / COMPLETED / CANNOT_REAPPLY(D-55) / WAITING(+순번)을 돌려준 뒤, 기록이 없을 때만 아래 정원 판단을 한다.
        // 현재는 스텁: 개인 기록은 보지 않고 정원 현황만으로 신청 가능 여부를 정한다(#09 전까지 화면이 자연스럽게 보이도록).
        SeatSummaryDTO seats = seatService.getSeatSummary(courseId);
        if (seats.getTotalCapacity() == 0) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.UNAVAILABLE);  // 담당 강사가 있는 분반이 없다 (CO-02 UNAVAILABLE)
        }
        // D-52: 대기자가 있으면 빈자리가 있어도 대기열 끝으로 간다 → 만석이거나 대기자가 있으면 대기 신청
        boolean mustWait = seats.isFull() || seats.getWaitingCount() > 0;
        return EnrollmentStatusDTO.of(mustWait ? EnrollmentStatusDTO.CAN_WAIT : EnrollmentStatusDTO.CAN_APPLY);
    }
}
