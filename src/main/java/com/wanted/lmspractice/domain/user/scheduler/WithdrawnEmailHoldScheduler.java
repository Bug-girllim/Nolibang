package com.wanted.lmspractice.domain.user.scheduler;

import com.wanted.lmspractice.domain.user.service.WithdrawnEmailHoldService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [역할] 기간이 끝난 탈퇴 이메일 확인값을 주기적으로 지우고, 이전 방식으로 원문 이메일이 남은 탈퇴 계정을 정리한다
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리   [기획] U-15 3번 "확인값만 30일 보관"
 * [연결]
 *  - 호출받음: Spring 스케줄러(주기 실행)
 *  - 호출함:   WithdrawnEmailHoldService.migrateLegacyWithdrawnEmails, purgeExpired
 * [기술] 🟡 @Scheduled (EnrollmentExpiryScheduler 와 같은 방식). @EnableScheduling 은 EnrollmentExpiryScheduler 에 이미 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnEmailHoldScheduler {

    /** ❓ 임시값: 1시간마다 실행. 재가입 판정은 만료 시각으로 하므로 정리가 늦어도 가입 제한이 길어지지 않는다 */
    static final long CHECK_INTERVAL_MS = 60 * 60 * 1000L;

    /** 서버 시작 1분 뒤 첫 실행 — 이전 방식 탈퇴 계정의 원문 이메일을 빨리 지우려고 */
    static final long INITIAL_DELAY_MS = 60 * 1000L;

    private final WithdrawnEmailHoldService holdService;

    /** 원문 이메일 정리 → 만료 확인값 삭제 순서로 한 번 실행한다 */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        int migrated = holdService.migrateLegacyWithdrawnEmails(now);
        int purged = holdService.purgeExpired(now);
        if (migrated > 0 || purged > 0) {
            log.info("탈퇴 이메일 확인값 정리 — 원문 이메일 제거 {}건, 만료 확인값 삭제 {}건", migrated, purged);
        }
    }
}
