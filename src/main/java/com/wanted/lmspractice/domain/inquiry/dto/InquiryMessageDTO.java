package com.wanted.lmspractice.domain.inquiry.dto;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryMessage;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 문의 메시지 한 개 표시 — 작성자 구분(내가 쓴 글 여부·표시 이름), 내용, 시각
 * [담당] 팀원 4 · #21   [화면] U-S18, T-S06   [API] IQ-03, II-02
 * [연결] 만든 곳: InquiryService / 쓰는 곳: inquiry/list.html, instructor/inquiries.html (bubble, mine이면 bubble me)
 * [기술] 🟢 DTO 정적 팩토리
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class InquiryMessageDTO {

    private Long id;
    /** 보는 사람이 쓴 글이면 true (오른쪽 말풍선) */
    private boolean mine;
    /** "나" / "담당 전문 강사 강사 A" / "수강생 김학습" / "강사 강사 A" */
    private String authorLabel;
    private String body;
    private LocalDateTime createdAt;

    public static InquiryMessageDTO of(InquiryMessage message, boolean mine, String authorLabel) {
        return new InquiryMessageDTO(message.getId(), mine, authorLabel, message.getBody(), message.getCreatedAt());
    }
}
