package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.entity.UserEquippedBadge;
import com.wanted.lmspractice.domain.game.entity.UserEquippedBadgeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEquippedBadgeRepository extends JpaRepository<UserEquippedBadge, UserEquippedBadgeId> {

    /** 여러 사용자의 장착 배지를 한 번에 — 게시판 한 쪽의 작성자 전체를 쿼리 1번으로 읽는다 (사용자별 칸 번호 순) */
    List<UserEquippedBadge> findByUserIdInOrderByUserIdAscSlotNoAsc(java.util.Collection<Long> userIds);

    /** 장착한 배지 — 칸 번호 순 */
    List<UserEquippedBadge> findByUserIdOrderBySlotNo(Long userId);

    /**
     * 장착 저장 — 같은 칸이거나 같은 배지가 이미 있으면 건너뛴다(INSERT IGNORE).
     * 더블클릭·동시 요청이 와도 기본키/유일키 예외로 요청이 롤백되지 않고 "0행"을 돌려받는다.
     *
     * @return 새로 저장했으면 1, 건너뛰었으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)   // 네이티브 쿼리는 1차 캐시를 모르므로 실행 후 캐시를 비운다
    @Query(value = "INSERT IGNORE INTO user_equipped_badges (user_id, slot_no, badge_code) VALUES (:userId, :slotNo, :badgeCode)",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("slotNo") int slotNo, @Param("badgeCode") String badgeCode);

    /** 해제 — 지운 행 수를 돌려준다(없으면 0) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)   // 네이티브 쿼리는 1차 캐시를 모르므로 실행 후 캐시를 비운다
    @Query(value = "DELETE FROM user_equipped_badges WHERE user_id = :userId AND badge_code = :badgeCode", nativeQuery = true)
    int deleteByCode(@Param("userId") Long userId, @Param("badgeCode") String badgeCode);
}
