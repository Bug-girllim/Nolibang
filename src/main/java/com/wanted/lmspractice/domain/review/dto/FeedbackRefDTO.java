package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 일반 작품 참고용 피드백 (계약 C11 반환값)
 * 담당: 팀원 3 · #14
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 *
 * [#19 추가 — 팀원 3 확인 필요] 일반 작품 등록 화면(U-S11, API MW-01)이 "본인 최근 공식 피드백 1개 전문"을 보여 주고
 * 체크하면 reviewRoundId 를 works.reference_review_id 에 저장해야 해서, 화면이 쓰는 최소 필드만 먼저 정의했다.
 * 값을 채우는 실제 조회(ReviewQueryService.findLatestFeedbackForReference)는 #14의 몫이다.
 * 규칙(D-17·D-47): 같은 사용자·같은 취미의 확정된 평가 회차 중 가장 최근 1건 — 승인이면 피드백, 반려면 반려 사유.
 * 필드 이름이 다르게 정해지면 work/form.html 과 WorkService.checkReference 의 읽는 부분만 같이 고치면 된다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class FeedbackRefDTO {

    /** 평가 회차 id(review_rounds.id) — works.reference_review_id 로 저장된다 */
    private Long reviewRoundId;
    /** 강의 이름 (예: "색연필화 심화") */
    private String courseTitle;
    /** 강의 수준 */
    private CourseLevel level;
    /** APPROVED(승인 피드백) 또는 REJECTED(반려 사유) */
    private ReviewStatus status;
    /** 피드백 전문(review_rounds.feedback_body) */
    private String body;
    /** 평가 확정 시각(review_rounds.finalized_at) */
    private LocalDateTime finalizedAt;

    /** 화면 표시: "승인 피드백" / "반려 사유" */
    public String getKindLabel() {
        return status == ReviewStatus.REJECTED ? "반려 사유" : "승인 피드백";
    }
}
