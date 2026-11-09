package com.wanted.lmspractice.domain.work.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 작품 상세 화면에 필요한 값 묶음 — 작품, 댓글 트리, 댓글·답글 작성 가능 여부, 작성자 여부
 * [담당] 팀원 4 · #18   [API] WB-02 모델 work·comments·canComment·isOwner
 * [연결] 만든 곳: WorkBoardService.getDetail / 쓰는 곳: WorkBoardController(모델로 풀어서 전달)
 */
@Getter
@AllArgsConstructor
public class WorkDetailDTO {

    private final WorkDTO work;
    private final List<CommentDTO> comments;
    /** 새 댓글 작성 가능 (CommentPolicy) */
    private final boolean canComment;
    /** 답글 작성 가능 (작성자는 배지 무관) */
    private final boolean canReply;
    private final boolean owner;
}
