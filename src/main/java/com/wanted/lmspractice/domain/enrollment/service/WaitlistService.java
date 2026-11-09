package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.MyWaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기 신청·대기 취소(재신청 시 끝순번)·순번 조회·관리자 조회
 * 담당: 팀원 2 · #09 (#11에서 본인 대기 목록 조회 getMyWaiting 추가)
 * (💡 제안 구조 — 대기 신청·취소·관리자 조회 상세 구현은 #09·#10에서)
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;

    /**
     * 계약 C19(#11 추가): 한 학습자가 지금 대기 중인(WAITING) 강의 목록과 각 대기 번호 — 신청 순서대로.
     * 대기 번호 = 같은 강의에서 먼저 신청한 대기자 수 + 1 (D-53: 신청 순서 FIFO).
     * 호출: MyLearningService.getMyLearning (GET /my/learning, API ML-01)
     */
    @Transactional(readOnly = true)
    public List<MyWaitlistDTO> getMyWaiting(Long userId) {
        return waitlistEntryRepository.findByUserIdAndStatusOrderByIdAsc(userId, WaitlistStatus.WAITING).stream()
                .map(this::toDto)
                .toList();
    }

    private MyWaitlistDTO toDto(WaitlistEntry entry) {
        long ahead = waitlistEntryRepository.countByCourseIdAndStatusAndIdLessThan(
                entry.getCourseId(), WaitlistStatus.WAITING, entry.getId());
        return new MyWaitlistDTO(entry.getId(), entry.getCourseId(), (int) ahead + 1, entry.getCreatedAt());
    }
}
