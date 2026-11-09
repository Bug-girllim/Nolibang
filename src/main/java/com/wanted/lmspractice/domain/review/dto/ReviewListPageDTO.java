package com.wanted.lmspractice.domain.review.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강사 평가 목록 화면(T-S03) 한 장에 필요한 값 묶음 — 선택한 탭·필터, 행 목록, 탭별 건수, 담당 분반 선택지
 * [담당] 팀원 3 · #14   [API] RV-01 (모델 이름 `reviewPage` — API 명세서에 없어 새로 정함, PR에 보고)
 * [연결] ReviewService.getReviewPage → InstructorReviewController.list → templates/instructor/reviews.html
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewListPageDTO {

    /** 기한 필터 값 — 마감 임박(48시간 이하) */
    public static final String DUE_SOON = "SOON";
    /** 기한 필터 값 — 기한 초과 */
    public static final String DUE_OVERDUE = "OVERDUE";

    private ReviewTab tab;
    private Long sectionId;
    private String due;
    private List<ReviewListDTO> reviews;
    /** 탭 버튼의 건수 — 분반·기한 필터를 적용한 뒤의 건수 */
    private long waitingCount;
    private long inReviewCount;
    private long doneCount;
    private List<SectionOption> sections;

    /** 담당 분반 필터 선택지 — "색연필화 기초 · 입문 · A분반" */
    @Getter
    @AllArgsConstructor
    @ToString
    public static class SectionOption {
        private Long id;
        private String label;
    }
}
