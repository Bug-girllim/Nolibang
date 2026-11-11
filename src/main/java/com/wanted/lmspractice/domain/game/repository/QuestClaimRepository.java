package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.QuestClaim;
import com.wanted.lmspractice.domain.game.entity.QuestClaimId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface QuestClaimRepository extends JpaRepository<QuestClaim, QuestClaimId> {

    /** 이번 기간(오늘·이번 주)에 받은 퀘스트 — 보상 받기 버튼 상태 판단용 */
    List<QuestClaim> findByUserIdAndPeriodKeyIn(Long userId, Collection<String> periodKeys);

    /** 회원의 quest_claims 행 전부 삭제 — 회원 탈퇴(U-15 4번 배지·개인 기록). 호출: GameService.deleteAllOfUserForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM quest_claims WHERE user_id = :userId", nativeQuery = true)
    int deleteAllByUserId(@Param("userId") Long userId);
}
