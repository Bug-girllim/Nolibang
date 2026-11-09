package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 게시판 검색·필터·정렬 조건 — GET /works 쿼리 파라미터를 그대로 받는다 (모두 선택)
 * [담당] 팀원 4 · #18   [API] WB-01
 * [연결] 받는 곳: WorkBoardController(@ModelAttribute) → WorkBoardService.search / 화면 폼 값 유지(cond)
 *  - categoryId·hobbyId·level·kind·keyword·sort 는 P1·P2 필터, scope·myCommented 는 강사 화면(T-S10) 전용
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class WorkSearchDTO {

    public static final String SORT_LATEST = "latest";
    public static final String SORT_LIKES = "likes";
    public static final String SCOPE_MINE = "mine";   // 강사: 내 담당 취미 작품만 (기본)
    public static final String SCOPE_ALL = "all";     // 강사: 전체 공개 작품

    private Long categoryId;
    private Long hobbyId;
    private CourseLevel level;
    private WorkKind kind;
    private String keyword;
    private String sort = SORT_LATEST;
    private int page;
    private String scope = SCOPE_MINE;
    private boolean myCommented;

    public boolean isSortByLikes() {
        return SORT_LIKES.equals(sort);
    }
}
