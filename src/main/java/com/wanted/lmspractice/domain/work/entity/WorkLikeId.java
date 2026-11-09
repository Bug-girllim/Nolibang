package com.wanted.lmspractice.domain.work.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WorkLike의 복합 기본키 (work_id, user_id) — @IdClass 용
 * 담당: 팀원 4 · #18
 * 필드 이름·타입은 WorkLike의 @Id 필드와 반드시 같아야 한다.
 * 🟡 미학습 기술(복합키 매핑) — 11-06 팀 결정으로 사용
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class WorkLikeId implements Serializable {

    private Long workId;
    private Long userId;
}
