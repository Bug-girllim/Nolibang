package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 30일 수강 기간 만료 처리 — 만료 시각이 지났고 평가할 과제가 없는 수강 건을 EXPIRED로 바꾸고 자리를 대기자에게 넘긴다
 * [담당] 팀원 2 · #10   [기획] FR-054, 05 §4-2   [API] SC-01
 * [연결]
 *  - 호출받음: EnrollmentExpiryScheduler.expire (주기 실행)
 *  - 호출함:   EnrollmentRepository·CourseSectionRepository(잠금), ReviewQueryService.hasPendingReview(C11, #14 — 그 전엔 스텁 false),
 *             SeatService.fillVacancies(C4, #09 — 같은 트랜잭션)
 *  - 이 클래스를 스케줄러와 나눈 이유: 수강 건 하나마다 트랜잭션을 따로 열어, 한 건이 실패해도 다른 건의 반환은 커밋되게 하려는 것.
 *    (스케줄러 안에서 자기 메서드를 부르면 @Transactional이 적용되지 않는다)
 * [기술] 🟢 @Service·@Transactional, 🟢 native SELECT … FOR UPDATE
 */
@Service
@RequiredArgsConstructor
public class EnrollmentExpiryService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseSectionRepository sectionRepository;
    private final ReviewQueryService reviewQueryService;
    private final SeatService seatService;

    /**
     * 만료 시각이 지난 ACTIVE 수강 건 id 목록 (아직 평가 대기 여부는 보지 않은 후보).
     * 호출: EnrollmentExpiryScheduler.expire
     */
    @Transactional(readOnly = true)
    public List<Long> findExpiredCandidateIds(LocalDateTime now) {
        return enrollmentRepository.findByStatusAndExpiresAtBefore(EnrollmentStatus.ACTIVE, now).stream()
                .map(Enrollment::getId)
                .toList();
    }

    /**
     * 수강 건 하나를 만료 처리한다 — 강의 분반 행 잠금 → 다시 확인 → EXPIRED → fillVacancies(대기 1순위 자동 확정 + 알림).
     * 호출: EnrollmentExpiryScheduler.expire (건마다 새 트랜잭션)
     *
     * @return 만료 처리했으면 true, 그대로 두었으면 false(이미 다른 상태, 아직 기간 안, 평가할 과제가 남음)
     * 트랜잭션: 신청·취소·정원 변경과 같은 "강의 분반 행 잠금"을 먼저 거친다 — 같은 강의 자리를 동시에 처리하지 않게(SC-01 동시성)
     */
    @Transactional
    public boolean expireOne(Long enrollmentId, LocalDateTime now) {
        Long courseId = enrollmentRepository.findCourseIdById(enrollmentId).orElse(null);
        if (courseId == null) {
            return false;
        }
        sectionRepository.lockByCourseId(courseId);
        // 잠근 뒤에 읽어야 그 사이 취소·수료된 상태를 본다 (잠금 전에 엔티티를 읽지 않은 이유)
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElse(null);
        if (enrollment == null || !enrollment.isActive() || !enrollment.isPastExpiry(now)) {
            return false;
        }
        if (reviewQueryService.hasPendingReview(enrollmentId)) {
            return false;  // 05 §4-2: 기간 내 제출한 과제가 평가 대기·진행 중이면 평가가 끝날 때까지 정원 점유 (반려되면 RV-07에서 반환)
        }
        enrollment.expire(now);
        enrollmentRepository.flush();  // fillVacancies의 native 조회가 반환된 자리를 보도록 먼저 반영
        seatService.fillVacancies(courseId);
        return true;
    }
}
