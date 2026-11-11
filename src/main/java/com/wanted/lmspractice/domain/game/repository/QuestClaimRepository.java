package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.QuestClaim;
import com.wanted.lmspractice.domain.game.entity.QuestClaimId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestClaimRepository extends JpaRepository<QuestClaim, QuestClaimId> {

    /** 이번 기간(오늘·이번 주)에 받은 퀘스트 — 보상 받기 버튼 상태 판단용 */
    List<QuestClaim> findByUserIdAndPeriodKeyIn(Long userId, Collection<String> periodKeys);
}
