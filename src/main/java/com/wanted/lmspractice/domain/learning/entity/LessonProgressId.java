package com.wanted.lmspractice.domain.learning.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LessonProgress의 복합 기본키 (enrollment_id, lesson_id) — @IdClass 용
 * 담당: 팀원 3 · #03
 * 필드 이름·타입은 LessonProgress의 @Id 필드와 반드시 같아야 한다.
 * 🟡 미학습 기술(복합키 매핑) — 11-06 팀 결정으로 사용
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class LessonProgressId implements Serializable {

    private Long enrollmentId;
    private Long lessonId;
}
