package com.wanted.lmspractice.domain.work.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 댓글·답글 (1단계) [P1] ❓ 17테이블 미포함 — 스키마 확인
 * 담당: 팀원 4 · #18
 * 칼럼은 resources/sql/schema.sql의 `work_comments` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "work_comments")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class WorkComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status = CommentStatus.NORMAL;  // DB 기본값과 같은 초기값

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /** 자발 삭제된 댓글의 본문 대체 문구 (API CM-C3) */
    public static final String DELETED_BODY = "삭제된 댓글입니다";

    /** 댓글(parentId == null) 또는 1단계 답글 작성 — "답글의 답글 금지"는 서비스가 먼저 검사한다 */
    public static WorkComment create(Long workId, Long parentId, Long authorId, String body) {
        WorkComment comment = new WorkComment();
        comment.workId = workId;
        comment.parentId = parentId;
        comment.authorId = authorId;
        comment.body = body;
        return comment;
    }

    public boolean isAuthor(Long userId) {
        return authorId != null && authorId.equals(userId);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }

    /** 본문 수정 — 삭제된 댓글은 서비스가 막는다 */
    public void edit(String body) {
        this.body = body;
    }

    /** 소프트 삭제 — 본문을 지우고 상태만 DELETED (DB CHECK: DELETED 이면 deleted_at 필수). 답글은 그대로 남는다 */
    public void delete(LocalDateTime now) {
        this.status = CommentStatus.DELETED;
        this.deletedAt = now;
        this.body = DELETED_BODY;
    }
}
