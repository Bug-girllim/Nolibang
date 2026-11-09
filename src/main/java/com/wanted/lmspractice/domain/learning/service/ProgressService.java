package com.wanted.lmspractice.domain.learning.service;

import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 재생 시간 누적(서버 관측 시간 상한)·60% 완료 판정 (FR-020)
 * 담당: 팀원 3 · #12
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class ProgressService {

    /** 계약 C10: 3차시 모두 완료했는지 (사용: SubmissionPolicy) */
    public boolean isAllCompleted(Long enrollmentId) {
        // TODO #12: 구현 (현재는 스텁)
        return false;
    }

    /** 계약 C10: 차시별 진도 (사용: 팀원 2 #11, 팀원 1 #07) */
    public List<LessonProgressDTO> getLessonStatuses(Long enrollmentId) {
        // TODO #12: 구현 (현재는 스텁)
        return List.of();
    }
}
