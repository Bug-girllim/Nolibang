package com.wanted.lmspractice.domain.inquiry.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * [역할] 문의 화면 한 장에 필요한 값 — 왼쪽 목록, 선택한 항목, 선택한 스레드의 메시지
 * [담당] 팀원 4 · #21   [화면] U-S18, T-S06 (시안이 목록과 스레드를 한 화면에 보여 준다)
 * [연결] 만든 곳: InquiryService.getLearnerPage·getInstructorPage / 쓰는 곳: 두 컨트롤러(모델 threads·thread·messages)
 */
@AllArgsConstructor
@Getter
@ToString
public class InquiryPageDTO {

    private List<InquiryDTO> threads;
    /** 선택한 항목. 목록이 비었으면 null */
    private InquiryDTO selected;
    /** 선택한 스레드의 메시지(스레드가 아직 없으면 빈 목록) */
    private List<InquiryMessageDTO> messages;
    /** 강사 화면의 "답변 대기 N" — 필터와 관계없는 전체 대기 수. 학습자 화면은 0 */
    private long waitingCount;
}
