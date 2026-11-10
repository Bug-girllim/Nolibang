package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 다른 도메인이 쓰는 평가 상태 조회 (계약 C11)
 * [담당] 팀원 3 · #14
 * [연결]
 *  - 호출받음: EnrollmentService(#09 수강 취소 가능 판단 — hasPendingReview), MyLearningService(#11 — getLatestStatus),
 *             강사·관리자 홈(#07), 일반 작품 참고 피드백(#19 — findLatestFeedbackForReference)
 *  - 호출함:   ReviewRoundRepository
 *  - 계약:     C11 — 시그니처 변경 금지
 * [기술] 🟢 @Transactional(readOnly = true), 🟢 쿼리 메서드
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    /** 평가가 아직 확정되지 않은 상태 — 이 상태의 회차가 있으면 수강 취소 불가(#09) */
    private static final List<ReviewStatus> PENDING = List.of(ReviewStatus.WAITING, ReviewStatus.IN_REVIEW);

    private final ReviewRoundRepository reviewRoundRepository;
    private final CourseService courseService;   // 참고 피드백 화면의 강의명·수준 (#19 연동)

    /** 계약 C11: 평가 대기·진행 중 과제가 있는지 (사용: 팀원 2 수강 취소·만료 판단) */
    @Transactional(readOnly = true)
    public boolean hasPendingReview(Long enrollmentId) {
        return reviewRoundRepository.existsByEnrollmentIdAndStatusIn(enrollmentId, PENDING);
    }

    /**
     * 계약 C11: 최신 평가 상태 (사용: 팀원 2 #11, 팀원 1 #07). 제출한 적이 없으면 status가 null인 DTO(미제출)를 돌려준다.
     * firstSubmittedAt은 1회차 제출 시각(최초 제출), reviewRoundId·status는 가장 최근 회차 값이다.
     */
    @Transactional(readOnly = true)
    public ReviewStatusDTO getLatestStatus(Long enrollmentId) {
        Optional<ReviewRound> latest = reviewRoundRepository.findFirstByEnrollmentIdOrderByRoundNoDesc(enrollmentId);
        if (latest.isEmpty()) {
            return ReviewStatusDTO.notSubmitted();
        }
        LocalDateTime firstSubmittedAt = reviewRoundRepository.findFirstByEnrollmentIdOrderByRoundNoAsc(enrollmentId)
                .map(ReviewRound::getSubmittedAt)
                .orElse(null);
        // 회차 값은 #13 ofRound(이력 한 줄과 같은 규칙)를 쓰고, C11에서만 쓰는 최초 제출 시각을 더한다
        ReviewStatusDTO dto = ReviewStatusDTO.ofRound(latest.get());
        dto.setFirstSubmittedAt(firstSubmittedAt);
        return dto;
    }

    /**
     * [성능 개선] 여러 수강의 최신 평가 상태를 한 번에 — 회차 조회 1번. 값은 수강마다 getLatestStatus(계약 C11)를 부른 것과 같다
     * (가장 큰 회차의 값 + 1회차(가장 작은 회차)의 제출 시각, 제출한 적이 없으면 미제출 DTO).
     * 수강 카드마다 getLatestStatus(최신 회차 1번 + 최초 회차 1번)를 부르던 반복 조회를 없앤다.
     * 호출: MyLearningService.getMyLearning
     *
     * @return 수강 id → 최신 평가 상태 (요청한 모든 id가 들어 있다)
     */
    @Transactional(readOnly = true)
    public Map<Long, ReviewStatusDTO> getLatestStatuses(Collection<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ReviewRound> latestByEnrollment = new HashMap<>();
        Map<Long, ReviewRound> firstByEnrollment = new HashMap<>();
        for (ReviewRound round : reviewRoundRepository.findByEnrollmentIdIn(enrollmentIds)) {
            latestByEnrollment.merge(round.getEnrollmentId(), round,
                    (a, b) -> b.getRoundNo() > a.getRoundNo() ? b : a);
            firstByEnrollment.merge(round.getEnrollmentId(), round,
                    (a, b) -> b.getRoundNo() < a.getRoundNo() ? b : a);
        }
        Map<Long, ReviewStatusDTO> result = new HashMap<>();
        for (Long enrollmentId : enrollmentIds) {
            ReviewRound latest = latestByEnrollment.get(enrollmentId);
            if (latest == null) {
                result.put(enrollmentId, ReviewStatusDTO.notSubmitted());
                continue;
            }
            ReviewStatusDTO dto = ReviewStatusDTO.ofRound(latest);
            dto.setFirstSubmittedAt(firstByEnrollment.get(enrollmentId).getSubmittedAt());
            result.put(enrollmentId, dto);
        }
        return result;
    }

    /** 계약 C11: 본인·같은 취미의 최근 승인 피드백 또는 반려 사유 1건 (사용: 팀원 4 #19) */
    @Transactional(readOnly = true)
    public Optional<FeedbackRefDTO> findLatestFeedbackForReference(Long userId, Long hobbyId) {
        if (userId == null || hobbyId == null) {
            return Optional.empty();
        }
        // D-17·D-47: 같은 사용자·같은 취미의 확정된 평가 회차 중 가장 최근 1건 (승인이면 피드백, 반려면 반려 사유)
        return reviewRoundRepository.findLatestFinalizedByUserAndHobby(userId, hobbyId)
                .flatMap(round -> reviewRoundRepository.findCourseIdByEnrollmentId(round.getEnrollmentId())
                        .map(courseId -> toFeedbackRef(round, courseId)));
    }

    private FeedbackRefDTO toFeedbackRef(ReviewRound round, Long courseId) {
        CourseBriefDTO course = courseService.getCourseBriefs(List.of(courseId)).get(courseId);
        return new FeedbackRefDTO(
                round.getId(),
                course == null ? null : course.getTitle(),
                course == null ? null : course.getLevel(),
                round.getStatus(),
                round.getFeedbackBody(),
                round.getFinalizedAt());
    }
}
