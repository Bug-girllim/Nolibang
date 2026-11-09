package com.wanted.lmspractice.domain.learning.repository;

import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import com.wanted.lmspractice.domain.learning.entity.LessonProgressId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] lesson_progresses 표 접근 (Spring Data JPA)
 * [담당] 팀원 3 · #12
 * [연결]
 *  - 호출받음: ProgressService
 *  - 호출함:   없음
 * 복합 기본키 → ID 타입은 LessonProgressId (findById(new LessonProgressId(…)) 로 조회)
 * [기술] 🟢 쿼리 메서드, 🟢 @Query(nativeQuery) + FOR UPDATE (행 잠금 — @Lock은 배우지 않아 native로 대신)
 */
@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgressId> {

    /**
     * 재생 보고를 처리할 행을 잠그고 읽는다. 같은 차시 보고가 겹쳐도(다중 탭) 한 번에 하나씩 순서대로 처리된다.
     * 호출: ProgressService.report
     */
    @Query(value = "SELECT * FROM lesson_progresses WHERE enrollment_id = :enrollmentId AND lesson_id = :lessonId FOR UPDATE",
            nativeQuery = true)
    Optional<LessonProgress> findForUpdate(@Param("enrollmentId") Long enrollmentId, @Param("lessonId") Long lessonId);

    /** 한 수강의 차시 진도 전체. 호출: ProgressService.getLessonView·getLessonStatuses */
    List<LessonProgress> findByEnrollmentId(Long enrollmentId);

    /** 완료한 차시 수 (3이면 최종 과제 제출 가능). 호출: ProgressService.isAllCompleted */
    long countByEnrollmentIdAndCompletedAtIsNotNull(Long enrollmentId);
}
