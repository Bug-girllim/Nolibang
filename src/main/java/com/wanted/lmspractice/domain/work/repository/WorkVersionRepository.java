package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_versions 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16
 * [연결] 호출받음: WorkService
 */
@Repository
public interface WorkVersionRepository extends JpaRepository<WorkVersion, Long> {

    /** 다음 version_no 를 정하기 위한 마지막 버전 */
    Optional<WorkVersion> findTopByWorkIdOrderByVersionNoDesc(Long workId);

    /** 탈퇴자 작품의 버전(제목·설명) 전부 삭제 — 평가 회차·이미지를 먼저 지워야 한다. 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE v FROM work_versions v JOIN works w ON w.id = v.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    int deleteAllOfAuthor(@Param("authorId") Long authorId);
}
