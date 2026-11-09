package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 작품 저장·수정 (최종 과제 저장 포함) — 파일 저장은 FileStorage 사용
 * 담당: 팀원 4 · #16 (#19 일반 작품)
 *
 * DB 구조(works → work_versions → work_files)에 맞춘 규칙 (11-06):
 * - 제목·설명은 works가 아니라 work_versions에, 이미지(1~5장)는 버전에 붙는다.
 * - works.display_version_id = 지금 화면에 보여 줄 버전 (같은 작품의 버전만 연결 가능).
 * - 최종 과제는 수강당 1개(works.enrollment_id UNIQUE). 취미·수준은 강의 값과 같아야 한다.
 * - 평가 회차(review_rounds.work_version_id)는 버전 1개에만 연결된다(UNIQUE).
 * - 승인된 과제의 수정·삭제(D-63)는 [미정] — 구현하지 않는다.
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class WorkService {

    /**
     * 계약 C8: 최종 과제 제출 버전 저장 (호출: 팀원 3 AssignmentService — 최초 제출·반려 후 재제출)
     * 작품(FINAL)이 없으면 만들고, 새 버전 + 이미지를 저장한 뒤 표시본을 새 버전으로 바꾼다.
     * 하나라도 실패하면 저장한 파일을 지우고 예외(트랜잭션 롤백).
     *
     * @return 새로 만든 work_versions.id — 호출한 쪽이 review_rounds.work_version_id 에 넣는다
     */
    public Long saveFinalWorkVersion(Long userId, Long enrollmentId, Long hobbyId, CourseLevel level,
                                     String title, String description, List<MultipartFile> images) {
        throw new UnsupportedOperationException("TODO #16");
    }

    /**
     * 계약 C8: 평가 시작 전 수정 — 같은 버전을 그대로 고친다(새 버전을 만들지 않음 → 평가 회차 연결·마감 유지)
     * images 가 null 이거나 비어 있으면 기존 이미지 유지, 있으면 전체 교체.
     */
    public void updateFinalWorkVersion(Long userId, Long workVersionId, String title, String description,
                                       List<MultipartFile> images) {
        throw new UnsupportedOperationException("TODO #16");
    }
}
