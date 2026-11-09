package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDetailDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewDetailDTO.HistoryDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 학습자의 평가 결과·공식 피드백 화면(U-S14) 값을 조립한다 — 본인 확인, 확정 전 차단, 재제출 가능 여부, 획득 배지, 이력
 * [담당] 팀원 3 · #15   [화면] U-S14   [API] AS-05   [기획] FR-023·024·025
 * [연결]
 *  - 호출받음: AssignmentController.result
 *  - 호출함:   EnrollmentService.getOwnedEnrollment(C6 본인 확인)·getMyEnrollments(분반 이름), CourseService(강의 소개·첫 차시),
 *             BadgeService.getBadges(C12), InstructorAccountService.getInstructor(강사 이름)
 * [새 클래스 — 팀원 3 확인 필요] 이슈의 담당 클래스 목록에는 없지만, AssignmentService(#13)·ReviewQueryService(#14)와 파일이 겹치지 않게 따로 두었다.
 * [기술] 🟢 @Transactional(readOnly), 🟢 JPA 조회 + Java 컬렉션
 */
@Service
@RequiredArgsConstructor
public class ReviewResultService {

    private final ReviewRoundRepository reviewRoundRepository;
    private final EnrollmentService enrollmentService;
    private final CourseService courseService;
    private final BadgeService badgeService;
    private final InstructorAccountService instructorAccountService;

    /**
     * 평가 결과 화면 값 — 승인이면 피드백 전문·배지, 반려면 사유 원문·재제출 가능 여부.
     * 호출: AssignmentController.result (GET /assignments/{reviewRoundId}/result, AS-05)
     * @throws BusinessException REVIEW_NOT_FOUND(없거나 남의 평가 — 존재를 숨김), INVALID_REVIEW_STATE(평가 확정 전)
     */
    @Transactional(readOnly = true)
    public ReviewDetailDTO getResult(Long userId, Long reviewRoundId) {
        ReviewRound round = reviewRoundRepository.findById(reviewRoundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        Enrollment enrollment = getOwnedEnrollmentOrHide(userId, round.getEnrollmentId());

        // 평가 대기·진행 중에는 결과가 없다(배지도 없음)
        if (round.getStatus() != ReviewStatus.APPROVED && round.getStatus() != ReviewStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE);
        }

        Long courseId = enrollment.getCourseId();
        CourseBriefDTO course = courseService.getCourseBriefs(List.of(courseId)).get(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        List<ReviewRound> rounds = reviewRoundRepository.findByEnrollmentIdOrderByRoundNoAsc(enrollment.getId());
        LocalDateTime now = LocalDateTime.now();

        return new ReviewDetailDTO(
                round.getId(),
                enrollment.getId(),
                courseId,
                firstLessonId(courseId),
                course.getTitle(),
                course.getLevelLabel(),
                sectionNameOf(userId, enrollment.getId()),
                instructorNameOf(round.getReviewerId()),
                round.getStatus(),
                round.getFeedbackBody(),
                round.getRoundNo(),
                round.getSubmittedAt(),
                round.getDueAt(),
                round.getStartedAt(),
                round.getFinalizedAt(),
                enrollment.getExpiresAt(),
                remainingDays(enrollment, now),
                canResubmit(round, rounds, enrollment, now),
                badgesOf(userId, round, course),
                rounds.stream()
                        .map(r -> new HistoryDTO(r.getRoundNo(), r.getSubmittedAt(), r.getDueAt(), r.getFinalizedAt(), r.getStatus()))
                        .toList());
    }

    /** 본인 수강 건이 아니면 평가 존재 자체를 숨긴다(API 명세 §1-6: *_NOT_FOUND) */
    private Enrollment getOwnedEnrollmentOrHide(Long userId, Long enrollmentId) {
        try {
            return enrollmentService.getOwnedEnrollment(userId, enrollmentId);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
    }

    /** 반려 + 가장 최근 회차 + 수강 진행 중 + 기간 안일 때만 재제출 링크를 보인다(기간이 지난 반려는 수강이 EXPIRED로 끝난다) */
    private boolean canResubmit(ReviewRound round, List<ReviewRound> rounds, Enrollment enrollment, LocalDateTime now) {
        boolean latest = rounds.get(rounds.size() - 1).getId().equals(round.getId());
        return round.getStatus() == ReviewStatus.REJECTED && latest
                && enrollment.isActive() && enrollment.isWithinPeriod(now);
    }

    private long remainingDays(Enrollment enrollment, LocalDateTime now) {
        return Math.max(0, Duration.between(now, enrollment.getExpiresAt()).toDays());
    }

    /** 승인일 때만: 이 강의의 수준 배지와(세 수준을 모두 수료했다면) 그 취미의 마스터 배지 */
    private List<BadgeDTO> badgesOf(Long userId, ReviewRound round, CourseBriefDTO course) {
        if (round.getStatus() != ReviewStatus.APPROVED) {
            return List.of();
        }
        return badgeService.getBadges(userId).stream()
                .filter(b -> b.getHobbyId().equals(course.getHobbyId()))
                .filter(b -> b.isMaster() || b.getLevel() == course.getLevel())
                .toList();
    }

    private String sectionNameOf(Long userId, Long enrollmentId) {
        return enrollmentService.getMyEnrollments(userId).stream()
                .filter(e -> e.getEnrollmentId().equals(enrollmentId))
                .map(MyEnrollmentDTO::getSectionName)
                .findFirst()
                .orElse(null);
    }

    private Long firstLessonId(Long courseId) {
        List<Lesson> lessons = courseService.getLessonsOfCourse(courseId);
        return lessons.isEmpty() ? null : lessons.get(0).getId();
    }

    /** 담당 강사 이름 — 계정을 찾을 수 없으면(탈퇴 표시는 P2) 이름 없이 표시한다 */
    private String instructorNameOf(Long reviewerId) {
        if (reviewerId == null) {
            return null;
        }
        try {
            return instructorAccountService.getInstructor(reviewerId).getRealName();
        } catch (BusinessException e) {
            return null;
        }
    }
}
