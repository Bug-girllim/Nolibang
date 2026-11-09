package com.wanted.lmspractice.domain.review.policy;

import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 평가 권한 판단 — 수강 건이 속한 분반의 "현재" 담당 강사만 평가 화면을 보고 평가할 수 있다.
 * [담당] 팀원 3 · #14   [기획] FR-030, D-59(담당 변경 시 평가 권한은 새 담당에게)   [API] RV-02~RV-07
 * [연결]
 *  - 호출받음: ReviewService(상세 조회·시작·시작 취소·초안·승인·반려)
 *  - 호출함:   SectionService.getCurrentInstructorId (계약 C6, 팀원 2)
 * [근거] 담당 변경 후 이전 강사는 평가 불가, 새 강사는 같은 회차의 초안·잠금·마감을 그대로 이어받는다 —
 *        권한을 started_by·draft_author_id가 아니라 "지금 분반 담당자"로만 판단하므로 별도 이관 처리가 없다.
 * [기술] 🟢 @Component Policy(다른 도메인 조회가 필요한 규칙), 🟢 BusinessException
 */
@Component
@RequiredArgsConstructor
public class ReviewAssignmentPolicy {

    private final SectionService sectionService;

    /**
     * 현재 분반 담당 강사인지 확인한다. 담당이 비어 있는 분반(담당 공백)도 아무도 평가할 수 없다.
     * @throws BusinessException NOT_ASSIGNED_INSTRUCTOR
     */
    public void checkAssigned(Long enrollmentId, Long instructorId) {
        Long current = sectionService.getCurrentInstructorId(enrollmentId);
        if (current == null || !current.equals(instructorId)) {
            throw new BusinessException(ErrorCode.NOT_ASSIGNED_INSTRUCTOR);
        }
    }
}
