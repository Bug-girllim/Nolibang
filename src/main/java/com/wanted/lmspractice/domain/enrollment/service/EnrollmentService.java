package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import org.springframework.stereotype.Service;

/**
 * 수강 신청(즉시 확정 or 대기)·수강 취소·수료/종료 처리
 * 담당: 팀원 2 · #09
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class EnrollmentService {

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
}
