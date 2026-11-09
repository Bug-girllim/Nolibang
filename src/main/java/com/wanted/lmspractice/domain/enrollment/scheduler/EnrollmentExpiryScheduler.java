package com.wanted.lmspractice.domain.enrollment.scheduler;

import com.wanted.lmspractice.domain.enrollment.service.EnrollmentExpiryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [역할] 30일 만료 수강 건을 주기적으로 찾아 만료 처리한다 — 접속하는 사람이 없어도 자리가 반환되고 대기자가 확정되게
 * [담당] 팀원 2 · #10   [기획] FR-054   [API] SC-01
 * [연결]
 *  - 호출받음: Spring 스케줄러(주기 실행), 테스트(expire 직접 호출)
 *  - 호출함:   EnrollmentExpiryService(후보 조회, 건마다 만료 처리 — 각자 트랜잭션)
 * [기술] 🟡 미학습 기술: @Scheduled + @EnableScheduling — 접속 요청 없이도 정해진 때에 만료·자동 확정을 해야 하는데,
 *        배운 방식(요청을 받을 때 판정)만으로는 아무도 접속하지 않으면 대기자가 확정되지 않는다(작업분배 §5 팀 허용).
 *        @EnableScheduling을 공통 설정(global/config, 팀원 1 소유)이 아니라 이 클래스에 둔 이유: 스케줄러가 이것 하나뿐이고
 *        다른 사람 파일을 고치지 않기 위함. 스케줄러가 늘어나면 공통 설정으로 옮긴다.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class EnrollmentExpiryScheduler {

    /** ❓ 임시값: 실행 주기 10분 (API SC-01 "예: 매 10분", §16 #18 확정 필요). 서버 시작 직후에는 같은 시간만큼 기다렸다 처음 실행한다 */
    static final long CHECK_INTERVAL_MS = 10 * 60 * 1000L;

    private final EnrollmentExpiryService expiryService;

    /**
     * 만료 처리 한 번 실행 — 후보를 찾고 건마다 만료 처리한 뒤 처리 건수를 로그로 남긴다(SC-01 결과).
     * 호출: Spring 스케줄러(fixedDelay — 앞 실행이 끝난 뒤 10분), 테스트
     *
     * @return 이번에 만료 처리한 건수
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)
    public int expire() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> candidateIds = expiryService.findExpiredCandidateIds(now);
        int expired = 0;
        int failed = 0;
        for (Long enrollmentId : candidateIds) {
            // 한 건이 실패해도 나머지 건은 계속 처리한다 — 건마다 트랜잭션이 따로라 실패한 건만 롤백된다
            try {
                if (expiryService.expireOne(enrollmentId, now)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("수강 만료 처리 실패 enrollmentId={}", enrollmentId, e);
            }
        }
        if (!candidateIds.isEmpty()) {
            log.info("수강 만료 처리: 후보 {}건, 만료 {}건, 평가 대기 등으로 유지 {}건, 실패 {}건",
                    candidateIds.size(), expired, candidateIds.size() - expired - failed, failed);
        }
        return expired;
    }
}
