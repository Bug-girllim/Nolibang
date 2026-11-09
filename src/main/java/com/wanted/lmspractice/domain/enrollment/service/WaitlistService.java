package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.enrollment.dto.ApplyResultDTO;
import com.wanted.lmspractice.domain.enrollment.dto.MyWaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.dto.WaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistQuery;
import java.util.List;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강의별 단일 대기열 — 대기 등록(끝순번)·대기 취소·순번 조회. 자동 확정은 SeatService.fillVacancies 한 곳에서만 한다.
 * [담당] 팀원 2 · #09 · #10(관리자 대기열 조회 getWaitlist·getRecentConfirmed·countRecentConfirmed) · #11(getMyWaiting·getAppliedAtByEnrollmentIds)
 *       [화면] U-S05, A-S12   [기획] FR-053, D-52, D-53   [API] EN-01(대기 분기)·EN-02, WL-01
 * [연결]
 *  - 호출받음: EnrollmentService.apply(join·hasWaiting), EnrollmentService.getStatusForUser(findWaiting·getWaitingNumber),
 *             EnrollmentController.cancelWaiting(cancel), AdminWaitlistController(#10 WL-01)
 *  - 호출함:   WaitlistEntryRepository, CourseSectionRepository(취소 전 강의 잠금), WaitlistQuery(#10 native 조회)
 * [기술] 🟢 @Transactional, 🟢 native FOR UPDATE
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    /** ❓ 임시값: 최근 자동 확정 내역 표시 개수 (API WL-01 "개수 ❓", #10) */
    public static final int RECENT_CONFIRMED_LIMIT = 10;
    /** 시안 KPI "최근 7일 자동 확정"의 기간(일, #10) */
    public static final int RECENT_CONFIRMED_DAYS = 7;

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final WaitlistQuery waitlistQuery;  // #10 관리자 조회(native)

    /**
     * 대기열 끝에 등록한다. 호출한 쪽(EnrollmentService.apply)이 강의 잠금·자격 확인을 마친 뒤 부른다.
     * 호출: EnrollmentService.apply (대기자가 있거나 자리가 없을 때)
     * @return WAITING + 순번
     * @throws BusinessException ALREADY_WAITING — DB UNIQUE(course_id, waiting_user_id) 이중 방어
     */
    @Transactional
    public ApplyResultDTO join(Long userId, Long courseId) {
        WaitlistEntry entry = WaitlistEntry.create(userId, courseId);
        try {
            waitlistEntryRepository.saveAndFlush(entry);  // 바로 INSERT해야 id(=순번 기준)가 생긴다
        } catch (DataIntegrityViolationException e) {
            // 정상 흐름에서는 자격 확인이 먼저 막는다. 그래도 같은 요청이 겹치면 DB UNIQUE가 막고, 화면에는 같은 안내를 보인다
            throw new BusinessException(ErrorCode.ALREADY_WAITING);
        }
        return ApplyResultDTO.waiting(getWaitingNumber(entry));
    }

    /**
     * 이 강의에 대기 중인 사람이 있는지 — 있으면 빈자리가 있어도 신규 신청은 대기열 끝으로 간다(D-52, 빈자리 직행 금지).
     * 호출: EnrollmentService.apply
     */
    @Transactional(readOnly = true)
    public boolean hasWaiting(Long courseId) {
        return waitlistEntryRepository.existsByCourseIdAndStatus(courseId, WaitlistStatus.WAITING);
    }

    /** 학습자의 이 강의 대기 줄(WAITING). 호출: EnrollmentService.getStatusForUser */
    @Transactional(readOnly = true)
    public Optional<WaitlistEntry> findWaiting(Long userId, Long courseId) {
        return waitlistEntryRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, WaitlistStatus.WAITING);
    }

    /**
     * 대기 순번(1부터) = 같은 강의에서 이 줄보다 먼저 대기 중인 줄 수 + 1. 앞사람이 확정·취소되면 자동으로 당겨진다(FIFO = id 순).
     * 호출: join, EnrollmentService.getStatusForUser
     */
    @Transactional(readOnly = true)
    public int getWaitingNumber(WaitlistEntry entry) {
        return (int) waitlistEntryRepository.countByCourseIdAndStatusAndIdLessThanEqual(
                entry.getCourseId(), WaitlistStatus.WAITING, entry.getId());
    }

    /**
     * 본인 대기 취소 → CANCELLED. 다시 대기 신청하면 새 줄이라 마지막 순번이 된다(FR-053).
     * 호출: EnrollmentController.cancelWaiting (POST /enrollments/waitlist/{waitlistId}/cancel, API EN-02)
     * @throws BusinessException NOT_FOUND — 없는 대기, 남의 대기, 이미 확정·취소된 대기 (API EN-02: 전용 코드가 없어 NOT_FOUND 사용)
     * 트랜잭션: 강의 잠금 → 대기 줄 잠금 순서. 자동 확정(fillVacancies)과 같은 순서로 잠가 교착을 막고,
     *          방금 확정된 줄을 "취소"로 덮어쓰지 않도록 최신 값을 잠근 뒤 판단한다.
     */
    @Transactional
    public void cancel(Long userId, Long waitlistId) {
        Long courseId = waitlistEntryRepository.findCourseIdById(waitlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        courseSectionRepository.findByCourseIdForUpdate(courseId);  // ① 강의 잠금(모든 흐름 공통 첫 잠금)
        WaitlistEntry entry = waitlistEntryRepository.findByIdForUpdate(waitlistId)  // ② 최신 값
                .filter(found -> found.isOwnedBy(userId) && found.isWaiting())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        entry.cancel(LocalDateTime.now());  // 변경 감지로 커밋 때 UPDATE
    }

    /**
     * 계약 C19(#11 추가): 한 학습자가 지금 대기 중인(WAITING) 강의 목록과 각 대기 번호 — 신청 순서대로.
     * 대기 번호는 getWaitingNumber를 그대로 쓴다(같은 규칙을 두 곳에 만들지 않는다).
     * 호출: MyLearningService.getMyLearning (GET /my/learning, API ML-01)
     */
    @Transactional(readOnly = true)
    public List<MyWaitlistDTO> getMyWaiting(Long userId) {
        return waitlistEntryRepository.findByUserIdAndStatusOrderByIdAsc(userId, WaitlistStatus.WAITING).stream()
                .map(entry -> new MyWaitlistDTO(entry.getId(), entry.getCourseId(), getWaitingNumber(entry), entry.getCreatedAt()))
                .toList();
    }

    /**
     * 수강 건들 가운데 대기열을 거쳐 자동 확정된 것의 "대기 신청 시각" (#11 "자동 확정" 카드용).
     * 호출: EnrollmentService.getMyEnrollments
     *
     * @return 수강 id → 대기 신청 시각 (바로 확정된 수강은 빠진다)
     */
    @Transactional(readOnly = true)
    public Map<Long, LocalDateTime> getAppliedAtByEnrollmentIds(Collection<Long> enrollmentIds) {
        return waitlistEntryRepository.findByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.toMap(WaitlistEntry::getEnrollmentId, WaitlistEntry::getCreatedAt, (first, second) -> first));
    }

    // ───────────────────────── #10 관리자 대기열 조회 (A-S12, 조회 전용 — D-53) ─────────────────────────

    /**
     * 관리자 대기열 — 한 강의의 대기 중 학습자를 순번(FIFO)대로 (WL-01 모델 waitlist).
     * 호출: AdminWaitlistController.list
     */
    @Transactional(readOnly = true)
    public List<WaitlistDTO> getWaitlist(Long courseId) {
        return waitlistQuery.findWaiting(courseId);
    }

    /**
     * 최근 자동 확정 내역 — 확정 시각 최신순 RECENT_CONFIRMED_LIMIT건 (WL-01 모델 recentConfirmed).
     * 호출: AdminWaitlistController.list
     */
    @Transactional(readOnly = true)
    public List<WaitlistDTO> getRecentConfirmed(Long courseId) {
        return waitlistQuery.findRecentConfirmed(courseId, RECENT_CONFIRMED_LIMIT);
    }

    /** 최근 7일 자동 확정 수 (A-S12 KPI). 호출: AdminWaitlistController.list */
    @Transactional(readOnly = true)
    public int countRecentConfirmed(Long courseId) {
        return waitlistQuery.countConfirmedWithinDays(courseId, RECENT_CONFIRMED_DAYS);
    }
}
