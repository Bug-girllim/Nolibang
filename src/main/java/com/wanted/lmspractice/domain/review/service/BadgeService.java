package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 수준·마스터 배지 계산 (저장하지 않고 조회 시 계산) — 계약 C12
 * 담당: 팀원 3 · #15
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class BadgeService {

    /** 계약 C12: 보유 배지 목록 */
    public List<BadgeDTO> getBadges(Long userId) {
        // TODO #15: 구현 (현재는 스텁)
        return List.of();
    }

    /** 계약 C12: 해당 취미의 최고 배지 수준 (없으면 null) — 사용: 팀원 4 WorkLevelPolicy */
    public CourseLevel getHighestLevel(Long userId, Long hobbyId) {
        // TODO #15: 구현 (현재는 스텁)
        return null;
    }

    /** 계약 C12: 특정 수준 배지 보유 여부 — 사용: 팀원 4 CommentPolicy */
    public boolean hasBadge(Long userId, Long hobbyId, CourseLevel level) {
        // TODO #15: 구현 (현재는 스텁)
        return false;
    }
}
