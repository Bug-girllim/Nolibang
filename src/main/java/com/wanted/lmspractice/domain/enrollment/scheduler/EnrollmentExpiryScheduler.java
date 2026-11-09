package com.wanted.lmspractice.domain.enrollment.scheduler;

import org.springframework.stereotype.Component;

/**
 * 30일 만료 수강 건 처리 → SeatService.fillVacancies() 🟡 @Scheduled
 * 담당: 팀원 2 · #10
 * @EnableScheduling 설정 위치는 #10에서 결정 (주기 ❓)
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Component
public class EnrollmentExpiryScheduler {

    // TODO #10: 구현
}
