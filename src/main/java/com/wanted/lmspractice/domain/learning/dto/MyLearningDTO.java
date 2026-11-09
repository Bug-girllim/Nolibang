package com.wanted.lmspractice.domain.learning.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 나의 학습 현황 한 화면 — 선택한 탭의 카드 목록과 위쪽 요약 숫자 5개
 * [담당] 팀원 2 · #11   [화면] U-S08   [API] ML-01
 * [연결]
 *  - 만든 곳: MyLearningService.getMyLearning(of)
 *  - 쓰는 곳: MyLearningController → templates/learning/my-learning.html
 * [기술] 🟢 DTO 정적 팩토리
 * 요약 숫자는 탭을 눌러도 줄어들지 않도록 "전체 카드" 기준으로 센다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class MyLearningDTO {

    /** 선택한 탭에 해당하는 카드만 */
    private List<MyCourseDTO> courses;
    /** 학습 중 */
    private long learningCount;
    /** 대기·자동 확정 */
    private long waitingCount;
    /** 평가 대기·진행 */
    private long reviewingCount;
    /** 수료 완료 */
    private long completedCount;
    /** 반려·만료·취소 */
    private long endedCount;

    /** 전체 카드로 요약 숫자를 세고, 선택한 탭의 카드만 골라 담는다 */
    public static MyLearningDTO of(List<MyCourseDTO> all, LearningFilter filter) {
        return new MyLearningDTO(
                all.stream().filter(card -> filter.matches(card.getState())).toList(),
                count(all, LearningFilter.LEARNING),
                count(all, LearningFilter.WAITING),
                count(all, LearningFilter.REVIEWING),
                count(all, LearningFilter.COMPLETED),
                count(all, LearningFilter.REJECTED) + count(all, LearningFilter.ENDED));
    }

    private static long count(List<MyCourseDTO> all, LearningFilter filter) {
        return all.stream().filter(card -> filter.matches(card.getState())).count();
    }
}
