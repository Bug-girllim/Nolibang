package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 일반 작품 폼의 선택지 묶음 — 운영 중 취미 목록, 고를 수 있는 수준, 참고할 최근 공식 피드백
 * [담당] 팀원 4 · #19   [화면] U-S11   [API] MW-01·MW-03
 * [연결]
 *  - 만든 곳: WorkService.getFormOptions
 *  - 쓰는 곳: MyWorkController 가 API 명세의 모델 이름(hobbies, allowedLevels, reference)으로 나눠 담는다
 */
@AllArgsConstructor
@Getter
@ToString
public class WorkFormOptionsDTO {

    /** 운영 중 세부 취미만 (D-29) */
    private List<HobbyDTO> hobbies;
    /** 배지가 없으면 BASIC 만, 있으면 최고 배지 이하 (WorkLevelPolicy) */
    private List<CourseLevel> allowedLevels;
    /** 선택한 취미의 본인 최근 공식 피드백 1개. 취미를 고르지 않았거나 없으면 null (D-17) */
    private FeedbackRefDTO reference;
}
