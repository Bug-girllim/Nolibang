package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 다른 도메인이 쓰는 평가 상태 조회 (계약 C11) — 읽기만 한다
 * [담당] 팀원 3 · #14
 * [연결]
 *  - 호출받음: 팀원 2 EnrollmentService(#09 수강 취소·신청 영역, hasPendingReview), #10 만료 처리, MyLearningService(#11 getLatestStatus),
 *             팀원 1 #07, 팀원 4 #19(findLatestFeedbackForReference)
 *  - 호출함:   ReviewRoundRepository
 *  - 계약:     C11 — 시그니처 변경 금지
 *  - 순환 주의: EnrollmentService가 이 클래스를 주입하므로, 여기서는 EnrollmentService·SeatService·NotificationService를 주입하지 않는다
 * [기술] 🟢 @Transactional(readOnly = true), 🟢 쿼리 메서드
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    /** 평가가 끝나지 않은 상태 — 정원 점유·수강 취소 불가 기준 (ReviewRound.isPending과 같은 기준) */
    private static final List<ReviewStatus> PENDING = List.of(ReviewStatus.WAITING, ReviewStatus.IN_REVIEW);

    private final ReviewRoundRepository reviewRoundRepository;

    /**
     * 계약 C11: 평가 대기·진행 중 회차가 있는지.
     * 호출: EnrollmentService.cancel·getStatusForUser(#09), #10 만료 처리
     */
    @Transactional(readOnly = true)
    public boolean hasPendingReview(Long enrollmentId) {
        return reviewRoundRepository.existsByEnrollmentIdAndStatusIn(enrollmentId, PENDING);
    }

    /**
     * 계약 C11: 최신 평가 상태 — 가장 최근 회차의 id·상태와 최초(1회차) 제출 시각. 제출한 적이 없으면 notSubmitted().
     * 호출: MyLearningService(#11), #07
     */
    @Transactional(readOnly = true)
    public ReviewStatusDTO getLatestStatus(Long enrollmentId) {
        List<ReviewRound> rounds = reviewRoundRepository.findByEnrollmentIdOrderByRoundNoAsc(enrollmentId);
        if (rounds.isEmpty()) {
            return ReviewStatusDTO.notSubmitted();
        }
        ReviewRound latest = rounds.get(rounds.size() - 1);
        return new ReviewStatusDTO(latest.getId(), latest.getStatus(), rounds.get(0).getSubmittedAt());
    }

    /** 계약 C11: 본인·같은 취미의 최근 승인 피드백 또는 반려 사유 1건 (사용: 팀원 4 #19) — #14 범위 밖이라 스텁 유지 */
    public Optional<FeedbackRefDTO> findLatestFeedbackForReference(Long userId, Long hobbyId) {
        // TODO #19 연결 시 구현 (현재는 스텁) — FeedbackRefDTO 필드가 정해지면 팀원 3이 채운다
        return Optional.empty();
    }
}
