package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 분반 생성·정원 변경(1~30, 활성 수강 미만 축소 불가)·담당 지정/변경
 * 담당: 팀원 2 · #10
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 *
 * [#14 변경 — 팀원 2 승인 필요] #10이 revert되어 C6 getCurrentInstructorId가 예외만 던지는 스텁이라
 * 평가 권한(#14)·작품 이미지 강사 열람(#16)이 동작하지 않았다. origin/issue10의 구현과 같은 내용만 먼저 채웠다
 * (#10 재merge 때 #10 쪽 코드로 덮어써도 된다).
 */
@Service
@RequiredArgsConstructor
public class SectionService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseSectionRepository sectionRepository;

    /**
     * 계약 C6: 수강 건의 현재 분반 담당 강사 id (사용: 팀원 3 평가 권한, 팀원 4 작품 열람·문의).
     * 담당이 비어 있는 분반이면 null.
     * @throws BusinessException ENROLLMENT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Long getCurrentInstructorId(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        return sectionRepository.findById(enrollment.getSectionId())
                .map(CourseSection::getInstructorId)
                .orElse(null);
    }

    /** 계약 C6: 강사의 담당 분반 목록 (사용: 팀원 1 #07) */
    public List<SectionDTO> getSectionsByInstructor(Long instructorId) {
        // TODO #10: 구현 (현재는 스텁)
        return List.of();
    }
}
