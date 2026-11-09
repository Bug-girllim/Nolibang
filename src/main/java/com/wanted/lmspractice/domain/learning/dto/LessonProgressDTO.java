package com.wanted.lmspractice.domain.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 차시별 진도 (계약 C10 반환값)
 * 담당: 팀원 3 · #12
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 *
 * [#11 추가 — 팀원 3 확인 필요] 나의 학습 현황(U-S08)이 "차시별 진도(비율·완료 여부)"를 보여 주려면 값이 필요해서,
 * 화면이 쓰는 최소 필드만 먼저 정의했다. 필드 이름이 다르게 정해지면 MyLearningService의 읽는 부분만 같이 고치면 된다.
 * 진도를 채우는 실제 구현(60% 판정·서버 관측 시간)은 #12의 몫이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LessonProgressDTO {

    private Long lessonId;
    /** 1~3차시 */
    private Integer lessonNo;
    private String title;
    /** 진도 비율 0~100 (인정 재생 시간 ÷ 영상 길이) */
    private int progressPercent;
    /** 실제 재생 60% 이상으로 완료 처리됐는지 (FR-020) */
    private boolean completed;
}
