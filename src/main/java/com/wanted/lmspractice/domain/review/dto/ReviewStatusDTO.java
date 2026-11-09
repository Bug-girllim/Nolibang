package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 최신 평가 상태 (계약 C11 반환값)
 * 담당: 팀원 3 · #14
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 *
 * [#11 추가 — 팀원 3 확인 필요] 나의 학습 현황(U-S08)이 "최종 과제 평가 상태·최초 제출 시각·피드백 링크"를 보여 주려면 값이 필요해서,
 * 화면이 쓰는 최소 필드만 먼저 정의했다. 필드 이름이 다르게 정해지면 MyLearningService의 읽는 부분만 같이 고치면 된다.
 * 규칙: 아직 과제를 내지 않았으면 status가 null이다(notSubmitted()). 평가 회차를 채우는 실제 구현은 #14의 몫이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ReviewStatusDTO {

    /** 가장 최근 평가 회차 id — 피드백 결과 화면 링크(/assignments/{reviewRoundId}/result)에 쓴다. 미제출이면 null */
    private Long reviewRoundId;
    /** 가장 최근 회차의 상태(평가 대기·진행·승인·반려). 아직 제출하지 않았으면 null */
    private ReviewStatus status;
    /** 최초 제출 시각(1회차 제출 시각). 미제출이면 null */
    private LocalDateTime firstSubmittedAt;

    /** 아직 최종 과제를 제출하지 않은 상태 */
    public static ReviewStatusDTO notSubmitted() {
        return new ReviewStatusDTO(null, null, null);
    }

    /** 최종 과제를 한 번이라도 제출했는지 */
    public boolean isSubmitted() {
        return status != null;
    }
}
