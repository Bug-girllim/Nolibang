package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
