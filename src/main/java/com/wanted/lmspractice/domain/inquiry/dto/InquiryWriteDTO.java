package com.wanted.lmspractice.domain.inquiry.dto;

import com.wanted.lmspractice.domain.inquiry.service.InquiryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 문의 작성·이어쓰기·답변 폼 입력 — enrollmentId·title은 새 스레드를 만들 때만 쓴다
 * [담당] 팀원 4 · #21   [화면] U-S18, T-S06   [API] IQ-02(enrollmentId·title·content), IQ-04·II-03(content)
 * [연결] 쓰는 곳: InquiryController.write·addMessage, InstructorInquiryController.answer
 * ❓ 임시값: 글자 수 상한(기획 [미정]) — InquiryService.MAX_CONTENT_LENGTH 한 곳에서 정한다(#01 확정 필요)
 * [기술] 🟢 Bean Validation
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class InquiryWriteDTO {

    private Long enrollmentId;

    @Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")
    private String title;

    @NotBlank(message = "문의 내용을 입력해 주세요.")
    @Size(max = InquiryService.MAX_CONTENT_LENGTH, message = "내용이 너무 길어요.")
    private String content;
}
