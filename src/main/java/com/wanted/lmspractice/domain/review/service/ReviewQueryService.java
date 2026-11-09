package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인이 쓰는 평가 상태 조회 (계약 C11)
 * 담당: 팀원 3 · #14
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class ReviewQueryService {

    /** 계약 C11: 평가 대기·진행 중 과제가 있는지 (사용: 팀원 2 수강 취소·만료 판단) */
    public boolean hasPendingReview(Long enrollmentId) {
        // TODO #14: 구현 (현재는 스텁)
        return false;
    }

    /** 계약 C11: 최신 평가 상태 (사용: 팀원 2 #11, 팀원 1 #07) */
    public ReviewStatusDTO getLatestStatus(Long enrollmentId) {
        throw new UnsupportedOperationException("TODO #14");
    }

    /** 계약 C11: 본인·같은 취미의 최근 승인 피드백 또는 반려 사유 1건 (사용: 팀원 4 #19) */
    public Optional<FeedbackRefDTO> findLatestFeedbackForReference(Long userId, Long hobbyId) {
        // TODO #14: 구현 (현재는 스텁)
        return Optional.empty();
    }
}
