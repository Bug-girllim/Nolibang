package com.wanted.lmspractice.domain.review.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 강사 평가 목록(T-S03) 화면 한 장 — 현재 탭·필터에 맞는 행, 탭별 건수, 「담당 분반」 필터 선택지
 * [담당] 팀원 3 · #14   [화면] T-S03   [API] RV-01
 * [연결]
 *  - 만든 곳: ReviewService.getReviewList
 *  - 쓰는 곳: InstructorReviewController.list → 모델 reviews(행 목록), page(건수·분반) — templates/instructor/reviews.html
 * [기술] 🟢 DTO
 */
@AllArgsConstructor
@Getter
@ToString
public class ReviewListPageDTO {

    /** 현재 탭·분반·기한 필터를 적용한 행 */
    private List<ReviewListDTO> reviews;
    /** 탭 건수 — 분반 필터는 적용하고 기한 필터는 적용하지 않은 값 */
    private long waitingCount;
    private long inReviewCount;
    private long doneCount;
    /** 「담당 분반」 select 선택지 */
    private List<SectionFilterDTO> sections;

    /** 「담당 분반」 select 선택지 1개 */
    @AllArgsConstructor
    @Getter
    @ToString
    public static class SectionFilterDTO {
        private Long sectionId;
        /** 예: "색연필화 기초 · 입문 · A분반" */
        private String label;
    }
}
