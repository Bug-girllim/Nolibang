package com.wanted.lmspractice.domain.work.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 게시판 한 페이지 — 카드 목록, 조건에 맞는 전체 개수, 현재 쪽(0부터), 다음 쪽 유무
 * [담당] 팀원 4 · #18   [API] WB-01 모델 works
 * [연결] 만든 곳: WorkBoardService.search / 쓰는 곳: templates/work/board·board-instructor (works.items, works.totalCount …)
 */
@Getter
@AllArgsConstructor
public class WorkPageDTO {

    private final List<WorkDTO> items;
    private final long totalCount;
    private final int page;
    private final boolean hasNext;

    public boolean isHasPrevious() {
        return page > 0;
    }
}
