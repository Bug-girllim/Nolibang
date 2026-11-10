package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 관리자 강의 편집 폼 입력값 (A-S06 콘텐츠 검수)
 * [담당] 팀원 2 · #08   [화면] templates/admin/hobby-manage.html (view=review)
 * [연결]
 *  - 받는 곳: AdminHobbyController.updateCourse (@Valid + BindingResult)
 *  - 쓰는 곳: CourseService.updateCourse
 * [규칙] 강의명·과제 제목 필수 200자 이하(courses VARCHAR(200)), 소개·준비물·과제 안내는 선택(빈 값 허용),
 *        준비물은 한 줄에 하나(강의 상세에서 줄 단위 목록), 공개 상태 OPEN(공개)·CLOSED(비공개 — 관리자만 봄)
 * [기술] 🟢 DTO + Bean Validation(@NotBlank·@Size·@NotNull)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class CourseFormDTO {

    @NotBlank(message = "강의명을 입력해 주세요.")
    @Size(max = 200, message = "강의명은 200자 이하로 입력해 주세요.")
    private String title;

    @Size(max = 5000, message = "강의 소개는 5000자 이하로 입력해 주세요.")
    private String description;

    @Size(max = 2000, message = "준비물은 2000자 이하로 입력해 주세요.")
    private String suppliesText;

    @NotBlank(message = "최종 과제 제목을 입력해 주세요.")
    @Size(max = 200, message = "최종 과제 제목은 200자 이하로 입력해 주세요.")
    private String assignmentTitle;

    @Size(max = 5000, message = "과제 안내는 5000자 이하로 입력해 주세요.")
    private String assignmentDescription;

    @NotNull(message = "공개 상태를 선택해 주세요.")
    private CourseStatus status;

    /** 앞뒤 공백을 뺀 값 (없으면 빈 문자열) — DB 칸이 NOT NULL 이라 null 대신 빈 문자열로 저장한다 */
    public static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
