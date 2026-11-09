package com.wanted.lmspractice.domain.enrollment.service;

import org.springframework.stereotype.Service;

/**
 * ★ 자리 반환 → 대기 1순위 자동 확정 → 알림 (호출자 트랜잭션 안에서 실행)
 * 담당: 팀원 2 · #09
 * 자리가 생기는 4가지(수료·수강 취소·만료·정원 증가/담당 지정)가 모두 이 메서드로 모인다.
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class SeatService {

    /** 계약 C4: 남은 자리가 있는 동안 대기 1순위부터 수강 확정, 확정 인원 수 반환 */
    public int fillVacancies(Long courseId) {
        // TODO #09: 구현 (현재는 스텁)
        return 0;
    }
}
