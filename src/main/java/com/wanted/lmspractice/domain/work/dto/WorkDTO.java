package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkFile;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 작품 표시용 값 — 작품 + 지금 보여 줄 버전(제목·설명) + 이미지 id 목록, 게시판 카드·상세 화면용 부가 값(닉네임·취미명·좋아요 등)
 * [담당] 팀원 4 · #16(기본 값) · #18(게시판·상세용 부가 값)
 * [연결] 만든 곳: WorkService.getWork(기본 값), WorkBoardService(부가 값을 setter 로 채움) / 쓰는 곳: #13·#14 화면, templates/work/board·detail·board-instructor
 *        이미지는 id만 담는다 — 화면은 th:src="@{/files/works/{id}(id=${imageId})}" 로 권한 검사 URL을 쓴다.
 *        부가 값은 게시판·상세에서만 채워지고, 다른 호출에서는 null·0 이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class WorkDTO {

    private Long workId;
    private Long workVersionId;
    private WorkKind kind;
    private Long authorId;
    private Long hobbyId;
    private CourseLevel level;
    private Visibility visibility;
    private int versionNo;
    private String title;
    private String description;
    private List<Long> imageIds;

    // ── #18 게시판·상세용 부가 값 ──
    private String authorNickname;
    private String hobbyName;
    private LocalDateTime publishedAt;
    private long likeCount;
    private long commentCount;
    private boolean liked;
    /** 카드 대표 이미지(첫 장) work_files.id — 없으면 null */
    private Long thumbnailFileId;
    /** 강사 게시판: 이 작품에 댓글을 달 수 있는 취미 권한이 있는지 */
    private boolean commentable;
    /** 강사 게시판: 내가 이 작품에 남긴 댓글 수 */
    private long myCommentCount;

    // ── #20 관리자 화면용 ──
    /** 관리자 숨김 상태 */
    private boolean hidden;
    private String hiddenReason;

    public static WorkDTO of(Work work, WorkVersion version, List<WorkFile> files) {
        WorkDTO dto = new WorkDTO();
        dto.workId = work.getId();
        dto.workVersionId = version.getId();
        dto.kind = work.getKind();
        dto.authorId = work.getAuthorId();
        dto.hobbyId = work.getHobbyId();
        dto.level = work.getLevel();
        dto.visibility = work.getVisibility();
        dto.versionNo = version.getVersionNo();
        dto.title = version.getTitle();
        dto.description = version.getDescription();
        dto.imageIds = files.stream().map(WorkFile::getId).toList();
        dto.publishedAt = work.getPublishedAt();
        return dto;
    }
}
