package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.dto.DueFilter;
import com.wanted.lmspractice.domain.review.dto.InstructorReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDecisionDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewListPageDTO.SectionFilterDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewTab;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.policy.ReviewAssignmentPolicy;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery;
import com.wanted.lmspractice.domain.review.repository.ReviewQuery.ReviewContext;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사 평가 흐름 — 목록·상세 화면 조립, 평가 시작(학습자 수정 잠금)·시작 취소·초안 저장·승인/반려 → 수료·자리 반환·알림
 * [담당] 팀원 3 · #14   [화면] T-S03, T-S04   [API] RV-01~RV-07   [기획] FR-023·030·031, D-56, D-37, D-59
 * [연결]
 *  - 호출받음: InstructorReviewController,
 *             WithdrawalService.withdraw(회원 탈퇴 — 이 도메인의 탈퇴자 데이터 정리)
 *  - 호출함:   ReviewAssignmentPolicy(현재 분반 담당 강사 확인 — C6), ReviewRound 상태 메서드,
 *             EnrollmentService.complete·closeAfterRejection(계약 C5, 팀원 2 — 내부에서 fillVacancies),
 *             NotificationService.notify(계약 C7, 팀원 4), ReviewQuery(화면용 조회)
 *  - 트랜잭션: complete·closeAfterRejection·notify는 모두 이 클래스의 트랜잭션에 참여한다(REQUIRED).
 *             승인 저장·수료·자리 반환·알림이 한 번에 커밋·롤백된다 — 별도 트랜잭션이면 "수료는 됐는데 자리 반환 안 됨"이 생긴다.
 * [기술] 🟢 @Transactional + 변경 감지, 🟢 native SELECT … FOR UPDATE(회차 행 잠금), 🟢 DB UNIQUE(uq_reviews_approved) 이중 방어
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewQuery reviewQuery;
    private final ReviewAssignmentPolicy assignmentPolicy;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;

    // ───────────────────────── 화면 조회 ─────────────────────────

    /**
     * 평가 목록 — 지금 본인이 담당하는 분반의 회차만, 탭·분반·기한 필터 적용.
     * 호출: InstructorReviewController.list (GET /instructor/reviews, RV-01)
     * @param sectionId 분반 필터(null이면 전체)   @param due 기한 필터(null이면 전체)
     */
    @Transactional(readOnly = true)
    public ReviewListPageDTO getReviewList(Long instructorId, ReviewTab tab, Long sectionId, DueFilter due) {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewContext> contexts = reviewQuery.findContextsByInstructor(instructorId);
        Map<Long, ReviewRound> rounds = reviewRoundRepository
                .findAllById(contexts.stream().map(ReviewContext::getReviewRoundId).toList())
                .stream().collect(Collectors.toMap(ReviewRound::getId, Function.identity()));

        List<ReviewListDTO> inSection = contexts.stream()
                .filter(c -> sectionId == null || sectionId.equals(c.getSectionId()))
                .map(c -> ReviewListDTO.from(rounds.get(c.getReviewRoundId()), c, now))
                .toList();

        List<ReviewListDTO> shown = inSection.stream()
                .filter(r -> tab.contains(r.getStatus()))
                .filter(r -> due == null || due.matches(r))
                .toList();

        return new ReviewListPageDTO(
                shown,
                countIn(inSection, ReviewTab.WAITING),
                countIn(inSection, ReviewTab.IN_REVIEW),
                countIn(inSection, ReviewTab.DONE),
                reviewQuery.findSectionsByInstructor(instructorId).stream()
                        .map(s -> new SectionFilterDTO(s.getSectionId(),
                                s.getCourseTitle() + " · " + s.getLevelLabel() + " · " + s.getSectionName()))
                        .toList());
    }

    /**
     * 평가 상세 — 현재 담당 강사만 볼 수 있다. 단순 열람은 잠금이 아니다(RV-02).
     * 호출: InstructorReviewController.detail (GET /instructor/reviews/{id}, RV-02), approve·reject 입력 오류 재표시
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR
     */
    @Transactional(readOnly = true)
    public InstructorReviewDetailDTO getReviewDetail(Long instructorId, Long reviewRoundId) {
        ReviewRound round = reviewRoundRepository.findById(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        assignmentPolicy.checkAssigned(round.getEnrollmentId(), instructorId);
        ReviewContext context = contextOf(reviewRoundId);
        return InstructorReviewDetailDTO.from(
                round,
                context,
                reviewQuery.findSubmission(round.getWorkVersionId()).orElse(null),
                reviewRoundRepository.findByEnrollmentIdOrderByRoundNoAsc(round.getEnrollmentId()),
                LocalDateTime.now());
    }

    // ───────────────────────── 상태 변경 ─────────────────────────

    /**
     * 평가 시작 — WAITING → IN_REVIEW, 학습자 과제 수정 잠금.
     * 호출: InstructorReviewController.start (POST /instructor/reviews/{id}/start, RV-03)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE
     */
    @Transactional
    public void start(Long instructorId, Long reviewRoundId) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.start(instructorId, LocalDateTime.now());
    }

    /**
     * 평가 시작 취소 — 초안이 비어 있을 때만 WAITING으로 되돌린다. 마감은 유지(D-56).
     * 학습자 수정 재허용은 #13 SubmissionPolicy가 "WAITING + 수강 기간 안"으로 판단한다.
     * 호출: InstructorReviewController.cancelStart (RV-04)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, CANNOT_CANCEL_START
     */
    @Transactional
    public void cancelStart(Long instructorId, Long reviewRoundId) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.cancelStart();
    }

    /**
     * 피드백 초안 저장 — 진행 중일 때만. 담당이 바뀌었으면 새 담당 강사가 이어서 쓴다.
     * 호출: InstructorReviewController.saveDraft (RV-05)
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE
     */
    @Transactional
    public void saveDraft(Long instructorId, Long reviewRoundId, String draft) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        round.saveDraft(draft, instructorId, LocalDateTime.now());
    }

    /**
     * 수료 승인 — 한 트랜잭션 안에서 ① 회차 APPROVED ② 수강 COMPLETED·자리 반환·대기 1순위 확정 ③ 학습자 알림 2건 (RV-06).
     * 동시에 두 번 눌러도 회차 행 잠금 + 상태 검사로 두 번째는 INVALID_REVIEW_STATE가 되어 수료는 1회만 일어난다.
     * 수강 기간이 지난 뒤에도 승인할 수 있다(기간 안에 제출한 과제는 끝까지 평가).
     * 호출: InstructorReviewController.approve
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT
     */
    @Transactional
    public void approve(Long instructorId, Long reviewRoundId, ReviewDecisionDTO dto) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        ReviewContext context = contextOf(reviewRoundId);   // 알림 받을 학습자·강의명 — 상태를 바꾸기 전에 읽어 둔다
        round.approve(instructorId, dto.getContent(), LocalDateTime.now());
        enrollmentService.complete(round.getEnrollmentId());   // C5: 같은 트랜잭션 — 내부에서 fillVacancies

        notificationService.notify(context.getLearnerId(), NotificationKind.REVIEW_APPROVED, round.getId(),
                "평가 승인", context.getCourseTitle() + " 최종 과제가 승인되었습니다.");
        notificationService.notify(context.getLearnerId(), NotificationKind.COURSE_COMPLETED, round.getEnrollmentId(),
                "수료", context.getCourseTitle() + " 강의를 수료했습니다.");
    }

    /**
     * 반려 — ① 회차 REJECTED ② 학습자 알림 ③ 수강 기간이 이미 지났으면 수강 종료(EXPIRED)·자리 반환 (RV-07).
     * 기간 안의 반려는 수강이 ACTIVE로 남아 학습자가 정식 재제출할 수 있다(#13).
     * 호출: InstructorReviewController.reject
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR, INVALID_REVIEW_STATE, FEEDBACK_TOO_SHORT
     */
    @Transactional
    public void reject(Long instructorId, Long reviewRoundId, ReviewDecisionDTO dto) {
        ReviewRound round = lockAssignedRound(instructorId, reviewRoundId);
        ReviewContext context = contextOf(reviewRoundId);
        LocalDateTime now = LocalDateTime.now();
        round.reject(instructorId, dto.getContent(), now);

        notificationService.notify(context.getLearnerId(), NotificationKind.REVIEW_REJECTED, round.getId(),
                "평가 반려", context.getCourseTitle() + " 최종 과제가 반려되었습니다. 반려 사유를 확인해 주세요.");

        // 만료 후 반려는 재제출 기회가 없으므로 수강을 끝내고 자리를 돌려준다(05 §4-2). 기간 판단은 Enrollment 한곳의 규칙을 쓴다
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(context.getLearnerId(), round.getEnrollmentId());
        if (enrollment.isActive() && !enrollment.isWithinPeriod(now)) {
            enrollmentService.closeAfterRejection(round.getEnrollmentId());   // C5: 같은 트랜잭션 — 내부에서 fillVacancies
        }
    }

    // ───────────────────────── 내부 ─────────────────────────

    /**
     * 회차를 잠그고(FOR UPDATE) 현재 담당 강사인지 확인한다 — 잠근 뒤 확인해야 같은 회차의 동시 요청이 차례로 처리된다.
     * @throws BusinessException REVIEW_NOT_FOUND, NOT_ASSIGNED_INSTRUCTOR
     */
    private ReviewRound lockAssignedRound(Long instructorId, Long reviewRoundId) {
        ReviewRound round = reviewRoundRepository.findByIdForUpdate(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        assignmentPolicy.checkAssigned(round.getEnrollmentId(), instructorId);
        return round;
    }

    private ReviewContext contextOf(Long reviewRoundId) {
        return reviewQuery.findContext(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private static long countIn(List<ReviewListDTO> rows, ReviewTab tab) {
        return rows.stream().filter(r -> tab.contains(r.getStatus())).count();
    }

    /**
     * 회원 탈퇴 — 학습자의 모든 평가 회차(공식 피드백·반려 사유 포함)를 지운다(U-15 4번 "강사 피드백 삭제").
     * 이 회차를 참고 피드백으로 가리키던 작품 연결은 먼저 끊겨 있어야 한다(WorkService.detachAllOfAuthorForWithdrawal).
     * 호출: WithdrawalService.withdraw (탈퇴 트랜잭션 안)
     */
    @Transactional
    public int deleteAllOfLearnerForWithdrawal(Long userId) {
        return reviewRoundRepository.deleteAllOfLearner(userId);
    }
}
