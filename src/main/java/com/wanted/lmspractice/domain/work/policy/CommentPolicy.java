package com.wanted.lmspractice.domain.work.policy;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.service.BadgeService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 작품 댓글·답글 작성 자격 판단 — 배지 수준, 취미 권한 강사, 작성자 답글 예외
 * [담당] 팀원 4 · #18   [기획] FR-033, API CM-C1
 * [연결]
 *  - 호출받음: CommentService.writeComment(작성 전 검사), WorkBoardService(상세·강사 게시판의 canComment 표시)
 *  - 호출함:   BadgeService.getHighestLevel(C12, 해당 취미 배지), InstructorAccountService.hasHobbyGrant(C3, 강사 취미 권한)
 * [규칙]
 *  - 학습자: 작품의 취미에서 작품 수준 이상의 배지가 있어야 한다 (입문 배지→입문 작품 / 중급→입문·중급 / 고급→전체)
 *  - 강사: 작품의 세부 취미 활동 권한이 있으면 댓글·답글 가능 (다른 취미는 열람만)
 *  - 작품 작성자: 배지와 무관하게 다른 사람의 댓글에 답글(1단계)만 가능 — 새 댓글은 위의 일반 규칙을 따른다
 *  - 관리자: 댓글을 쓰지 않는다 (숨김·복구만 한다)
 * [기술] 🟢 Policy 패턴
 */
@Component
@RequiredArgsConstructor
public class CommentPolicy {

    private final BadgeService badgeService;
    private final InstructorAccountService instructorAccountService;

    /** 새 댓글(1단계 최상위)을 쓸 수 있는지 — 작성자 예외는 포함하지 않는다 */
    public boolean canWriteComment(Work work, Long userId, Role role) {
        if (role == Role.INSTRUCTOR) {
            return instructorAccountService.hasHobbyGrant(userId, work.getHobbyId());
        }
        if (role != Role.USER) {
            return false;
        }
        CourseLevel highest = badgeService.getHighestLevel(userId, work.getHobbyId());
        return highest != null && highest.getCode() >= work.getLevel().getCode();
    }

    /** 답글을 쓸 수 있는지 — 작성자는 배지 무관, 그 밖에는 댓글과 같은 자격 */
    public boolean canWriteReply(Work work, Long userId, Role role) {
        return isLearnerAuthor(work, userId, role) || canWriteComment(work, userId, role);
    }

    /**
     * [성능 개선] 답글 자격 — canWriteComment 결과를 이미 구했을 때 다시 계산하지 않는다(배지 계산·강사 권한 조회 중복 제거).
     * 규칙은 canWriteReply(work, userId, role)와 같다. 호출: WorkBoardService.getDetail(댓글·답글 자격을 함께 구하는 작품 상세)
     */
    public boolean canWriteReply(Work work, Long userId, Role role, boolean canWriteComment) {
        return isLearnerAuthor(work, userId, role) || canWriteComment;
    }

    /** 작성자 본인(학습자)은 배지와 관계없이 답글을 쓸 수 있다 */
    private boolean isLearnerAuthor(Work work, Long userId, Role role) {
        return role == Role.USER && work.isAuthor(userId);
    }

    /** 자격이 없으면 COMMENT_NOT_ALLOWED(403) */
    public void checkCanWrite(Work work, Long userId, Role role, boolean reply) {
        boolean allowed = reply ? canWriteReply(work, userId, role) : canWriteComment(work, userId, role);
        if (!allowed) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_ALLOWED);
        }
    }
}
