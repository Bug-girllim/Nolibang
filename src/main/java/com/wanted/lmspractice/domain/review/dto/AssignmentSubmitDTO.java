package com.wanted.lmspractice.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 최종 과제 제출·평가 전 수정·재제출 폼의 글 입력(제목·설명). 이미지는 컨트롤러가 List&lt;MultipartFile&gt; images로 따로 받는다.
 * [담당] 팀원 3 · #13   [화면] U-S07   [API] AS-02·AS-03·AS-04 (Form title, description)
 * [연결]
 *  - 호출받음: AssignmentController(@Valid @ModelAttribute("form")), templates/review/assignment.html(th:object="${form}")
 *  - 넘기는 곳: AssignmentService → WorkService.saveFinalWorkVersion·updateFinalWorkVersion(계약 C8의 title·description)
 * [기술] 🟢 Bean Validation(@NotBlank·@Size) + @Valid + BindingResult
 * ❓ API 명세의 visibility(공개 여부)는 받지 않는다 — 계약 C8 시그니처에 공개 여부 인자가 없어 넘길 곳이 없다(DB 기본값 PRIVATE).
 *    C8에 인자가 추가되면 여기에 필드를 더한다(팀원 4와 합의 필요).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class AssignmentSubmitDTO {

    /** 작품 제목 최대 글자 수 — DB work_versions.title VARCHAR(200) */
    public static final int MAX_TITLE_LENGTH = 200;

    /** 작품 제목 — 기본값은 강의의 과제 제목(예: "색연필화 기초 — 최종 과제"), 학습자가 바꿀 수 있다 */
    @NotBlank(message = "작품 제목을 입력해 주세요.")
    @Size(max = MAX_TITLE_LENGTH, message = "작품 제목은 200자 이하로 입력해 주세요.")
    private String title;

    /** 과제 설명·회고 (선택, DB TEXT) */
    private String description;

    /** 화면 첫 표시용 — 제목 칸에 강의의 과제 제목을 미리 넣는다(AS-01 form 기본값) */
    public static AssignmentSubmitDTO withDefaultTitle(String defaultTitle) {
        return new AssignmentSubmitDTO(defaultTitle, null);
    }
}
