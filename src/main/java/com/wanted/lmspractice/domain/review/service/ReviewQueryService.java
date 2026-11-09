package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import java.time.LocalDateTime;
import java.util.List;
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

    /** 계약 C11: 본인·같은 취미의 최근 승인 피드백 또는 반려 사유 1건 (사용: 팀원 4 #19) */
    public Optional<FeedbackRefDTO> findLatestFeedbackForReference(Long userId, Long hobbyId) {
        // TODO #19 연동 시 구현 — #14 이슈 범위(hasPendingReview·getLatestStatus) 밖이라 스텁으로 둔다
        return Optional.empty();
    }
}
