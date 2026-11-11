package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkModerationLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_moderation_logs 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #20
 * [연결] 호출받음: WorkModerationService, GrowthService(성장 기록의 조치 사유 링크 — 이력이 있는 작품 확인, #167)
 */
@Repository
public interface WorkModerationLogRepository extends JpaRepository<WorkModerationLog, Long> {

    /** 한 작품의 조치 이력 — 최신순 (U-S16) */
    List<WorkModerationLog> findByWorkIdOrderByIdDesc(Long workId);

    /** 전체 조치 이력 — 최신순 (A-S08 "조치 이력" 탭) */
    List<WorkModerationLog> findAllByOrderByIdDesc();

    /** 여러 작품의 조치 이력 — 성장 기록에서 "조치 이력이 있는 작품"을 한 번에 고를 때 (#167) */
    List<WorkModerationLog> findByWorkIdIn(Collection<Long> workIds);

    /** 탈퇴자 작품의 관리자 조치 이력 삭제(대상 작품이 지워지므로, fk_modlogs_work). 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE m FROM work_moderation_logs m JOIN works w ON w.id = m.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    int deleteAllOnWorksOfAuthor(@Param("authorId") Long authorId);
}
