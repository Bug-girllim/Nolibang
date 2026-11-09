package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 관리자 분반 생성·정원 변경(1~30, 활성 수강 미만 축소 불가)·담당 지정/변경
 * 담당: 팀원 2 · #10
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class SectionService {

    /** 계약 C6: 수강 건의 현재 분반 담당 강사 id (사용: 팀원 3 평가 권한, 팀원 4 작품 열람·문의) */
    public Long getCurrentInstructorId(Long enrollmentId) {
        throw new UnsupportedOperationException("TODO #10");
    }

    /** 계약 C6: 강사의 담당 분반 목록 (사용: 팀원 1 #07) */
    public List<SectionDTO> getSectionsByInstructor(Long instructorId) {
        // TODO #10: 구현 (현재는 스텁)
        return List.of();
    }
}
