package com.wanted.lmspractice.domain.hobby.dto;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.entity.Hobby;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 세부 취미 상세 한 장 — 소개·상태·대표 이미지, 수준(입문·중급·고급)별 강의 카드
 * [담당] 팀원 2 · #08   [화면] U-S02   [API] HB-02 모델 hobby
 * [연결]
 *  - 만든 곳: HobbyService.getHobby(기본 정보) → CourseCatalogService.getHobbyDetail(levels 채움)
 *  - 쓰는 곳: templates/hobby/detail.html
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * levels는 수준 순서(입문→고급)이며, 각 CourseDTO에 내 신청 상태가 들어 있다. 강의가 없는 준비 중 취미는 빈 목록이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class HobbyDetailDTO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String code;
    private String name;
    private String description;
    private ExposureState state;
    private String picClass;
    /** 대표 이미지 경로(없으면 null) */
    private String heroImage;
    private List<CourseDTO> levels = List.of();

    /** Hobby 엔티티 → 상세 DTO(levels는 비어 있고 CourseCatalogService가 채운다) */
    public static HobbyDetailDTO from(Hobby hobby, ExposureState effectiveState, String categoryName) {
        return new HobbyDetailDTO(
                hobby.getId(),
                hobby.getCategoryId(),
                categoryName,
                hobby.getCode(),
                hobby.getName(),
                hobby.getDescription(),
                effectiveState,
                HobbyPicStyle.hobbyPicClass(hobby.getCode()),
                HobbyPicStyle.heroImage(hobby.getCode()),
                List.of());
    }

    public String getStateLabel() {
        return state.getLabel();
    }

    /** 운영 중이어야 강의 신청이 가능하다 */
    public boolean isOperating() {
        return state == ExposureState.OPERATING;
    }
}
