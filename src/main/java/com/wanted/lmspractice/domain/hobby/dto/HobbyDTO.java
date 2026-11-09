package com.wanted.lmspractice.domain.hobby.dto;

import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.entity.Hobby;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 세부 취미 한 개의 목록·카드 표시값 (이름·소개·노출 상태·그림 스타일)
 * [담당] 팀원 2 · #08   [화면] U-S01-A(대분류별 목록), U-S03(강의 목록 머리), A-S15(관리자)   [API] HB-01·CO-01·HB-03
 * [연결]
 *  - 만든 곳: HobbyService(from)  → CategoryDTO 안의 목록으로도 쓰인다
 *  - 쓰는 곳: templates/hobby/category.html, hobby/side.html, course/list.html, admin/hobbies.html
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * state는 "화면에 적용되는 노출 상태"다 — 상위 대분류가 비공개/준비 중이면 세부 취미 자신의 값보다 우선한다(05 §3, HobbyService.effectiveState).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class HobbyDTO {

    private Long id;
    private Long categoryId;
    private String code;
    private String name;
    private String description;
    private ExposureState state;
    /** 시안 그림 스타일 CSS 클래스(없으면 빈 문자열) */
    private String picClass;

    /** 관리자 화면(A-S15) 전용 집계 — 일반 화면에서는 0 그대로 둔다. CourseCatalogService.getAdminCategories가 채운다 */
    private int openCourseCount;
    private int totalCourseCount;
    private int lessonCount;

    /** Hobby 엔티티 → 표시용 DTO. effectiveState는 상위 대분류까지 반영한 노출 상태 */
    public static HobbyDTO from(Hobby hobby, ExposureState effectiveState) {
        return new HobbyDTO(
                hobby.getId(),
                hobby.getCategoryId(),
                hobby.getCode(),
                hobby.getName(),
                hobby.getDescription(),
                effectiveState,
                HobbyPicStyle.hobbyPicClass(hobby.getCode()),
                0, 0, 0);
    }

    /** 화면에 보여 줄 상태 이름 (운영 중·준비 중·비공개) */
    public String getStateLabel() {
        return state.getLabel();
    }

    /** 운영 중이어야 강의 신청이 가능하다 (준비 중은 소개만 — 05 §3) */
    public boolean isOperating() {
        return state == ExposureState.OPERATING;
    }
}
