package com.wanted.lmspractice.domain.notification.repository;

import com.wanted.lmspractice.domain.notification.entity.Notification;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 회원 탈퇴로 지워질 데이터와 이어진 알림 전부 삭제 — 본인이 받은 알림 + 남이 받았지만 대상이 탈퇴자의 작품·그 작품의 댓글·
     * 수강·평가·문의인 알림(예: 강사의 과제 제출·문의 알림). 대상 행보다 먼저 지워야 FK 가 깨지지 않는다.
     * 탈퇴자가 남의 작품에 단 댓글은 익명으로 남으므로 그 댓글을 가리키는 알림은 남긴다(알림 본문에 작성자 이름이 없다).
     * 호출: NotificationService.deleteAllForWithdrawal
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM notifications WHERE user_id = :userId"
            + " OR work_id IN (SELECT w.id FROM works w WHERE w.author_id = :userId)"
            + " OR comment_id IN (SELECT c.id FROM work_comments c JOIN works w ON w.id = c.work_id WHERE w.author_id = :userId)"
            + " OR enrollment_id IN (SELECT e.id FROM enrollments e WHERE e.user_id = :userId)"
            + " OR review_round_id IN (SELECT rr.id FROM review_rounds rr JOIN enrollments e ON e.id = rr.enrollment_id WHERE e.user_id = :userId)"
            + " OR inquiry_thread_id IN (SELECT t.id FROM inquiry_threads t WHERE t.learner_id = :userId)",
            nativeQuery = true)
    int deleteAllRelatedToUser(@Param("userId") Long userId);
}
