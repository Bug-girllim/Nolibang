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
 * [역할] 관리자 세부 취미 추가 폼 입력값 (A-S03)
 * [담당] 팀원 2 · #08   [화면] templates/admin/hobby-manage.html (view=hobbies)
 * [연결]
 *  - 받는 곳: AdminHobbyController.createHobby (@Valid + BindingResult)
 *  - 쓰는 곳: HobbyService.createHobby
 * [규칙] 대분류 필수, 이름 1~100자(hobbies.name VARCHAR(100)), 소개 1000자 이하(선택), 상태 필수.
 *        같은 대분류 안 이름 중복·없는 대분류는 컨트롤러가 서비스로 확인한다.
 * [기술] 🟢 DTO + Bean Validation(@NotBlank·@Size·@NotNull)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class HobbyFormDTO {

    @NotNull(message = "대분류를 선택해 주세요.")
    private Long categoryId;

    @NotBlank(message = "세부 취미 이름을 입력해 주세요.")
    @Size(max = 100, message = "세부 취미 이름은 100자 이하로 입력해 주세요.")
    private String name;

    @Size(max = 1000, message = "소개는 1000자 이하로 입력해 주세요.")
    private String description;

    /** 새 세부 취미는 학습자에게 바로 보이지 않도록 기본값을 비공개로 둔다 */
    @NotNull(message = "노출 상태를 선택해 주세요.")
    private ExposureState state = ExposureState.PRIVATE;

    /** 앞뒤 공백을 뺀 이름 */
    public String getTrimmedName() {
        return name == null ? null : name.trim();
    }

    /** 앞뒤 공백을 뺀 소개 (없으면 빈 문자열) */
    public String getTrimmedDescription() {
        return description == null ? "" : description.trim();
    }
}
