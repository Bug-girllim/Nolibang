package com.wanted.lmspractice.domain.instructor.service;

import com.wanted.lmspractice.domain.instructor.dto.InstructorStatSummaryDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery.InstructorRow;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 통계(현재 숫자만, D-58) 조회 — 강사 본인·관리자만. A-S13(관리자 강사 통계)에서도 재사용한다.
 * [담당] 팀원 1 · #07   [화면] T-S05, A-S13   [기획] FR-051, D-58
 * [연결]
 *  - 호출받음: InstructorStatController(T-S05), AdminStatController(A-S13)
 *  - 호출함:   InstructorStatQuery (native 집계)
 * [기술] 🟢 @Transactional(readOnly = true), 🟢 생성자 주입
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorStatService {

    private final InstructorStatQuery instructorStatQuery;

    /**
     * 강사의 분반별 숫자 (이슈 #07 입출력 정의). 권한 확인은 하지 않는다 — 권한이 필요한 호출은 3개 인자 버전을 쓴다.
     * 호출: InstructorDashboardService 등 서버 안쪽 조합
     */
    public List<SectionStatDTO> getSectionStats(Long instructorId) {
        return instructorStatQuery.findSectionStats(instructorId, LocalDateTime.now());   // 시각은 LocalDateTime.now()(CONVENTIONS §9)
    }

    /**
     * 권한 확인 후 분반별 숫자. 관리자는 모든 강사, 강사는 본인만.
     * 호출: InstructorStatController(본인), AdminStatController(선택한 강사)
     * @throws BusinessException ACCESS_DENIED 다른 강사의 통계를 요청한 경우(학습자는 SecurityConfig가 403으로 먼저 막는다)
     */
    public List<SectionStatDTO> getSectionStats(Long requesterId, Role requesterRole, Long instructorId) {
        if (requesterRole != Role.ADMIN && !requesterId.equals(instructorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return getSectionStats(instructorId);
    }

    /** 분반 숫자들의 합계(시안의 "합계" 행·KPI). */
    public SectionStatDTO summarize(List<SectionStatDTO> sections) {
        return SectionStatDTO.total(sections);
    }

    /**
     * 관리자 강사 통계: 강사별 합계. instructorId가 null이면 전체 강사.
     * 호출: AdminStatController (A-S13)
     */
    public List<InstructorStatSummaryDTO> getInstructorStats(Long instructorId) {
        List<InstructorStatSummaryDTO> result = new ArrayList<>();
        for (InstructorRow instructor : instructorStatQuery.findInstructors()) {
            if (instructorId != null && !instructorId.equals(instructor.getId())) {
                continue;
            }
            List<SectionStatDTO> sections = getSectionStats(instructor.getId());
            SectionStatDTO total = SectionStatDTO.total(sections);
            result.add(new InstructorStatSummaryDTO(instructor.getId(), instructor.getName(),
                    accountLabel(instructor), sections.size(),
                    total.getLearningCount(), total.getCompletedCount(), total.getRejectedCount(), total.getPendingCount()));
        }
        return result;
    }

    /** 강사별 줄들의 합계 KPI(A-S13 상단 4칸). 4개 숫자만 채운다. */
    public SectionStatDTO summarizeInstructors(List<InstructorStatSummaryDTO> rows) {
        SectionStatDTO total = new SectionStatDTO();
        for (InstructorStatSummaryDTO r : rows) {
            total.setLearningCount(total.getLearningCount() + r.getLearningCount());
            total.setCompletedCount(total.getCompletedCount() + r.getCompletedCount());
            total.setRejectedCount(total.getRejectedCount() + r.getRejectedCount());
            total.setPendingCount(total.getPendingCount() + r.getPendingCount());
        }
        return total;
    }

    /** 관리자 강사 선택 목록용(전체 강사). */
    public List<InstructorStatSummaryDTO> getAllInstructors() {
        return getInstructorStats(null);
    }

    private String accountLabel(InstructorRow instructor) {
        if ("INACTIVE".equals(instructor.getStatus())) {   // users.status CHECK 값
            return "비활성";
        }
        return instructor.isFirstLoginDone() ? "활성" : "첫 로그인 전";
    }
}
