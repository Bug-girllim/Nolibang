package com.wanted.lmspractice.domain.instructor.service;

import com.wanted.lmspractice.domain.instructor.dto.InstructorHomeDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionSummaryDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentProgressDTO;
import com.wanted.lmspractice.domain.instructor.dto.StudentStatus;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 홈(담당 분반 요약)과 담당 학습자 목록을 조립한다. 다른 분반 학습자는 차단한다.
 * [담당] 팀원 1 · #07   [화면] T-S01, T-S02   [기획] 05 §7-0, NFR-04
 * [연결]
 *  - 호출받음: InstructorHomeController, InstructorStudentController
 *  - 호출함:   InstructorStatQuery (native 집계)
 * ❓ 임시 구현: 이슈는 진도·평가 상태를 ProgressService.getLessonStatuses / ReviewQueryService.getLatestStatus(계약 C10·C11)로
 *    얻으라고 했지만, 두 메서드가 아직 스텁(빈 목록·예외)이고 반환 DTO 필드도 비어 있어 InstructorStatQuery의 읽기 전용 집계로 대신했다.
 *    계약 메서드가 구현·확정되면 getStudents 안의 조회를 계약 호출로 교체한다.
 * [기술] 🟢 @Transactional(readOnly = true)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorDashboardService {

    private final InstructorStatQuery instructorStatQuery;

    /**
     * 강사 홈 데이터: 담당 분반 표 + KPI 합계 + 기한 초과 경고.
     * 호출: InstructorHomeController.home (GET /instructor, IN-01)
     * 담당 분반이 없으면 빈 목록과 0으로 채운다.
     */
    public InstructorHomeDTO getHome(Long instructorId) {
        LocalDateTime now = LocalDateTime.now();
        List<SectionStatDTO> stats = instructorStatQuery.findSectionStats(instructorId, now);
        SectionStatDTO total = SectionStatDTO.total(stats);
        List<SectionSummaryDTO> sections = new ArrayList<>();
        for (SectionStatDTO s : stats) {
            sections.add(SectionSummaryDTO.from(s));
        }
        return new InstructorHomeDTO(sections, total.getActiveCount(), total.getPendingCount(),
                total.getImminentCount(), total.getOverdueCount(),
                instructorStatQuery.findOverdueReviews(instructorId, now));
    }

    /** 담당 분반 목록(학습자 관리의 분반 선택). 호출: InstructorStudentController */
    public List<SectionSummaryDTO> getAssignedSections(Long instructorId) {
        List<SectionSummaryDTO> sections = new ArrayList<>();
        for (SectionStatDTO s : instructorStatQuery.findSectionStats(instructorId, LocalDateTime.now())) {
            sections.add(SectionSummaryDTO.from(s));
        }
        return sections;
    }

    /**
     * 담당 분반 학습자 목록 (실명 포함 — 담당 강사에게만 보인다, NFR-04).
     * 호출: InstructorStudentController.students (GET /instructor/students, IN-02)
     * @param sectionId null이면 담당 분반 전체
     * @param status    null이면 전체 상태
     * @param name      null·빈 값이면 이름 검색 안 함
     * @throws BusinessException SECTION_NOT_FOUND 내 담당이 아닌 분반 id를 URL로 넣은 경우(존재를 숨기려고 404)
     */
    public List<StudentProgressDTO> getStudents(Long instructorId, Long sectionId, StudentStatus status, String name) {
        if (sectionId != null && !isMySection(instructorId, sectionId, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        }
        return findStudents(instructorId, sectionId, status, name);
    }

    /**
     * [성능 개선] 담당 분반 학습자 목록 — 화면이 이미 읽은 담당 분반 목록(getAssignedSections)으로 "내 분반인지"를 확인한다.
     * 예전에는 학습자 관리 화면 한 번에 담당 분반 집계(findSectionStats — 분반마다 하위 조회 7개)를 두 번 실행했다
     * (isMySection 1번 + 분반 선택 목록 1번). 규칙·결과는 getStudents(instructorId, sectionId, status, name)와 같다.
     * 호출: InstructorStudentController.students
     * @throws BusinessException SECTION_NOT_FOUND 내 담당이 아닌 분반 id
     */
    public List<StudentProgressDTO> getStudents(Long instructorId, List<SectionSummaryDTO> assignedSections,
                                                Long sectionId, StudentStatus status, String name) {
        if (sectionId != null && assignedSections.stream().noneMatch(s -> s.getSectionId().equals(sectionId))) {
            throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        }
        return findStudents(instructorId, sectionId, status, name);
    }

    /** 담당 분반 학습자 조회 + 상태 필터 + 남은 기간 표시 (분반 소유 확인 뒤에 부른다) */
    private List<StudentProgressDTO> findStudents(Long instructorId, Long sectionId, StudentStatus status, String name) {
        LocalDateTime now = LocalDateTime.now();
        String keyword = (name == null || name.isBlank()) ? null : name.trim();

        List<StudentProgressDTO> result = new ArrayList<>();
        for (StudentProgressDTO s : instructorStatQuery.findStudents(instructorId, sectionId, keyword)) {
            if (status != null && s.getStatus() != status) {
                continue;
            }
            s.setRemainingText(remainingText(s, now));
            result.add(s);
        }
        return result;
    }

    private boolean isMySection(Long instructorId, Long sectionId, LocalDateTime now) {
        for (SectionStatDTO s : instructorStatQuery.findSectionStats(instructorId, now)) {
            if (s.getSectionId().equals(sectionId)) {
                return true;
            }
        }
        return false;
    }

    /** 남은 기간 표시. 학습 가능한 ACTIVE 수강만 계산하고, 끝난 수강은 "-". 기간이 지났으면 "기간 종료". */
    private String remainingText(StudentProgressDTO s, LocalDateTime now) {
        if (s.getStatus() == StudentStatus.APPROVED || s.getStatus() == StudentStatus.EXPIRED) {
            return "-";
        }
        Duration left = Duration.between(now, s.getExpiresAt());
        if (left.isNegative()) {
            return "기간 종료";   // 평가 중에는 기간이 끝나도 ACTIVE로 남는다(정원 점유 규칙)
        }
        return left.toDays() + "일 " + (left.toHours() % 24) + "시간";
    }
}
