package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
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
 * [역할] 성장 타임라인 화면 값 — 작품 기록 목록, 취미 필터 선택지, 취미 선택 시 첫/최근 작품 (FR-028)
 * [담당] 팀원 4 · #19   [화면] U-S12 — templates/work/growth.html (모델 이름 timeline)   [API] MW-07
 * [연결]
 *  - 만든 곳: GrowthService.getTimeline
 *  - 쓰는 곳: work/growth.html
 * [규칙]
 *  - items: 최종 과제 + 일반 작품, 등록일 최신순(수정해도 순서 불변)
 *  - first/latest: 취미를 골랐을 때만. 작품이 1개면 first 만 있고 latest 는 null (D-13). 전체 보기에서는 둘 다 null
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class TimelineDTO {

    /** 필터에서 고른 취미 id. 전체 보기면 null */
    private Long selectedHobbyId;
    /** 고른 취미 이름 (요약 문구용). 전체 보기면 null */
    private String selectedHobbyName;
    /** 필터 선택지 — 내가 작품을 남긴 취미만 */
    private List<HobbyDTO> hobbies;
    private List<Item> items;
    /** "나의 첫 시작" */
    private Item first;
    /** "가장 최근 실습" */
    private Item latest;

    /**
     * [역할] 타임라인 한 줄 = 작품 1개 (지금 보여 줄 버전의 제목)
     * 회고·참고 피드백(D-14·D-17), 최종 과제면 공식 피드백 링크용 평가 회차를 함께 담는다.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @ToString
    public static class Item {

        private Long workId;
        private WorkKind kind;
        private String title;
        private LocalDateTime createdAt;
        private Long hobbyId;
        private String hobbyName;
        private CourseLevel level;
        private Visibility visibility;
        private boolean adminHidden;
        private String reflection;
        /** 이 작품에 연결한 참고 피드백(평가 회차 id). 없으면 null */
        private Long referenceReviewId;
        /** 최종 과제의 최근 평가 회차 id·상태 — 일반 작품이거나 미제출이면 null */
        private Long reviewRoundId;
        private ReviewStatus reviewStatus;
        /** 대표 이미지 id — 첫/최근 작품 썸네일에만 채운다 */
        private Long thumbnailFileId;

        public static Item of(Work work, WorkVersion version, String hobbyName,
                              Long reviewRoundId, ReviewStatus reviewStatus) {
            return new Item(work.getId(), work.getKind(), version.getTitle(), work.getCreatedAt(),
                    work.getHobbyId(), hobbyName, work.getLevel(), work.getVisibility(),
                    Boolean.TRUE.equals(work.getAdminHidden()), work.getReflection(), work.getReferenceReviewId(),
                    reviewRoundId, reviewStatus, null);
        }

        public boolean isGeneral() {
            return kind == WorkKind.GENERAL;
        }

        public boolean isPublicVisibility() {
            return visibility == Visibility.PUBLIC;
        }

        /** 공식 피드백이 확정된 최종 과제만 "공식 피드백 보기" 링크를 보인다 */
        public boolean isFeedbackReady() {
            return reviewStatus == ReviewStatus.APPROVED || reviewStatus == ReviewStatus.REJECTED;
        }

        /** 칩 문구: "일반 작품" / "최종 과제 · 수료" 등 */
        public String getKindLabel() {
            if (isGeneral()) {
                return "일반 작품";
            }
            if (reviewStatus == null) {
                return "최종 과제";
            }
            return "최종 과제 · " + switch (reviewStatus) {
                case WAITING -> "평가 대기";
                case IN_REVIEW -> "평가 중";
                case APPROVED -> "수료";
                case REJECTED -> "반려";
            };
        }

        public String getVisibilityLabel() {
            return isPublicVisibility() ? "공개" : "비공개";
        }

        public String getLevelLabel() {
            return level == null ? "" : level.getLabel();
        }
    }
}
