package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.RoundContext;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 평가 흐름을 조립한다 — 목록·상세 조회, 평가 시작(학습자 수정 잠금)·시작 취소·초안 저장,
 *        승인(→ 수료·자리 반환·알림)·반려(→ 알림, 만료 후면 수강 종료·자리 반환)
 * [담당] 팀원 3 · #14   [화면] T-S03·T-S04   [기획] FR-023·030·031, D-56, D-59, D-37   [API] RV-01~RV-07
 * [연결]
 *  - 호출받음: InstructorReviewController
 *  - 호출함:   ReviewRoundRepository(FOR UPDATE 잠금·이력), ReviewQuery(현재 담당 강사·화면 값),
 *             EnrollmentService.complete·closeAfterRejection(계약 C5, 팀원 2 — 내부에서 fillVacancies),
 *             NotificationService.notify(계약 C7, 팀원 4 — REVIEW_APPROVED·COURSE_COMPLETED·REVIEW_REJECTED)
 *  - 계약:     C5·C7은 모두 이 클래스의 트랜잭션 안에서 실행된다(별도 트랜잭션이면 "수료는 됐는데 자리 반환 안 됨" 불일치)
 *  - 순환 주의: EnrollmentService가 ReviewQueryService를 주입하므로, 수강 쪽 호출은 이 클래스에만 두고 ReviewQueryService에는 두지 않는다
 * [잠금 순서] 평가 회차 행(FOR UPDATE) → (complete·closeAfterRejection 안에서) 강의 분반 행 → 수강 건 행.
 *            회차 행을 먼저 잠가 같은 회차의 동시 승인·반려를 한 줄로 세운다. #13(제출·수정)도 회차 행을 잠근다면 같은 순서를 지킨다.
 * [기술] 🟢 @Transactional + 변경 감지, 🟢 native FOR UPDATE, 🟢 Entity 상태 메서드, 🟢 DB UNIQUE(uq_reviews_approved)·CHECK 이중 방어
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewQuery reviewQuery;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------
    // 조회 (RV-01·RV-02)
    // ---------------------------------------------------------------

    /**
     * 강사 평가 목록 — 지금 본인이 담당하는 분반의 평가만, 탭·분반·기한 필터 적용. 탭 건수는 분반·기한 필터를 적용한 값이다.
     * 호출: InstructorReviewController.list (GET /instructor/reviews, API RV-01)
     * @param due null(전체) / ReviewListPageDTO.DUE_SOON / DUE_OVERDUE
     */
    @Transactional(readOnly = true)
    public ReviewListPageDTO getReviewPage(Long instructorId, ReviewTab tab, Long sectionId, String due) {
        LocalDateTime now = now();
        Map<Long, RoundContext> contexts = reviewQuery.findContextsByInstructor(instructorId).stream()
                .collect(Collectors.toMap(RoundContext::getReviewRoundId, Function.identity()));

        List<ReviewListDTO> filtered = reviewRoundRepository.findAllById(contexts.keySet()).stream()
                .map(round -> ReviewListDTO.from(round, contexts.get(round.getId()), now))
                .filter(row -> sectionId == null || sectionId.equals(row.getSectionId()))
                .filter(row -> matchesDue(row, due))
                .toList();

        List<ReviewListDTO> reviews = filtered.stream()
                .filter(row -> tab.includes(row.getStatus()))
                .sorted(orderOf(tab))
                .toList();

        return new ReviewListPageDTO(tab, sectionId, due, reviews,
                countOf(filtered, ReviewTab.WAITING), countOf(filtered, ReviewTab.IN_REVIEW), countOf(filtered, ReviewTab.DONE),
                reviewQuery.findSectionOptions(instructorId));
    }

    /**
     * 평가 상세 — 현재 분반 담당자만. 단순 열람은 잠금이 아니다(상태를 바꾸지 않음).
     * 호출: InstructorReviewController.detail (GET /instructor/reviews/{id}, API RV-02)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR
     */
    @Transactional(readOnly = true)
    public InstructorReviewDetailDTO getDetail(Long instructorId, Long reviewRoundId) {
        ReviewRound round = reviewRoundRepository.findById(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        RoundContext context = requireAssigned(instructorId, reviewRoundId);
        return InstructorReviewDetailDTO.from(round, context,
                reviewQuery.findSubmission(round.getWorkVersionId()),
                reviewRoundRepository.findByEnrollmentIdOrderByRoundNoAsc(round.getEnrollmentId()),
                now());
    }

    // ---------------------------------------------------------------
    // 상태 변경 (RV-03~RV-07) — 모두 "회차 잠금 → 담당 확인 → 엔티티 메서드" 순서
    // ---------------------------------------------------------------

    /**
     * 평가 시작 — WAITING → IN_REVIEW, 이 순간부터 학습자 과제 수정이 잠긴다.
     * 호출: InstructorReviewController.start (POST /instructor/reviews/{id}/start, API RV-03)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE
     */
    @Transactional
    public void start(Long instructorId, Long reviewRoundId) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.start(instructorId, now());
    }

    /**
     * 평가 시작 취소 — 초안이 비어 있을 때만 IN_REVIEW → WAITING, 마감은 그대로(D-56).
     * 호출: InstructorReviewController.cancelStart (POST /instructor/reviews/{id}/cancel-start, API RV-04)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, CANNOT_CANCEL_START
     */
    @Transactional
    public void cancelStart(Long instructorId, Long reviewRoundId) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.cancelStart();
    }

    /**
     * 피드백 초안 저장 — IN_REVIEW일 때만. 담당이 바뀌면 새 담당이 같은 초안을 이어서 쓴다.
     * 호출: InstructorReviewController.saveDraft (POST /instructor/reviews/{id}/draft, API RV-05)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE
     */
    @Transactional
    public void saveDraft(Long instructorId, Long reviewRoundId, String draft) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.saveDraft(instructorId, draft, now());
    }

    /**
     * 수료 승인 — 한 트랜잭션 안에서 ① 회차 APPROVED ② 수강 COMPLETED·자리 반환·대기 1순위 확정 ③ 학습자 알림 2건.
     * 어느 단계든 실패하면 전부 롤백된다(승인만 되고 수료·자리 반환이 빠지는 일이 없다).
     * 호출: InstructorReviewController.approve (POST /instructor/reviews/{id}/approve, API RV-06)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE(이미 확정 포함), FEEDBACK_TOO_SHORT
     */
    @Transactional
    public void approve(Long instructorId, Long reviewRoundId, ReviewDecisionDTO dto) {
        ReviewRound round = lockRound(reviewRoundId);
        RoundContext context = requireAssigned(instructorId, reviewRoundId);

        round.approve(instructorId, dto.getContent(), now());
        // 수료 처리 전에 반영해 DB CHECK·UNIQUE(uq_reviews_approved, 수강 건당 승인 1회)를 먼저 확인한다 — 위반이면 여기서 전체 롤백
        reviewRoundRepository.saveAndFlush(round);
        enrollmentService.complete(round.getEnrollmentId());  // 계약 C5 — 같은 트랜잭션, 내부에서 fillVacancies

        notificationService.notify(context.getLearnerId(), NotificationKind.REVIEW_APPROVED, round.getId(),
                "평가 승인", context.getCourseTitle() + " 최종 과제가 승인되었습니다. 공식 피드백을 확인해 보세요.");
        notificationService.notify(context.getLearnerId(), NotificationKind.COURSE_COMPLETED, round.getEnrollmentId(),
                "수료", context.getCourseTitle() + " 강의를 수료했습니다.");
    }

    /**
     * 반려 — ① 회차 REJECTED ② 학습자 알림 ③ 수강 기간이 이미 지났으면 수강 종료(EXPIRED)·자리 반환.
     * 기간 안 반려는 정식 재제출(새 7일)이 가능하도록 수강을 ACTIVE로 둔다(05 §4-2).
     * 호출: InstructorReviewController.reject (POST /instructor/reviews/{id}/reject, API RV-07)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT
     */
    @Transactional
    public void reject(Long instructorId, Long reviewRoundId, ReviewDecisionDTO dto) {
        ReviewRound round = lockRound(reviewRoundId);
        RoundContext context = requireAssigned(instructorId, reviewRoundId);
        LocalDateTime now = now();

        round.reject(instructorId, dto.getContent(), now);
        reviewRoundRepository.saveAndFlush(round);

        notificationService.notify(context.getLearnerId(), NotificationKind.REVIEW_REJECTED, round.getId(),
                "평가 반려", context.getCourseTitle() + " 최종 과제가 반려되었습니다. 반려 사유를 확인해 주세요.");
        // 만료 판단 경계는 Enrollment.isWithinPeriod와 같다(만료 시각 정각까지는 기간 안) — 바뀌면 함께 고친다
        if (now.isAfter(context.getExpiresAt())) {
            enrollmentService.closeAfterRejection(round.getEnrollmentId());  // 계약 C5 — 같은 트랜잭션, 내부에서 fillVacancies
        }
    }

    // ---------------------------------------------------------------
    // 내부 규칙
    // ---------------------------------------------------------------

    /** 회차 잠금 + 담당 확인 (시작·시작 취소·초안 공통) */
    private ReviewRound lockAssignedRound(Long instructorId, Long reviewRoundId) {
        ReviewRound round = lockRound(reviewRoundId);
        requireAssigned(instructorId, reviewRoundId);
        return round;
    }

    /**
     * 회차 행을 FOR UPDATE로 잠그고 최신 값으로 읽는다. 트랜잭션에서 회차를 처음 읽는 곳이어야 한다
     * (먼저 일반 조회로 불러 두면 잠근 뒤에도 영속성 컨텍스트의 오래된 객체가 재사용된다).
     */
    private ReviewRound lockRound(Long reviewRoundId) {
        return reviewRoundRepository.findByIdForUpdate(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    /**
     * 지금 이 분반의 담당 강사인지 확인한다(FR-023). 담당이 바뀌면 이전 강사는 즉시 불가, 새 강사가 이어받는다(D-59).
     * 관리자는 /instructor/** 에 들어올 수 없어 평가하지 않는다(SecurityConfig).
     * @throws BusinessException REVIEW_NOT_FOUND(회차 없음), NOT_ASSIGNED_INSTRUCTOR(담당 아님 — API ErrorCode 표대로 403)
     */
    private RoundContext requireAssigned(Long instructorId, Long reviewRoundId) {
        RoundContext context = reviewQuery.findContext(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (!context.isAssignedTo(instructorId)) {
            throw new BusinessException(ErrorCode.NOT_ASSIGNED_INSTRUCTOR);
        }
        return context;
    }

    /** 기한 필터 — 전체 / 마감 임박 / 기한 초과 */
    private boolean matchesDue(ReviewListDTO row, String due) {
        if (ReviewListPageDTO.DUE_SOON.equals(due)) {
            return row.isDueSoon();
        }
        if (ReviewListPageDTO.DUE_OVERDUE.equals(due)) {
            return row.isOverdue();
        }
        return true;
    }

    /** 대기·진행 중은 마감이 가까운 순, 완료는 최근 확정 순 */
    private Comparator<ReviewListDTO> orderOf(ReviewTab tab) {
        if (tab == ReviewTab.DONE) {
            return Comparator.comparing(ReviewListDTO::getFinalizedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(ReviewListDTO::getDueAt);
    }

    private long countOf(List<ReviewListDTO> rows, ReviewTab tab) {
        return rows.stream().filter(row -> tab.includes(row.getStatus())).count();
    }

    /** 서버 현재 시각 — DB가 DATETIME(6)이라 마이크로초 아래를 잘라 둔다(Enrollment.create와 같은 이유) */
    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
