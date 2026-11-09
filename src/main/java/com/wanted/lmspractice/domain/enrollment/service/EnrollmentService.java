package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.ApplyResultDTO;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.policy.EnrollEligibilityPolicy;
import com.wanted.lmspractice.domain.enrollment.policy.SectionAssignPolicy;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 수강 신청(즉시 확정 or 대기)·수강 취소·수료/만료 후 반려 종료 흐름을 조립하고, 강의별 "내 신청 상태"를 만든다
 * [담당] 팀원 2 · #09   [화면] U-S05(강의 상세 신청 영역), U-S03·U-S04   [기획] FR-015·052·053·054·055, D-52~D-55
 *       [API] EN-01·EN-03, CO-02(enrollStatus)
 * [연결]
 *  - 호출받음: EnrollmentController(apply·cancel), CourseCatalogService(#08, getStatusForUser),
 *             팀원 3 ReviewService(#14, complete·closeAfterRejection — 계약 C5), 팀원 3·4(getOwnedEnrollment — 계약 C6)
 *  - 호출함:   CourseSectionRepository(강의 잠금), EnrollEligibilityPolicy(자격), SectionAssignPolicy(분반 선택),
 *             WaitlistService(대기 등록·순번), SeatService.fillVacancies(자리 반환 — 계약 C4),
 *             ReviewQueryService.hasPendingReview(계약 C11, 팀원 3 — #14 전에는 스텁 false)
 *  - 계약:     C5 complete·closeAfterRejection, C6 getOwnedEnrollment — 시그니처 변경 금지
 *  - 순환 주의: ReviewQueryService·NotificationService가 EnrollmentService·SeatService를 주입하면 서로 주입하는 순환이 된다(#14·#17 담당 공유)
 * [기술] 🟢 @Transactional(롤백으로 일관성), 🟢 native FOR UPDATE, 🟢 Entity 상태 메서드 + Policy, 🟢 DB UNIQUE 이중 방어
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseSectionRepository courseSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollEligibilityPolicy eligibilityPolicy;
    private final SectionAssignPolicy sectionAssignPolicy;
    private final WaitlistService waitlistService;
    private final SeatService seatService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 수강 신청 — 대기자가 있으면 대기열 끝, 없으면 남은 자리가 가장 큰 분반에 즉시 확정, 자리가 없으면 대기(D-52).
     * 호출: EnrollmentController.apply (POST /enrollments, API EN-01)
     * @return CONFIRMED + 분반·만료일, 또는 WAITING + 순번
     * @throws BusinessException COURSE_NOT_FOUND, HOBBY_NOT_OPEN, ALREADY_ENROLLED, ALREADY_WAITING, CANNOT_REAPPLY, NO_ASSIGNABLE_SECTION
     * 트랜잭션: 강의의 분반 행을 FOR UPDATE로 "가장 먼저" 잠근다(이슈의 ①자격 → ②잠금 순서를 바꿈).
     *          MySQL은 트랜잭션의 첫 일반 조회 시점의 사진을 계속 보므로, 자격 확인을 먼저 하면 잠금을 기다리는 동안
     *          다른 요청이 확정한 결과를 못 보고 정원을 넘길 수 있다. 잠금을 먼저 잡으면 이후 조회가 모두 최신 값을 본다.
     */
    @Transactional
    public ApplyResultDTO apply(Long userId, Long courseId) {
        List<CourseSection> sections = courseSectionRepository.findByCourseIdForUpdate(courseId);  // ① 잠금
        eligibilityPolicy.check(userId, courseId, sections);                                         // ② 자격

        // ③ D-52: 대기자가 있으면 빈자리가 있어도 대기열 끝 (빈자리 직행 금지 — 시나리오 31)
        if (waitlistService.hasWaiting(courseId)) {
            return waitlistService.join(userId, courseId);
        }
        // ④ 남은 자리 최대 분반(동률은 번호순) → 즉시 확정 / ⑤ 자리가 없으면 대기
        Optional<CourseSection> section = sectionAssignPolicy.pickSection(sections);
        if (section.isEmpty()) {
            return waitlistService.join(userId, courseId);
        }
        Enrollment enrollment = saveNewEnrollment(Enrollment.create(userId, courseId, section.get().getId(), LocalDateTime.now()));
        return ApplyResultDTO.confirmed(section.get().getDisplayName(), enrollment.getExpiresAt());
    }

    /**
     * 본인 수강 취소 — 평가 대기·진행 중이면 거부, 아니면 CANCELLED로 바꾸고 자리를 반환해 대기 1순위를 자동 확정한다(D-54).
     * 취소한 강의는 다시 신청할 수 없다(D-55) — 화면에서 확인을 받은 뒤 이 메서드가 불린다.
     * 호출: EnrollmentController.cancel (POST /enrollments/{enrollmentId}/cancel, API EN-03)
     * @throws BusinessException ENROLLMENT_NOT_FOUND(없거나 남의 수강 건), CANCEL_NOT_ALLOWED_DURING_REVIEW, INVALID_REVIEW_STATE(이미 끝난 수강 건)
     */
    @Transactional
    public void cancel(Long userId, Long enrollmentId) {
        Enrollment enrollment = lockEnrollment(enrollmentId);
        if (!enrollment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);  // 남의 수강 건은 존재를 숨긴다(API §1-6)
        }
        if (reviewQueryService.hasPendingReview(enrollmentId)) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED_DURING_REVIEW);  // U-S05 ⑨ 평가 대기·진행 중 취소 불가
        }
        enrollment.cancel(LocalDateTime.now());
        releaseSeat(enrollment);
    }

    /**
     * 계약 C5: 수료 승인 → COMPLETED, 자리 반환 → 대기 1순위 자동 확정.
     * 호출: 팀원 3 ReviewService(#14 승인) — 호출한 쪽 트랜잭션 안에서 실행된다(승인 저장과 함께 커밋·롤백).
     * @throws BusinessException ENROLLMENT_NOT_FOUND, INVALID_REVIEW_STATE(ACTIVE가 아님)
     */
    @Transactional
    public void complete(Long enrollmentId) {
        Enrollment enrollment = lockEnrollment(enrollmentId);
        enrollment.complete(LocalDateTime.now());
        releaseSeat(enrollment);
    }

    /**
     * 계약 C5: 수강 기간 만료 후 반려 확정 → EXPIRED(종료), 자리 반환 → 대기 1순위 자동 확정. 기간 안 반려는 ACTIVE 유지라 부르지 않는다.
     * 호출: 팀원 3 ReviewService(#14 반려) — 호출한 쪽 트랜잭션 안에서 실행된다.
     * @throws BusinessException ENROLLMENT_NOT_FOUND, INVALID_REVIEW_STATE(ACTIVE가 아니거나 아직 기간 안)
     */
    @Transactional
    public void closeAfterRejection(Long enrollmentId) {
        Enrollment enrollment = lockEnrollment(enrollmentId);
        enrollment.closeAfterRejection(LocalDateTime.now());
        releaseSeat(enrollment);
    }

    /**
     * 계약 C6: 본인 수강 건 조회 (본인 확인 포함).
     * 호출: 팀원 3(LessonController·AssignmentService 등), 팀원 4(문의·작품) — Entity는 서비스 안에서만 쓰고 Controller로 넘기지 않는다
     * @throws BusinessException ENROLLMENT_NOT_FOUND — 없거나 남의 수강 건(존재를 숨김)
     */
    @Transactional(readOnly = true)
    public Enrollment getOwnedEnrollment(Long userId, Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .filter(enrollment -> enrollment.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
    }

    /**
     * 계약 C17(#08 이슈 명시): 한 학습자의 강의별 신청 상태 — 학습 중/수료/재신청 불가/대기 n번, 기록이 없으면 신청 가능·대기 신청·신청 불가.
     * 호출: CourseCatalogService (강의 목록·상세·세부 취미 화면의 신청 영역)
     * 강의·취미 자체의 노출 규칙(준비 중 취미, 비회원, 강사·관리자 계정)은 호출한 쪽(#08)이 먼저 걸러서 이 메서드에는 학습자만 온다.
     */
    @Transactional(readOnly = true)
    public EnrollmentStatusDTO getStatusForUser(Long userId, Long courseId) {
        // 1) 수강 기록 — UNIQUE(user_id, course_id)라 최대 1건
        Optional<Enrollment> enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
        if (enrollment.isPresent()) {
            return statusOf(enrollment.get());
        }
        // 2) 대기 기록
        Optional<WaitlistEntry> waiting = waitlistService.findWaiting(userId, courseId);
        if (waiting.isPresent()) {
            WaitlistEntry entry = waiting.get();
            return EnrollmentStatusDTO.waiting(waitlistService.getWaitingNumber(entry), entry.getId(), entry.getCreatedAt());
        }
        // 3) 기록이 없으면 정원 현황으로 정한다
        SeatSummaryDTO seats = seatService.getSeatSummary(courseId);
        if (seats.getTotalCapacity() == 0) {
            return EnrollmentStatusDTO.of(EnrollmentStatusDTO.UNAVAILABLE);  // 담당 강사가 있는 분반이 없다 (CO-02 UNAVAILABLE)
        }
        // D-52: 대기자가 있으면 빈자리가 있어도 대기열 끝으로 간다 → 만석이거나 대기자가 있으면 대기 신청
        boolean mustWait = seats.isFull() || seats.getWaitingCount() > 0;
        return EnrollmentStatusDTO.of(mustWait ? EnrollmentStatusDTO.CAN_WAIT : EnrollmentStatusDTO.CAN_APPLY);
    }

    /** 수강 건 상태 → 신청 영역 상태. 취소·만료는 재신청 불가(D-55), 수료도 재수강 불가(U-S05 ⑪)라 신청 버튼이 없다 */
    private EnrollmentStatusDTO statusOf(Enrollment enrollment) {
        return switch (enrollment.getStatus()) {
            case ACTIVE -> EnrollmentStatusDTO.learning(
                    enrollment.getId(),
                    courseSectionRepository.findById(enrollment.getSectionId()).map(CourseSection::getDisplayName).orElse(""),
                    enrollment.getExpiresAt(),
                    reviewQueryService.hasPendingReview(enrollment.getId()));
            case COMPLETED -> EnrollmentStatusDTO.completed(enrollment.getId());
            case CANCELLED, EXPIRED -> EnrollmentStatusDTO.of(EnrollmentStatusDTO.CANNOT_REAPPLY);
        };
    }

    /**
     * 상태를 바꿀 수강 건을 "강의 잠금 → 수강 건 잠금" 순서로 최신 값으로 가져온다(cancel·complete·closeAfterRejection 공통).
     * 순서 이유: 신청·자리 채우기도 강의(분반 행) 잠금을 가장 먼저 잡는다. 모두 같은 순서로 잠가야 서로 기다리다 멈추는 교착이 없다.
     * 강의 id만 먼저 값으로 읽고(엔티티를 미리 불러 두면 잠금 뒤에도 오래된 객체가 재사용됨), 잠금 뒤 FOR UPDATE로 수강 건을 읽는다.
     */
    private Enrollment lockEnrollment(Long enrollmentId) {
        Long courseId = enrollmentRepository.findCourseIdById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        courseSectionRepository.findByCourseIdForUpdate(courseId);
        return enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
    }

    /**
     * 정원 점유를 끝낸 수강 건을 저장하고 같은 트랜잭션에서 자리를 채운다.
     * 순서 이유: fillVacancies는 DB에서 남은 자리를 세므로, 상태 변경을 먼저 flush해야 방금 비운 자리가 계산에 들어간다.
     */
    private void releaseSeat(Enrollment enrollment) {
        enrollmentRepository.saveAndFlush(enrollment);
        seatService.fillVacancies(enrollment.getCourseId());
    }

    /**
     * 즉시 확정 수강 건 저장. DB UNIQUE(user_id, course_id)가 중복 확정을 한 번 더 막는다(이중 방어, API EN-01 💡).
     * 정상 흐름에서는 강의 잠금 + 자격 확인이 먼저 막으므로 여기까지 오지 않는다.
     */
    private Enrollment saveNewEnrollment(Enrollment enrollment) {
        try {
            return enrollmentRepository.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }
    }
}
