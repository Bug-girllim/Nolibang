package com.wanted.lmspractice.domain.review.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 배지 한 개 (취미·수준·마스터 여부) — 계약 C12 반환값
 * [담당] 팀원 3 · #15   [기획] FR-024·025
 * [연결]
 *  - 만든 곳: BadgeService.getBadges
 *  - 쓰는 곳: ReviewResultService → review/result.html, 팀원 4 CommentPolicy(#18)·WorkLevelPolicy(#19)
 * 표현 규칙(❓ 임시, #01 확정 필요): 수준 배지는 level에 값이 있고 master=false,
 * 마스터 배지는 취미별 1개이며 level=null, master=true.
 * [기술] 🟢 DTO 정적 팩토리
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class BadgeDTO {

    private Long hobbyId;
    private String hobbyName;
    /** 수준 배지의 수준. 마스터 배지는 null */
    private CourseLevel level;
    private boolean master;

    /** 취미·수준 배지 (그 수준의 강의를 수료하면 얻는다) */
    public static BadgeDTO levelBadge(Long hobbyId, String hobbyName, CourseLevel level) {
        return new BadgeDTO(hobbyId, hobbyName, level, false);
    }

    /** 마스터 배지 (한 취미의 입문·중급·고급을 모두 수료하면 얻는다) */
    public static BadgeDTO masterBadge(Long hobbyId, String hobbyName) {
        return new BadgeDTO(hobbyId, hobbyName, null, true);
    }

    /** 화면 표시 이름: "색연필화 중급" / "색연필화 마스터" */
    public String getLabel() {
        return hobbyName + " " + (master ? "마스터" : level.getLabel());
    }
}
