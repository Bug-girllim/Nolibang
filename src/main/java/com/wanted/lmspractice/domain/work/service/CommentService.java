package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.work.dto.LikeResultDTO;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkComment;
import com.wanted.lmspractice.domain.work.entity.WorkLike;
import com.wanted.lmspractice.domain.work.entity.WorkLikeId;
import com.wanted.lmspractice.domain.work.policy.CommentPolicy;
import com.wanted.lmspractice.domain.work.policy.WorkAccessPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkCommentRepository;
import com.wanted.lmspractice.domain.work.repository.WorkLikeRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 댓글·답글 작성, 본인 수정·삭제(소프트 삭제), 좋아요 토글, 그에 따른 알림 발송
 * [담당] 팀원 4 · #18   [화면] U-S10, T-S10   [API] CM-C1·C2·C3, WB-03   [기획] FR-033, D-09
 * [연결]
 *  - 호출받음: CommentController(작성·수정·삭제), WorkBoardController.like(좋아요)
 *  - 호출함:   WorkAccessPolicy(열람 가능한 작품에만), CommentPolicy(작성 자격), NotificationService.notify(C7, 호출한 쪽 트랜잭션 안에서),
 *              InstructorAccountService.getGrantedInstructorIds(취미 담당 강사 알림 대상 — 계약 추가 요청)
 * [규칙]
 *  - 답글은 1단계만(답글의 답글 불가, INVALID_INPUT). 본문 1~1000자(DB), 앞뒤 공백 제거.
 *  - 수정·삭제는 본인 댓글만. 삭제는 본문을 "삭제된 댓글입니다"로 바꾸고 답글은 유지한다.
 *  - 좋아요는 작품당 1회, 다시 누르면 취소, 본인 작품도 가능. 열람할 수 있는 작품에만 누를 수 있다.
 *  - 알림: 새 댓글→작품 작성자(COMMENT_ON_MY_WORK), 답글→원 댓글 작성자(REPLY_TO_MY_COMMENT), 둘 다 본인 글이면 생략.
 *         같은 사건에 취미 권한 강사에게 PUBLIC_COMMENT (본인·이미 알림 받은 사람 제외).
 * [기술] 🟢 @Transactional, 🟢 JPA 변경 감지
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    public static final int MAX_CONTENT_LENGTH = 1000;   // work_comments.body VARCHAR(1000)

    private final WorkRepository workRepository;
    private final WorkVersionRepository workVersionRepository;
    private final WorkCommentRepository workCommentRepository;
    private final WorkLikeRepository workLikeRepository;
    private final WorkAccessPolicy workAccessPolicy;
    private final CommentPolicy commentPolicy;
    private final NotificationService notificationService;
    private final InstructorAccountService instructorAccountService;

    /**
     * 댓글·답글 작성 (CM-C1)
     * 호출: CommentController.write (POST /comments)
     * @param parentId null 이면 댓글, 있으면 그 댓글에 대한 1단계 답글
     * @return 새 댓글 id (화면이 #comment-{id} 로 이동)
     * @throws BusinessException WORK_NOT_FOUND, WORK_ACCESS_DENIED(열람 불가), COMMENT_NOT_ALLOWED(자격 없음), INVALID_INPUT(빈 내용·너무 김·답글의 답글·다른 작품의 댓글)
     */
    @Transactional
    public Long writeComment(Long userId, Role role, Long workId, Long parentId, String content) {
        String body = validateContent(content);
        Work work = findWork(workId);
        workAccessPolicy.checkCanView(work, userId, role);

        WorkComment parent = parentId == null ? null : findParent(parentId, workId);
        commentPolicy.checkCanWrite(work, userId, role, parent != null);   // 자격 검사는 입력이 올바른 것을 확인한 뒤

        WorkComment saved = workCommentRepository.save(WorkComment.create(workId, parentId, userId, body));
        sendNotifications(work, parent, saved, userId);
        return saved.getId();
    }

    /**
     * 댓글 수정 (CM-C2) — 본인 댓글만, 삭제된 댓글은 수정 불가
     * @return 댓글이 속한 작품 id (화면 이동용)
     * @throws BusinessException NOT_FOUND(없음·삭제됨), ACCESS_DENIED(남의 댓글), INVALID_INPUT
     */
    @Transactional
    public Long editComment(Long userId, Long commentId, String content) {
        WorkComment comment = findOwnComment(userId, commentId);
        comment.edit(validateContent(content));   // 변경 감지로 저장
        return comment.getWorkId();
    }

    /**
     * 댓글 삭제 (CM-C3) — 본인 댓글만. 본문은 "삭제된 댓글입니다"로 바뀌고 답글은 유지된다
     * @return 댓글이 속한 작품 id
     */
    @Transactional
    public Long deleteComment(Long userId, Long commentId) {
        WorkComment comment = findOwnComment(userId, commentId);
        comment.delete(LocalDateTime.now());
        return comment.getWorkId();
    }

    /**
     * 좋아요 토글 (WB-03) — 있으면 취소, 없으면 추가. 본인 작품도 가능
     * @throws BusinessException WORK_NOT_FOUND, WORK_ACCESS_DENIED
     */
    @Transactional
    public LikeResultDTO toggleLike(Long userId, Role role, Long workId) {
        Work work = findWork(workId);
        workAccessPolicy.checkCanView(work, userId, role);

        WorkLikeId id = new WorkLikeId(workId, userId);
        boolean liked;
        if (workLikeRepository.existsById(id)) {
            workLikeRepository.deleteById(id);
            liked = false;
        } else {
            workLikeRepository.save(WorkLike.create(workId, userId));
            liked = true;
        }
        workLikeRepository.flush();   // 바로 아래 count 에 방금 변경이 보이도록
        return new LikeResultDTO(liked, workLikeRepository.countByWorkId(workId));
    }

    private Work findWork(Long workId) {
        return workRepository.findById(workId)
                .filter(work -> !work.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
    }

    // 부모 댓글은 같은 작품의 최상위 댓글이어야 한다 (답글의 답글 금지 — API CM-C1)
    private WorkComment findParent(Long parentId, Long workId) {
        WorkComment parent = workCommentRepository.findById(parentId)
                .filter(c -> c.getWorkId().equals(workId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (parent.isReply()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return parent;
    }

    // 존재를 숨기지 않는다: 없으면 NOT_FOUND, 남의 댓글이면 ACCESS_DENIED (API CM-C2·C3)
    private WorkComment findOwnComment(Long userId, Long commentId) {
        WorkComment comment = workCommentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!comment.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return comment;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank() || content.strip().length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return content.strip();
    }

    // 알림 3종 — NotificationService 는 호출한 쪽(이 메서드) 트랜잭션 안에서 실행된다(계약 C7)
    private void sendNotifications(Work work, WorkComment parent, WorkComment saved, Long writerId) {
        String title = workVersionRepository.findById(work.getDisplayVersionId()).map(v -> v.getTitle()).orElse("작품");
        Set<Long> notified = new HashSet<>();
        notified.add(writerId);   // 본인에게는 보내지 않는다

        if (parent == null) {
            if (notified.add(work.getAuthorId())) {
                notificationService.notify(work.getAuthorId(), NotificationKind.COMMENT_ON_MY_WORK, saved.getId(),
                        "내 작품에 새 댓글", "'" + title + "' 작품에 새 댓글이 달렸습니다.");
            }
        } else if (notified.add(parent.getAuthorId())) {
            notificationService.notify(parent.getAuthorId(), NotificationKind.REPLY_TO_MY_COMMENT, saved.getId(),
                    "내 댓글에 새 답글", "'" + title + "' 작품의 내 댓글에 답글이 달렸습니다.");
        }

        for (Long instructorId : instructorAccountService.getGrantedInstructorIds(work.getHobbyId())) {
            if (notified.add(instructorId)) {
                // 같은 댓글을 여러 강사에게 보내므로 받는 사람까지 넣어 중복 방지 키를 만든다
                notificationService.notify(instructorId, NotificationKind.PUBLIC_COMMENT, saved.getId(),
                        "담당 취미 작품에 새 댓글", "'" + title + "' 작품에 새 댓글이 달렸습니다.",
                        "PUBLIC_COMMENT:" + saved.getId() + ":" + instructorId);
            }
        }
    }
}
