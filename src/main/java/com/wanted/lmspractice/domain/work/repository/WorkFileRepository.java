package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/**
 * [역할] work_files 표 접근 (Spring Data JPA)
 * [담당] 팀원 4 · #16 (#19 대표 이미지 조회 추가)
 * [연결] 호출받음: WorkService, GrowthService(#19)
 */
@Repository
public interface WorkFileRepository extends JpaRepository<WorkFile, Long> {

    /** 한 버전의 이미지를 보여 줄 순서대로 */
    List<WorkFile> findByWorkVersionIdOrderBySortOrder(Long workVersionId);

    /** 대표 이미지(첫 번째 장) — 성장 타임라인의 첫/최근 작품 썸네일(#19, D-13) */
    Optional<WorkFile> findFirstByWorkVersionIdOrderBySortOrder(Long workVersionId);

    /** 여러 버전의 이미지를 한 번에 — 타임라인 항목마다 대표 이미지를 찾을 때 쿼리를 하나로 줄인다(버전별 첫 장은 호출한 쪽이 고른다) */
    List<WorkFile> findByWorkVersionIdInOrderByWorkVersionIdAscSortOrderAsc(java.util.Collection<Long> workVersionIds);

    /** 이미지 교체 때 이전 행 삭제 — 호출 뒤 flush 해야 같은 sort_order 새 행을 넣을 수 있다 */
    void deleteByWorkVersionId(Long workVersionId);

    /** 탈퇴자 작품의 모든 버전의 이미지 행 — 실제 파일 key 를 모으려고 지우기 전에 읽는다. 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Query(value = "SELECT f.* FROM work_files f JOIN work_versions v ON v.id = f.work_version_id"
            + " JOIN works w ON w.id = v.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    List<WorkFile> findAllOfAuthor(@Param("authorId") Long authorId);

    /** 탈퇴자 작품의 이미지 행 전부 삭제. 호출: WorkService.deleteAllOfAuthorForWithdrawal */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE f FROM work_files f JOIN work_versions v ON v.id = f.work_version_id"
            + " JOIN works w ON w.id = v.work_id WHERE w.author_id = :authorId",
            nativeQuery = true)
    int deleteAllOfAuthor(@Param("authorId") Long authorId);
}
