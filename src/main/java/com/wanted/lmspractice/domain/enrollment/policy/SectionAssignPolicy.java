package com.wanted.lmspractice.domain.enrollment.policy;

import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 분반 자동 배정 규칙 — 담당 강사가 있는 분반 중 남은 자리(정원 − ACTIVE 수)가 가장 큰 분반, 동률이면 분반 번호가 작은 쪽 (D-52)
 * [담당] 팀원 2 · #09   [기획] FR-052, D-52   [API] EN-01 처리 4, schema.sql v_section_seats 주석(ORDER BY remaining DESC, section_no ASC)
 * [연결]
 *  - 호출받음: EnrollmentService.apply(즉시 확정), SeatService.fillVacancies(대기 1순위 자동 확정) — 두 흐름이 같은 규칙을 쓴다
 *  - 호출함:   EnrollmentRepository.countActiveBySectionIdForUpdate (분반별 정원 점유 수, 잠금 읽기)
 * [기술] 🟢 Policy(@Component) — 판단만 하고 저장·상태 변경은 하지 않는다
 */
@Component
@RequiredArgsConstructor
public class SectionAssignPolicy {

    private final EnrollmentRepository enrollmentRepository;

    /**
     * 배정할 분반을 고른다. 남은 자리가 있는 배정 가능 분반이 없으면 빈 값 — 호출한 쪽이 대기로 보내거나 자리 채우기를 멈춘다.
     * 호출: EnrollmentService.apply, SeatService.fillVacancies
     * @param lockedSections 한 강의의 분반 목록 — 호출한 쪽이 이미 FOR UPDATE로 잠근 것이어야 남은 자리 계산이 그대로 유지된다
     */
    public Optional<CourseSection> pickSection(List<CourseSection> lockedSections) {
        CourseSection best = null;
        long bestRemaining = 0;  // 0보다 커야 후보가 된다(남은 자리 없음 = 배정 불가)
        List<CourseSection> byNumber = lockedSections.stream()
                .filter(CourseSection::isAssignable)  // D-52: 담당 강사가 없는 분반은 제외
                .sorted(Comparator.comparing(CourseSection::getSectionNo))
                .toList();
        for (CourseSection section : byNumber) {
            long remaining = section.getCapacity() - enrollmentRepository.countActiveBySectionIdForUpdate(section.getId());
            // 더 클 때만 바꾼다 → 동률이면 먼저 본(번호가 작은) 분반이 남는다(D-52 동률 규칙)
            if (remaining > bestRemaining) {
                best = section;
                bestRemaining = remaining;
            }
        }
        return Optional.ofNullable(best);
    }
}
