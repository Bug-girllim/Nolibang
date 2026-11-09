package com.wanted.lmspractice.domain.work.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 일반 작품 등록·수정 폼 입력 (이미지는 컨트롤러가 List&lt;MultipartFile&gt; images 로 따로 받는다)
 * [담당] 팀원 4 · #19   [화면] U-S11 — templates/work/form.html   [API] MW-01~MW-04
 * [연결]
 *  - 만든 곳: MyWorkController(폼 바인딩 th:object="${workFormDTO}"), WorkService.getGeneralWorkForm(수정 화면의 기존 값)
 *  - 쓰는 곳: WorkService.saveGeneralWork / updateGeneralWork
 * 필드 이름은 API 명세 MW-02 요청값 이름 그대로다(title, hobbyId, level, description, visibility, referenceReviewId, reflection).
 * 형식 검사(빈 제목·길이)는 여기서, 업무 규칙(운영 중 취미·배지 수준·참고 피드백 본인 여부)은 WorkService·WorkLevelPolicy 에서 한다.
 * [기술] 🟢 Bean Validation(@NotBlank·@Size, @NotNull 은 같은 묶음의 기본 제약), 🟢 DTO 정적 팩토리
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class WorkFormDTO {

    @NotBlank(message = "작품 제목을 입력해 주세요.")   // D-18: 제목은 직접 입력(필수)
    @Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")   // work_versions.title VARCHAR(200)
    private String title;

    @NotNull(message = "취미를 선택해 주세요.")
    private Long hobbyId;

    @NotNull(message = "수준 태그를 선택해 주세요.")
    private CourseLevel level = CourseLevel.BASIC;   // MW-01: 기본값 입문

    private String description;

    private Visibility visibility = Visibility.PRIVATE;   // MW-01: 기본값 비공개

    /** "이 피드백을 참고하여 작성함"을 체크했을 때만 값이 온다 */
    private Long referenceReviewId;

    @Size(max = Work.MAX_REFLECTION_LENGTH, message = "회고는 500자 이하로 입력해 주세요.")
    private String reflection;

    /** 수정 화면(MW-03)의 기존 값 — 작품 + 지금 보여 줄 버전(제목·설명) */
    public static WorkFormDTO from(Work work, WorkVersion version) {
        WorkFormDTO form = new WorkFormDTO();
        form.title = version.getTitle();
        form.description = version.getDescription();
        form.hobbyId = work.getHobbyId();
        form.level = work.getLevel();
        form.visibility = work.getVisibility();
        form.referenceReviewId = work.getReferenceReviewId();
        form.reflection = work.getReflection();
        return form;
    }
}
