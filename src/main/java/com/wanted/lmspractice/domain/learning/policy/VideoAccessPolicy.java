package com.wanted.lmspractice.domain.learning.policy;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * [역할] 영상 접근 규칙 = 본인 수강 && (수강 30일 이내 || 수료). 미수료 만료자는 거부한다.
 * [담당] 팀원 3 · #12   [기획] D-42, FR-020
 * [연결]
 *  - 호출받음: ProgressService (차시 화면·영상 출력·재생 보고 전에),
 *             AssignmentService·ReviewResultService (canWatch — 학습 메뉴의 [차시 영상 학습] 링크 표시 여부)
 *  - 호출함:   Enrollment.isWithinPeriod (계약 C6, 자기 시각 칼럼만으로 기간 판단)
 * [전제] "본인 수강"은 호출한 쪽이 EnrollmentService.getOwnedEnrollment(userId, enrollmentId)로 이미 확인했다.
 *        이 클래스는 그 수강으로 이 차시의 영상을 볼 수 있는 상태인지만 본다.
 * [기술] 🟢 Policy(저장·상태 변경 없음), 🟢 BusinessException
 */
@Component
public class VideoAccessPolicy {

    /**
     * @param enrollment     본인 확인이 끝난 수강
     * @param lessonCourseId 보려는 차시가 속한 강의 — 수강한 강의의 차시여야 한다
     * @throws BusinessException LESSON_NOT_FOUND 다른 강의의 차시(존재를 숨긴다), VIDEO_ACCESS_DENIED 기간이 끝난 미수료·취소·만료 수강
     */
    public void checkVideoAccess(Enrollment enrollment, Long lessonCourseId, LocalDateTime now) {
        if (!enrollment.getCourseId().equals(lessonCourseId)) {
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);
        }
        if (!canWatch(enrollment, now)) {
            throw new BusinessException(ErrorCode.VIDEO_ACCESS_DENIED);
        }
    }

    /**
     * 이 수강으로 지금 차시 영상을 볼 수 있는지 (예외 없이 true/false).
     * 과제·결과 화면의 학습 메뉴가 [차시 영상 학습] 링크를 보여 줄지 정하는 데 쓴다 — 만료·취소된 수강에 링크를 두면 누를 때 403이 된다.
     * 호출: checkVideoAccess, AssignmentService.getAssignmentView, ReviewResultService(결과 화면)
     */
    public boolean canWatch(Enrollment enrollment, LocalDateTime now) {
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            return true;   // D-42: 수료자는 기간과 관계없이 시청 가능
        }
        // ACTIVE이면서 기간 안일 때만. 평가 중에 기간이 끝난 ACTIVE(정원 점유)도 영상은 막는다.
        return enrollment.getStatus() == EnrollmentStatus.ACTIVE && enrollment.isWithinPeriod(now);
    }
}
