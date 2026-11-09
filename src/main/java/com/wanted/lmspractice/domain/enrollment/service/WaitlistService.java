package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.WaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 대기 신청·대기 취소(재신청 시 끝순번)·순번 조회(#09) + 관리자 대기열 조회(#10, 조회 전용)
 * [담당] 팀원 2 · #09(신청·취소·순번), #10(관리자 조회 — getWaitlist·getRecentConfirmed·countRecentConfirmed)
 *       [화면] A-S12   [기획] FR-053, D-53(순번 변경·수동 확정 없음)   [API] WL-01
 * [연결]
 *  - 호출받음: AdminWaitlistController(WL-01)
 *  - 호출함:   WaitlistQuery(native 조회)
 * [기술] 🟢 @Service·@Transactional(readOnly)
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    /** ❓ 임시값: 최근 자동 확정 내역 표시 개수 (API WL-01 "개수 ❓") */
    public static final int RECENT_CONFIRMED_LIMIT = 10;
    /** 시안 KPI "최근 7일 자동 확정"의 기간(일) */
    public static final int RECENT_CONFIRMED_DAYS = 7;

    private final WaitlistQuery waitlistQuery;

    // TODO #09: 대기 신청·대기 취소·순번 조회 구현

    /**
     * 관리자 대기열 — 한 강의의 대기 중 학습자를 순번(FIFO)대로 (WL-01 모델 waitlist).
     * 호출: AdminWaitlistController.list
     */
    @Transactional(readOnly = true)
    public List<WaitlistDTO> getWaitlist(Long courseId) {
        return waitlistQuery.findWaiting(courseId);
    }

    /**
     * 최근 자동 확정 내역 — 확정 시각 최신순 RECENT_CONFIRMED_LIMIT건 (WL-01 모델 recentConfirmed).
     * 호출: AdminWaitlistController.list
     */
    @Transactional(readOnly = true)
    public List<WaitlistDTO> getRecentConfirmed(Long courseId) {
        return waitlistQuery.findRecentConfirmed(courseId, RECENT_CONFIRMED_LIMIT);
    }

    /** 최근 7일 자동 확정 수 (A-S12 KPI). 호출: AdminWaitlistController.list */
    @Transactional(readOnly = true)
    public int countRecentConfirmed(Long courseId) {
        return waitlistQuery.countConfirmedWithinDays(courseId, RECENT_CONFIRMED_DAYS);
    }
}
