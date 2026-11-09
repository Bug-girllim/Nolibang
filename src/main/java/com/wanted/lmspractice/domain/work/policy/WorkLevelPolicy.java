package com.wanted.lmspractice.domain.work.policy;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.service.BadgeService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 일반 작품 수준 태그 규칙 — 해당 취미의 최고 배지 수준 이하만, 배지가 없으면 입문만 (FR-027)
 * [담당] 팀원 4 · #19   [화면] U-S11   [API] MW-01(선택지)·MW-02·MW-04(검사)
 * [연결]
 *  - 호출받음: WorkService.getFormOptions(선택지), WorkService.saveGeneralWork·updateGeneralWork(검사 — 수정 때도 "수정 시점의 배지"로 다시 검사)
 *  - 호출함:   BadgeService.getHighestLevel (계약 C12, #15)
 * [주의] 선택지를 화면에서 줄여 보여 주는 것과 별개로, 저장 때 반드시 checkAllowed 로 다시 검사한다(화면 조작 방지).
 * [기술] 🟢 @Component Policy, 🟢 도메인 Service 계약 호출
 */
@Component
@RequiredArgsConstructor
public class WorkLevelPolicy {

    private final BadgeService badgeService;

    /**
     * 고를 수 있는 수준 목록(낮은 수준부터). 취미를 아직 고르지 않았거나 배지가 없으면 [BASIC].
     * 예: 중급 배지 보유 → [BASIC, INTERMEDIATE]
     */
    public List<CourseLevel> allowedLevels(Long userId, Long hobbyId) {
        CourseLevel max = hobbyId == null ? null : badgeService.getHighestLevel(userId, hobbyId);
        int maxCode = max == null ? CourseLevel.BASIC.getCode() : max.getCode();   // 배지 없으면 입문
        return Arrays.stream(CourseLevel.values())
                .filter(level -> level.getCode() <= maxCode)
                .toList();
    }

    /**
     * 수준 태그 검사
     * @throws BusinessException LEVEL_TAG_NOT_ALLOWED (보유 배지보다 높은 수준, 또는 수준 없음)
     */
    public void checkAllowed(Long userId, Long hobbyId, CourseLevel level) {
        if (level == null || !allowedLevels(userId, hobbyId).contains(level)) {
            throw new BusinessException(ErrorCode.LEVEL_TAG_NOT_ALLOWED);
        }
    }
}
