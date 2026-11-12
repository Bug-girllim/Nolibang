package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.CommentDTO;
import com.wanted.lmspractice.domain.work.dto.LikeResultDTO;
import com.wanted.lmspractice.domain.work.entity.WorkComment;
import com.wanted.lmspractice.domain.work.repository.WorkCommentRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] CommentService 통합 테스트 — 댓글·답글 작성 자격, 1단계 답글, 수정·삭제 권한, 소프트 삭제 후 답글 유지, 좋아요 토글
 * [담당] 팀원 4 · #18   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 변경은 테스트 트랜잭션이 롤백한다.
 *       시드: 작품 2(공개, 작성자 learnerB(3), 색연필화 입문, 좋아요 2: learnerB·learnerC), 작품 3(learnerB 비공개),
 *             작품 1(공개, 작성자 learnerB, 디저트 만들기 중급 최종 과제), 작품 5(공개, 작성자 learnerC, 색연필화 입문 — 댓글 3 teacherA), 댓글 1(teacherA) ← 답글 2(learnerB) — 둘 다 작품 2.
 *             learnerB 는 디저트 만들기 중급 수료 → 디저트 중급 배지(색연필화 배지는 없음), learnerA·learnerC 는 배지 없음.
 *             teacherA 는 색연필화 취미 권한이 있다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class CommentServiceTest {

    private static final Long PUBLIC_WORK = 2L, PRIVATE_WORK = 3L, BADGE_WORK = 1L, TEACHER_COMMENT = 1L, AUTHOR_REPLY = 2L;

    @Autowired
    private CommentService commentService;
    @Autowired
    private WorkBoardService workBoardService;
    @Autowired
    private WorkCommentRepository workCommentRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("배지가 있는 학습자(작품 수준 이하)는 댓글을 쓸 수 있고, 배지가 없는 학습자는 COMMENT_NOT_ALLOWED")
    void badgeRequiredForComment() {
        // learnerB: 디저트 중급 배지 → 디저트 중급 작품 1에 댓글 가능 (배지 수준 ≥ 작품 수준)
        Long id = commentService.writeComment(userId("learnerB"), Role.USER, BADGE_WORK, null, "배지로 쓰는 댓글");
        assertThat(workCommentRepository.findById(id).orElseThrow().getBody()).isEqualTo("배지로 쓰는 댓글");

        assertThatThrownBy(() -> commentService.writeComment(userId("learnerA"), Role.USER, BADGE_WORK, null, "배지 없음"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COMMENT_NOT_ALLOWED));
        // 다른 취미(색연필화) 배지가 없으면 자기 작품이라도 새 댓글은 쓸 수 없다
        assertThatThrownBy(() -> commentService.writeComment(userId("learnerB"), Role.USER, PUBLIC_WORK, null, "다른 취미"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COMMENT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("취미 권한 강사는 댓글을 쓸 수 있고, 관리자는 쓸 수 없다")
    void instructorAllowedAdminNot() {
        assertThat(commentService.writeComment(userId("teacherA"), Role.INSTRUCTOR, PUBLIC_WORK, null, "강사 댓글")).isNotNull();

        assertThatThrownBy(() -> commentService.writeComment(userId("admin"), Role.ADMIN, PUBLIC_WORK, null, "관리자"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COMMENT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("답글은 1단계만: 댓글에는 답글 가능, 답글의 답글은 INVALID_INPUT, 다른 작품의 댓글을 부모로 줘도 INVALID_INPUT")
    void oneLevelReplyOnly() {
        Long reply = commentService.writeComment(userId("learnerB"), Role.USER, PUBLIC_WORK, TEACHER_COMMENT, "작성자 답글");
        assertThat(workCommentRepository.findById(reply).orElseThrow().getParentId()).isEqualTo(TEACHER_COMMENT);

        assertThatThrownBy(() -> commentService.writeComment(userId("learnerB"), Role.USER, PUBLIC_WORK, AUTHOR_REPLY, "답글의 답글"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> commentService.writeComment(userId("learnerB"), Role.USER, BADGE_WORK, TEACHER_COMMENT, "다른 작품"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("빈 내용·공백·1001자는 INVALID_INPUT, 없는 작품은 WORK_NOT_FOUND, 비공개 작품은 WORK_ACCESS_DENIED")
    void inputAndAccessRejections() {
        Long teacher = userId("teacherA");
        assertThatThrownBy(() -> commentService.writeComment(teacher, Role.INSTRUCTOR, PUBLIC_WORK, null, "   "))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> commentService.writeComment(teacher, Role.INSTRUCTOR, PUBLIC_WORK, null, "가".repeat(1001)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThat(commentService.writeComment(teacher, Role.INSTRUCTOR, PUBLIC_WORK, null, "가".repeat(1000))).isNotNull();   // 경계값

        assertThatThrownBy(() -> commentService.writeComment(teacher, Role.INSTRUCTOR, 999999L, null, "없는 작품"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThatThrownBy(() -> commentService.writeComment(userId("learnerC"), Role.USER, PRIVATE_WORK, null, "비공개 작품"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("수정: 본인은 가능, 타인은 ACCESS_DENIED, 없는 댓글은 NOT_FOUND")
    void editOwnOnly() {
        Long workId = commentService.editComment(userId("teacherA"), TEACHER_COMMENT, "고친 댓글");
        assertThat(workId).isEqualTo(PUBLIC_WORK);
        assertThat(workCommentRepository.findById(TEACHER_COMMENT).orElseThrow().getBody()).isEqualTo("고친 댓글");

        assertThatThrownBy(() -> commentService.editComment(userId("learnerB"), TEACHER_COMMENT, "남의 댓글 수정"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> commentService.editComment(userId("teacherA"), 999999L, "없는 댓글"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("삭제: 본인만 가능하고 본문은 '삭제된 댓글입니다'로 바뀌며 답글은 그대로 남는다. 삭제된 댓글은 수정할 수 없다")
    void softDeleteKeepsReplies() {
        assertThatThrownBy(() -> commentService.deleteComment(userId("learnerB"), TEACHER_COMMENT))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));

        commentService.deleteComment(userId("teacherA"), TEACHER_COMMENT);

        WorkComment deleted = workCommentRepository.findById(TEACHER_COMMENT).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getBody()).isEqualTo("삭제된 댓글입니다");
        assertThat(deleted.getDeletedAt()).isNotNull();

        CommentDTO root = workBoardService.getDetail(PUBLIC_WORK, userId("learnerC"), Role.USER).getComments().get(0);
        assertThat(root.isDeleted()).isTrue();
        assertThat(root.getReplies()).extracting(CommentDTO::getId).containsExactly(AUTHOR_REPLY);   // 답글 유지

        assertThatThrownBy(() -> commentService.editComment(userId("teacherA"), TEACHER_COMMENT, "삭제 후 수정"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("좋아요 토글: 시드에서 이미 누른 사용자는 취소→다시 추가, 본인 작품도 가능, 비공개 작품은 403")
    void toggleLike() {
        Long learnerC = userId("learnerC");

        LikeResultDTO off = commentService.toggleLike(learnerC, Role.USER, PUBLIC_WORK);
        assertThat(off.isLiked()).isFalse();
        assertThat(off.getLikeCount()).isEqualTo(1);

        LikeResultDTO on = commentService.toggleLike(learnerC, Role.USER, PUBLIC_WORK);
        assertThat(on.isLiked()).isTrue();
        assertThat(on.getLikeCount()).isEqualTo(2);

        LikeResultDTO own = commentService.toggleLike(userId("learnerB"), Role.USER, PUBLIC_WORK);   // 본인 작품 (시드에서 이미 누름 → 취소)
        assertThat(own.isLiked()).isFalse();

        assertThatThrownBy(() -> commentService.toggleLike(learnerC, Role.USER, PRIVATE_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }
}
