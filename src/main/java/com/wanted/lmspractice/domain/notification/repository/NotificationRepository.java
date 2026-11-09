package com.wanted.lmspractice.domain.notification.repository;

import com.wanted.lmspractice.domain.notification.entity.Notification;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Notification 테이블 접근 (Spring Data JPA)
 * 담당: 팀원 4 · #17
 * 모든 조회에 user_id 조건이 들어가 다른 사람 알림은 읽을 수 없다.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 같은 dedupe_key가 이미 있는지 — UNIQUE(user_id, dedupe_key) 위반 예외로 호출자 트랜잭션이 롤백되지 않게 저장 전에 확인한다 */
    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    /** 안 읽은 알림 수 (헤더 표시) */
    long countByUserIdAndReadAtIsNull(Long userId);

    /** 본인 알림 한 건 (남의 알림이면 비어 있음) */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** 안 읽은 알림 전체 (모두 읽음 처리) */
    List<Notification> findByUserIdAndReadAtIsNull(Long userId);

    /** 헤더 알림창 미리보기 — 최근 5건 */
    List<Notification> findTop5ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 탭의 알림 수 (페이지 수 계산) */
    long countByUserIdAndKindIn(Long userId, Collection<NotificationKind> kinds);

    /** 탭의 한 페이지 — 최신순. 인덱스 idx_notifications_inbox 순서에 맞춘 정렬 */
    @Query(value = "SELECT * FROM notifications WHERE user_id = :userId AND kind IN (:kinds) "
            + "ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Notification> findPage(@Param("userId") Long userId, @Param("kinds") Collection<String> kinds,
                                @Param("limit") int limit, @Param("offset") int offset);
}
