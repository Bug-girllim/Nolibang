package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수강 신청(즉시 확정 or 대기)·수강 취소·수료/종료 처리
 * 담당: 팀원 2 · #09 (#11에서 본인 수강 목록 조회 getMyEnrollments 추가)
 * (💡 제안 구조 — 신청·취소·수료 상세 구현은 #09에서)
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final SeatService seatService;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;

    /** 계약 C5: 수료 승인 시 호출 (호출: 팀원 3 ReviewService) — 내부에서 SeatService.fillVacancies() */
    public void complete(Long enrollmentId) {
        // TODO #09: 구현 (현재는 스텁)
    }

    /** 계약 C5: 수강기간 만료 후 반려 확정 시 호출 (호출: 팀원 3 ReviewService) */
    public void closeAfterRejection(Long enrollmentId) {
        // TODO #09: 구현 (현재는 스텁)
    }

    /** 계약 C6: 본인 수강 건 조회 (본인 확인 포함, 사용: 팀원 3·4) */
    public Enrollment getOwnedEnrollment(Long userId, Long enrollmentId) {
        throw new UnsupportedOperationException("TODO #09");
    }

    /**
     * 계약 C17(#08 이슈 명시): 한 학습자의 강의별 신청 상태 — 신청 가능/대기 n번/학습 중/수료/재신청 불가.
     * 호출: CourseCatalogService (강의 목록·상세·세부 취미 화면의 신청 영역)
     * 강의·취미 자체의 노출 규칙(준비 중 취미, 강사·관리자 계정)은 호출한 쪽(#08)이 먼저 걸러서 이 메서드에는 학습자만 온다.
     */
    public EnrollmentStatusDTO getStatusForUser(Long userId, Long courseId) {
        // TODO #09: 구현 — 이 학습자의 수강(ACTIVE·COMPLETED·CANCELLED·EXPIRED)·대기(WAITING) 기록을 먼저 보고
        //          LEARNING / COMPLETED / CANNOT_REAPPLY(D-55) / WAITING(+순번)을 돌려준 뒤, 기록이 없을 때만 아래 정원 판단을 한다.
        // 현재는 스텁: 개인 기록은 보지 않고 정원 현황만으로 신청 가능 여부를 정한다(#09 전까지 화면이 자연스럽게 보이도록).
        SeatSummaryDTO seats = seatService.getSeatSummary(courseId);
        if (seats.getTotalCapacity() == 0) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.UNAVAILABLE);  // 담당 강사가 있는 분반이 없다 (CO-02 UNAVAILABLE)
        }
        // D-52: 대기자가 있으면 빈자리가 있어도 대기열 끝으로 간다 → 만석이거나 대기자가 있으면 대기 신청
        boolean mustWait = seats.isFull() || seats.getWaitingCount() > 0;
        return EnrollmentStatusDTO.of(mustWait ? EnrollmentStatusDTO.CAN_WAIT : EnrollmentStatusDTO.CAN_APPLY);
    }

    /**
     * 계약 C18(#11 추가): 한 학습자의 모든 수강 건(진행·수료·취소·만료)을 화면용으로 읽는다 — 최근 확정 순.
     * 분반 이름과, 대기열을 거쳐 자동 확정된 수강이면 그 대기 신청 시각을 함께 붙인다.
     * 호출: MyLearningService.getMyLearning (GET /my/learning, API ML-01)
     * 본인 것만: userId로만 조회하므로 다른 학습자의 수강은 섞이지 않는다.
     */
    @Transactional(readOnly = true)
    public List<MyEnrollmentDTO> getMyEnrollments(Long userId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserIdOrderByConfirmedAtDesc(userId);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        Map<Long, String> sectionNames = courseSectionRepository
                .findAllById(enrollments.stream().map(Enrollment::getSectionId).distinct().toList()).stream()
                .collect(Collectors.toMap(CourseSection::getId, section -> sectionNameOf(section)));
        // 대기열에서 자동 확정된 수강: WaitlistEntry.enrollmentId가 이 수강 건을 가리킨다
        Map<Long, LocalDateTime> waitlistAppliedAt = waitlistEntryRepository
                .findByEnrollmentIdIn(enrollments.stream().map(Enrollment::getId).toList()).stream()
                .collect(Collectors.toMap(WaitlistEntry::getEnrollmentId, WaitlistEntry::getCreatedAt, (a, b) -> a));

        return enrollments.stream()
                .map(enrollment -> MyEnrollmentDTO.from(
                        enrollment,
                        sectionNames.get(enrollment.getSectionId()),
                        waitlistAppliedAt.get(enrollment.getId())))
                .toList();
    }

    /** 분반 이름이 있으면 그대로, 없으면 "N분반"으로 보여 준다 */
    private static String sectionNameOf(CourseSection section) {
        return section.getName() != null ? section.getName() : section.getSectionNo() + "분반";
    }
}
