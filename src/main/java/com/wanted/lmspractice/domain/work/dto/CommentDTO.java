package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.work.entity.WorkComment;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 댓글·1단계 답글 표시값 — 삭제된 댓글은 본문 문구만, 탈퇴·없는 작성자는 고정 문구, 답글은 replies 에 담긴다
 * [담당] 팀원 4 · #18   [API] WB-02 모델 comments
 * [연결] 만든 곳: WorkBoardService.getComments / 쓰는 곳: templates/work/detail.html
 *  - 실명·로그인 아이디는 담지 않는다. authorName 은 닉네임(강사는 이름)이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CommentDTO {

    public static final String WITHDRAWN_NAME = "--탈퇴한 사용자 입니다--";   // API WB-02 ✅

    private Long id;
    private Long parentId;
    private String authorName;
    private String body;
    private boolean deleted;
    /** 작품 작성자가 쓴 글 — "(작성자)" 표시 */
    private boolean workAuthor;
    /** 보는 사람이 쓴 글 — 수정·삭제 버튼 */
    private boolean mine;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies = new ArrayList<>();

    public static CommentDTO of(WorkComment comment, String authorName, boolean workAuthor, boolean mine) {
        return new CommentDTO(comment.getId(), comment.getParentId(),
                authorName != null ? authorName : WITHDRAWN_NAME, comment.getBody(), comment.isDeleted(),
                workAuthor, mine, comment.getCreatedAt(), new ArrayList<>());
    }
}
