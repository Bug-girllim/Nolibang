package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkFile;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 작품 표시용 값 — 작품 + 지금 보여 줄 버전(제목·설명) + 이미지 id 목록
 * [담당] 팀원 4 · #16
 * [연결] 만든 곳: WorkService.getWork / 쓰는 곳: #18 작품 상세, #13·#14 제출·평가 화면
 *        이미지는 id만 담는다 — 화면은 th:src="@{/files/works/{id}(id=${imageId})}" 로 권한 검사 URL을 쓴다.
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

    public static WorkDTO of(Work work, WorkVersion version, List<WorkFile> files) {
        return new WorkDTO(work.getId(), version.getId(), work.getKind(), work.getAuthorId(), work.getHobbyId(),
                work.getLevel(), work.getVisibility(), version.getVersionNo(), version.getTitle(),
                version.getDescription(), files.stream().map(WorkFile::getId).toList());
    }
}
