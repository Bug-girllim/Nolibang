package com.wanted.lmspractice.domain.hobby.dto;

import com.wanted.lmspractice.domain.hobby.entity.Category;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 대분류 한 개와 그 아래 세부 취미 목록 (이름·노출 상태·카드 이미지)
 * [담당] 팀원 2 · #08   [화면] U-S01(전체 취미), U-S01-A(대분류별), 사이드 메뉴, A-S15   [API] HB-01·HB-03 모델 categories
 * [연결]
 *  - 만든 곳: HobbyService(from)
 *  - 쓰는 곳: templates/hobby/list.html·category.html·side.html·admin/hobbies.html
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * categories 표에는 소개 문구 칼럼이 없어서 대분류 설명은 두지 않는다(시안의 설명 문장은 화면에서 뺐다).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CategoryDTO {

    private Long id;
    private String code;
    private String name;
    private ExposureState state;
    /** 카드 이미지 경로(매핑이 없으면 기본 이미지 — HobbyPicStyle.categoryImage) */
    private String imagePath;
    private List<HobbyDTO> hobbies;

    /** Category 엔티티 + 이미 노출 규칙이 적용된 세부 취미 목록 → 표시용 DTO */
    public static CategoryDTO from(Category category, List<HobbyDTO> hobbies) {
        return new CategoryDTO(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getState(),
                HobbyPicStyle.categoryImage(category.getCode()),
                hobbies);
    }

    /** 대분류별 보기 상단 배너 CSS 클래스 (시안의 bn-art·bn-baking 등) */
    public String getBannerClass() {
        return HobbyPicStyle.categoryBanner(code);
    }

    /** 화면에 보여 줄 상태 이름 */
    public String getStateLabel() {
        return state.getLabel();
    }

    /** 이 대분류에 보이는 세부 취미 수 */
    public int getHobbyCount() {
        return hobbies.size();
    }

    /** 그중 운영 중인 세부 취미 수 */
    public long getOperatingCount() {
        return hobbies.stream().filter(HobbyDTO::isOperating).count();
    }

    /**
     * D-31 대분류 상태 재설정 필요 — 운영 중인데 운영 중 세부 취미가 하나도 없다(05 §3 조건이 깨짐).
     * 시스템이 상태를 자동으로 바꾸지 않고 관리자 화면(A-S15·A-S02)에 표시만 한다. 관리자용 DTO(세부 취미 전체)에서 의미가 있다.
     */
    public boolean isStateResetNeeded() {
        return state == ExposureState.OPERATING && getOperatingCount() == 0;
    }

    /** 이 대분류 아래 공개(OPEN) 강의 수의 합 — 관리자 화면(A-S15)의 요약 숫자용. 세부 취미에 집계가 채워진 경우에만 의미가 있다 */
    public int getOpenCourseCount() {
        return hobbies.stream().mapToInt(HobbyDTO::getOpenCourseCount).sum();
    }
}
