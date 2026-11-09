package com.wanted.lmspractice.domain.work.entity;

/**
 * 댓글 상태 (work_comments.status) — DELETED면 화면에 "삭제된 댓글입니다"
 * 담당: 팀원 4 · #18
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum CommentStatus {
    NORMAL,
    DELETED
}
