package com.wanted.lmspractice.domain.work.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 좋아요 토글 결과 JSON — { "liked": true, "likeCount": 12 }
 * [담당] 팀원 4 · #18   [API] WB-03
 * [연결] 만든 곳: CommentService.toggleLike / 쓰는 곳: WorkBoardController.like(@ResponseBody) → static/js/work-like.js
 */
@Getter
@AllArgsConstructor
public class LikeResultDTO {

    private final boolean liked;
    private final long likeCount;
}
