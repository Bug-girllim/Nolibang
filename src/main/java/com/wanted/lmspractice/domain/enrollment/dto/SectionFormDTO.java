package com.wanted.lmspractice.domain.enrollment.dto;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 분반 생성 입력 (SE-02 요청값 courseId·name·capacity·instructorId — 이름은 API 명세 그대로)
 * [담당] 팀원 2 · #10   [화면] A-S05 신규 분반 생성 패널   [API] SE-01 모델 sectionFormDTO, SE-02
 * [연결]
 *  - 받는 곳: AdminSectionController.create (@Valid + BindingResult → 같은 화면 재표시)
 *  - 넘기는 곳: SectionService.create(courseId, dto)
 * [기술] 🟢 Bean Validation @NotNull·@Min·@Max·@Size
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SectionFormDTO {

    @NotNull(message = "강의를 선택해 주세요.")
    private Long courseId;

    /** 분반 이름 — 비우면 번호로 표시(DB H4). 강의 안에서 중복 불가는 컨트롤러·서비스가 확인 */
    @Size(max = 30, message = "분반 이름은 30자 이하로 입력해 주세요.")
    private String name;

    @NotNull(message = "정원을 입력해 주세요.")
    @Min(value = CourseSection.MIN_CAPACITY, message = "정원은 1~30명입니다.")
    @Max(value = CourseSection.MAX_CAPACITY, message = "정원은 1~30명입니다.")
    private Integer capacity;

    @NotNull(message = "담당 강사를 선택해 주세요.")
    private Long instructorId;

    /** 저장할 분반 이름 — 앞뒤 공백 제거, 비어 있으면 null (UNIQUE(course_id, name)은 NULL 여러 개를 허용) */
    public String getTrimmedName() {
        return (name == null || name.isBlank()) ? null : name.trim();
    }
}
