package com.wanted.lmspractice.domain.review.policy;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 최종 과제 제출·재제출·평가 전 수정이 가능한지 판단한다. 위반하면 BusinessException — 저장·상태 변경은 하지 않는다.
 * [담당] 팀원 3 · #13   [화면] U-S07   [기획] FR-021·022·026, D-41
 * [연결]
 *  - 호출받음: AssignmentService(submit·resubmit·edit 저장 전, getAssignmentView의 기간 판단)
 *  - 호출함:   ProgressService.isAllCompleted(계약 C10 — 3차시 완료), Enrollment.isWithinPeriod·isActive(계약 C6)
 * [규칙] 3차시 완료 / 수강 30일 이내(ACTIVE) / 진행 중(WAITING·IN_REVIEW) 평가는 수강당 1건 / 평가 시작 후 수정 잠금 / 이미지 1~5장
 *       이미지 형식(JPG·PNG·WebP)·장당 50MB는 파일을 직접 다루는 FileStorage(#16, 팀원 4)가 검사한다(중복 구현 방지).
 * [기술] 🟢 @Component Policy, BusinessException(ErrorCode)
 */
@Component
@RequiredArgsConstructor
public class SubmissionPolicy {

    /** 한 번에 올릴 수 있는 이미지 수 (FR-022: 1~5장) */
    public static final int MIN_IMAGES = 1;
    public static final int MAX_IMAGES = 5;

    private final ProgressService progressService;

    /**
     * 최초 제출 가능 여부.
     * 호출: AssignmentService.submit (POST /assignments, API AS-02)
     *
     * @param rounds     이 수강 건의 회차(최근 회차부터, 잠금 읽기 결과)
     * @param imageCount 비어 있지 않은 이미지 수
     * @throws BusinessException ALREADY_SUBMITTED(진행 중 회차 있음), INVALID_REVIEW_STATE(이미 평가 결과가 있음 — 반려면 재제출로),
     *                           ENROLLMENT_EXPIRED, LESSONS_NOT_COMPLETED, INVALID_IMAGE_COUNT
     */
    public void checkCanSubmit(Enrollment enrollment, List<ReviewRound> rounds, int imageCount, LocalDateTime now) {
        checkNoActiveRound(rounds);
        if (!rounds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);  // 반려 뒤에는 AS-04 재제출, 승인 뒤에는 제출 없음
        }
        checkPeriod(enrollment, now);
        if (!progressService.isAllCompleted(enrollment.getId())) {
            throw new BusinessException(ErrorCode.LESSONS_NOT_COMPLETED);  // FR-021: 3차시 완료 후 제출
        }
        checkImageCount(imageCount);
    }

    /**
     * 반려 후 정식 재제출 가능 여부 — 직전 회차가 반려이고 수강 기간 안일 때만. 횟수 제한은 없다(FR-026).
     * 호출: AssignmentService.resubmit (POST /assignments/resubmit, API AS-04)
     *
     * @throws BusinessException ALREADY_SUBMITTED, RESUBMIT_NOT_ALLOWED(직전 회차가 반려가 아님), ENROLLMENT_EXPIRED, INVALID_IMAGE_COUNT
     */
    public void checkCanResubmit(Enrollment enrollment, List<ReviewRound> rounds, int imageCount, LocalDateTime now) {
        checkNoActiveRound(rounds);
        if (rounds.isEmpty() || !rounds.get(0).isRejected()) {
            throw new BusinessException(ErrorCode.RESUBMIT_NOT_ALLOWED);
        }
        checkPeriod(enrollment, now);  // 05 §4-2: 만료 후 반려는 재제출 없이 종료
        // ❓ 임시: 재제출은 새 버전이라 이미지도 새로 1~5장 받는다(기존 이미지 유지 허용 여부는 API §미확정 13, 팀원 3·4 결정)
        checkImageCount(imageCount);
    }

    /**
     * 평가 시작 전 수정 가능 여부 — 평가 대기일 때만. 이미지는 안 보내면(0장) 기존 이미지 유지, 보내면 1~5장으로 전체 교체.
     * 호출: AssignmentService.edit (POST /assignments/{reviewRoundId}/edit, API AS-03)
     *
     * @throws BusinessException SUBMISSION_LOCKED(평가 시작됨), INVALID_REVIEW_STATE(승인·반려 확정 — D-63 미정이라 수정 없음),
     *                           ENROLLMENT_EXPIRED, INVALID_IMAGE_COUNT(6장 이상)
     */
    public void checkCanEdit(Enrollment enrollment, ReviewRound round, int imageCount, LocalDateTime now) {
        if (round.isInReview()) {
            throw new BusinessException(ErrorCode.SUBMISSION_LOCKED);  // E-MODALS "평가 중 과제 잠금"
        }
        if (!round.isWaiting()) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }
        checkPeriod(enrollment, now);
        if (imageCount > 0) {  // ❓ 임시: 0장 = 기존 이미지 유지 (API AS-03 💡)
            checkImageCount(imageCount);
        }
    }

    /**
     * 새로 제출할 수 있는 수강 기간인지 — 학습 중(ACTIVE)이고 확정 후 30일 안. 수료·취소·만료한 수강 건은 제출할 수 없다.
     * 호출: checkPeriod, AssignmentService.getAssignmentView(화면 상태 EXPIRED 판단 — 같은 기준을 한곳에서 쓴다)
     */
    public boolean isPeriodOpen(Enrollment enrollment, LocalDateTime now) {
        return enrollment.isActive() && enrollment.isWithinPeriod(now);
    }

    private void checkPeriod(Enrollment enrollment, LocalDateTime now) {
        if (!isPeriodOpen(enrollment, now)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_EXPIRED);
        }
    }

    /** 진행 중(평가 대기·평가 중) 회차는 수강당 1건 — 제출 버튼 연타·다른 탭 중복 제출 방지 */
    private void checkNoActiveRound(List<ReviewRound> rounds) {
        if (rounds.stream().anyMatch(ReviewRound::isActive)) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }
    }

    private void checkImageCount(int imageCount) {
        if (imageCount < MIN_IMAGES || imageCount > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_COUNT);
        }
    }
}
