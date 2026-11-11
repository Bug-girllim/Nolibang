package com.wanted.lmspractice.domain.work.policy;

import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 작품·작품 이미지 열람 권한 판단 (Q-02)
 * [담당] 팀원 4 · #16   [기획] Q-02, FR-036
 * [연결]
 *  - 호출받음: WorkService (이미지 출력·작품 조회), 이후 #18 작품 상세, #14 평가 화면(계약 C9은 이미지 URL만 쓰므로 직접 호출하지 않아도 된다)
 *  - 호출함:   SectionService.getCurrentInstructorId (계약 C6, #10 — 최종 과제의 "현재 담당 강사" 확인)
 * [규칙]
 *  - 삭제된 작품: 아무도 열람 불가 (D-63 삭제 기능은 미정이라 지금은 거의 해당 없음)
 *  - 공개 + 관리자 숨김 아님 → 로그인한 누구나
 *  - 비공개 또는 숨김 → 작성자 본인, 관리자, (최종 과제면) 현재 담당 강사
 *  - 이전 버전(표시 버전이 아닌 것)의 이미지 → 공개 여부와 관계없이 작성자 본인, 관리자, (최종 과제면) 현재 담당 강사
 *  - 로그인하지 않은 사람(userId == null)은 어떤 경우에도 불가
 * [주의] 이 정책은 권한만 판단한다. 존재를 숨길지(404) 알릴지(403)는 호출한 쪽이 정한다. 이미지 URL은 API FL-01에 따라 403.
 * [기술] 🟢 @Component, 🟢 도메인 Service 계약 호출
 */
@Component
@RequiredArgsConstructor
public class WorkAccessPolicy {

    private final SectionService sectionService;

    /** 열람 가능 여부 */
    public boolean canView(Work work, Long viewerId, Role viewerRole) {
        if (viewerId == null || viewerRole == null || work.isDeleted()) {
            return false;
        }
        if (work.isPubliclyVisible()) {
            return true;
        }
        return canViewPastVersions(work, viewerId, viewerRole);
    }

    /** 열람 불가면 WORK_ACCESS_DENIED(403) */
    public void checkCanView(Work work, Long viewerId, Role viewerRole) {
        if (!canView(work, viewerId, viewerRole)) {
            throw new BusinessException(ErrorCode.WORK_ACCESS_DENIED);
        }
    }

    /**
     * 지금 보여 주는 버전이 아닌 이전 버전(최종 과제의 이전 제출 회차 등)을 볼 수 있는지 — 작성자 본인, 관리자, (최종 과제면) 현재 담당 강사.
     * 작품을 공개해도 공개되는 것은 표시 버전(display_version_id)뿐이다. 다른 사람은 이전 회차 이미지를 fileId 로 직접 요청해도 볼 수 없다.
     * 호출: canView, WorkService.openFile
     */
    public boolean canViewPastVersions(Work work, Long viewerId, Role viewerRole) {
        if (viewerId == null || viewerRole == null || work.isDeleted()) {
            return false;
        }
        if (work.isAuthor(viewerId) || viewerRole == Role.ADMIN) {
            return true;
        }
        return viewerRole == Role.INSTRUCTOR && isCurrentInstructor(work, viewerId);
    }

    // 최종 과제만 담당 강사가 있다. 일반 작품(GENERAL)은 수강과 무관해 강사 예외가 없다
    private boolean isCurrentInstructor(Work work, Long instructorId) {
        if (work.getKind() != WorkKind.FINAL || work.getEnrollmentId() == null) {
            return false;
        }
        return instructorId.equals(sectionService.getCurrentInstructorId(work.getEnrollmentId()));
    }
}
