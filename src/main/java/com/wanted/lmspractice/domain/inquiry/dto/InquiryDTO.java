package com.wanted.lmspractice.domain.inquiry.dto;

import com.wanted.lmspractice.domain.inquiry.entity.InquiryStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 1:1 문의 한 줄(강의 하나의 문의 상태) — 학습자 목록은 문의 가능한 수강마다 한 줄, 강사 목록은 담당 학습자 스레드마다 한 줄
 * [담당] 팀원 4 · #21   [화면] U-S18, T-S06   [API] IQ-01·03, II-01·02
 * [연결] 만든 곳: InquiryService / 쓰는 곳: inquiry/list.html, instructor/inquiries.html
 * 아직 스레드를 만들지 않은 수강은 threadId·status·lastMessageAt이 null이다("문의 없음").
 * 담당 강사가 없으면 vacant=true(담당 공백 D-31) — 새 문의·답장 모두 불가.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class InquiryDTO {

    private Long threadId;
    private Long enrollmentId;
    private String courseTitle;
    private String levelLabel;
    private String sectionName;
    /** 현재 담당 강사 이름(학습자 화면). 담당 공백이면 null */
    private String instructorName;
    /** 학습자 실명(강사 화면에만 채움, NFR-04) */
    private String learnerName;
    private String title;
    private InquiryStatus status;
    private LocalDateTime lastMessageAt;
    private boolean vacant;
    /** 지금 메시지를 쓸 수 있는지(수강 중·수료 + 담당 있음). 강사 화면은 답변 폼을 따로 보여 주므로 false */
    private boolean canWrite;

    public boolean isHasThread() {
        return threadId != null;
    }

    public boolean isAnswered() {
        return status == InquiryStatus.ANSWERED;
    }

    /** 목록 칩 글자 */
    public String getStateLabel() {
        if (!isHasThread()) {
            return vacant ? "담당 공백" : "문의 없음";
        }
        if (isAnswered()) {
            return "답변 완료";
        }
        return vacant ? "담당 공백" : "답변 대기";
    }
}
