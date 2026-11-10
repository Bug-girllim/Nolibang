package com.wanted.lmspractice.domain.hobby.dto;

import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 관리자 대분류 추가 폼 입력값 (A-S02)
 * [담당] 팀원 2 · #08   [화면] templates/admin/hobby-manage.html (view=categories)
 * [연결]
 *  - 받는 곳: AdminHobbyController.createCategory (@Valid + BindingResult)
 *  - 쓰는 곳: HobbyService.createCategory
 * [규칙] 이름 1~100자(categories.name VARCHAR(100)), 상태는 운영 중·준비 중·비공개 중 하나. 이름 중복은 컨트롤러가 서비스로 확인한다.
 * [기술] 🟢 DTO + Bean Validation(@NotBlank·@Size·@NotNull)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CategoryFormDTO {

    @NotBlank(message = "대분류 이름을 입력해 주세요.")
    @Size(max = 100, message = "대분류 이름은 100자 이하로 입력해 주세요.")
    private String name;

    /** 새 대분류는 학습자에게 바로 보이지 않도록 기본값을 비공개로 둔다 */
    @NotNull(message = "노출 상태를 선택해 주세요.")
    private ExposureState state = ExposureState.PRIVATE;

    /** 앞뒤 공백을 뺀 이름 */
    public String getTrimmedName() {
        return name == null ? null : name.trim();
    }
}
