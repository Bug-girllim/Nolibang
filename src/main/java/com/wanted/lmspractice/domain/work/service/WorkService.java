package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFileDTO;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkFile;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.policy.WorkAccessPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkFileRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import com.wanted.lmspractice.global.file.StoredFile;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 작품 저장·수정(최종 과제)과 이미지·작품 열람 — 파일 저장은 FileStorage, 열람 권한은 WorkAccessPolicy
 * [담당] 팀원 4 · #16 (#19 일반 작품 저장은 이 서비스에 이어서 추가)   [기획] FR-022, D-18·D-22, NFR-01·05
 * [연결]
 *  - 호출받음: AssignmentService(#13, 계약 C8 저장·수정), WorkFileController(이미지 출력), #18·#14 화면(getWork)
 *  - 호출함:   EnrollmentService.getOwnedEnrollment(본인 수강 확인), FileStorage, WorkAccessPolicy, Work·WorkVersion·WorkFile Repository
 *  - 계약:     C8 saveFinalWorkVersion / updateFinalWorkVersion — 시그니처 변경 금지
 * [DB 구조] works → work_versions(제목·설명) → work_files(이미지 1~5장). works.display_version_id = 지금 보여 줄 버전.
 * [원칙] DB와 파일은 같은 트랜잭션이 아니다(DEV_REVIEW §2-10). 그래서 "파일 먼저 저장 → DB 저장, DB가 실패하면 저장한 파일 삭제"
 *        순서를 지키고, 수정 때는 "새 파일 저장 성공 → DB 교체 → 이전 파일 삭제" 순서로 한다.
 * [범위 밖] 승인된 과제의 수정·삭제(D-63)는 미정 — 구현하지 않는다. "평가 전인지"(SUBMISSION_LOCKED) 판단은 #13 SubmissionPolicy 가 호출 전에 한다.
 * [기술] 🟢 @Transactional, 🟢 MultipartFile
 */
@Service
@RequiredArgsConstructor
public class WorkService {

    public static final int MIN_IMAGES = 1;   // D-18
    public static final int MAX_IMAGES = 5;   // D-18·D-22
    private static final int MAX_TITLE_LENGTH = 200;   // work_versions.title VARCHAR(200)

    private final WorkRepository workRepository;
    private final WorkVersionRepository workVersionRepository;
    private final WorkFileRepository workFileRepository;
    private final FileStorage fileStorage;
    private final WorkAccessPolicy workAccessPolicy;
    private final EnrollmentService enrollmentService;

    /**
     * 계약 C8: 최종 과제 제출 버전 저장 (호출: AssignmentService — 최초 제출·반려 후 재제출)
     * 흐름: 입력 검증 → 본인 수강 확인 → 이미지 전부 저장 → (수강당 최초면) 작품 생성 → 버전(version_no+1)·이미지 연결 → 표시본을 새 버전으로
     * 하나라도 실패하면 저장한 파일을 지우고 예외를 던진다(트랜잭션도 롤백).
     * 취미·수준은 최초 작품 생성 때만 쓰고, 재제출에서는 기존 작품 값을 유지한다.
     *
     * @return 새로 만든 work_versions.id — 호출한 쪽이 review_rounds.work_version_id 에 넣는다
     * @throws BusinessException ENROLLMENT_NOT_FOUND(남의 수강), INVALID_INPUT(제목), INVALID_IMAGE_COUNT, INVALID_IMAGE_FILE, FILE_TOO_LARGE, FILE_SAVE_FAILED
     */
    @Transactional
    public Long saveFinalWorkVersion(Long userId, Long enrollmentId, Long hobbyId, CourseLevel level,
                                     String title, String description, List<MultipartFile> images) {
        validateTitle(title);
        validateImageCount(images);
        enrollmentService.getOwnedEnrollment(userId, enrollmentId);   // 남의 수강이면 존재를 숨기고 404

        List<StoredFile> stored = fileStorage.storeAll(images);       // 파일이 먼저 — 실패하면 storeAll 이 정리하고 던진다
        try {
            Work work = workRepository.findByEnrollmentId(enrollmentId)
                    .orElseGet(() -> workRepository.save(Work.createFinal(userId, enrollmentId, hobbyId, level)));
            int nextNo = workVersionRepository.findTopByWorkIdOrderByVersionNoDesc(work.getId())
                    .map(v -> v.getVersionNo() + 1).orElse(1);
            WorkVersion version = workVersionRepository.save(WorkVersion.create(work.getId(), nextNo, title.strip(), description));
            saveFiles(version.getId(), stored);
            work.display(version.getId());
            return version.getId();
        } catch (RuntimeException e) {
            fileStorage.deleteAllQuietly(stored);                     // DB 쪽 실패 — 방금 저장한 파일을 남기지 않는다(F-05)
            throw e;
        }
    }

    /**
     * 계약 C8: 평가 시작 전 수정 — 같은 버전의 제목·설명을 고친다(새 버전을 만들지 않음 → 평가 회차 연결·마감 유지).
     * images 가 null 이거나 비어 있으면 기존 이미지를 유지하고, 있으면 전체 교체한다.
     * 교체 순서: 새 파일 저장 성공 → DB의 이미지 행 교체 → 이전 파일 삭제. 새 파일 저장이 실패하면 이전 이미지는 그대로다.
     *
     * @throws BusinessException WORK_NOT_FOUND(없거나 남의 작품 — 존재를 숨김), INVALID_INPUT, INVALID_IMAGE_COUNT·INVALID_IMAGE_FILE·FILE_TOO_LARGE·FILE_SAVE_FAILED
     */
    @Transactional
    public void updateFinalWorkVersion(Long userId, Long workVersionId, String title, String description,
                                       List<MultipartFile> images) {
        validateTitle(title);
        WorkVersion version = workVersionRepository.findById(workVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        Work work = workRepository.findById(version.getWorkId())
                .filter(w -> w.isAuthor(userId) && w.getKind() == WorkKind.FINAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));

        boolean replaceImages = images != null && !images.isEmpty();
        if (replaceImages) {
            validateImageCount(images);
        }
        List<StoredFile> stored = replaceImages ? fileStorage.storeAll(images) : List.of();
        List<String> oldKeys = new ArrayList<>();
        try {
            version.edit(title.strip(), description);
            if (replaceImages) {
                List<WorkFile> old = workFileRepository.findByWorkVersionIdOrderBySortOrder(version.getId());
                old.forEach(f -> oldKeys.add(f.getOriginalKey()));
                workFileRepository.deleteByWorkVersionId(version.getId());
                workFileRepository.flush();                           // uq_files_order(버전, 순서) — 이전 행이 먼저 지워져야 같은 순서로 넣을 수 있다
                saveFiles(version.getId(), stored);
            }
        } catch (RuntimeException e) {
            fileStorage.deleteAllQuietly(stored);                     // 교체 실패 — 새로 저장한 파일을 정리하고 이전 파일은 건드리지 않는다
            throw e;
        }
        oldKeys.forEach(fileStorage::deleteQuietly);                  // 마지막에 이전 파일 삭제
    }

    /**
     * 작품 1건 조회 — 지금 보여 줄 버전(display_version_id)의 제목·설명·이미지 id
     * 호출: #18 작품 상세, #13·#14 제출·평가 화면
     * @throws BusinessException WORK_NOT_FOUND(없음·삭제·보여 줄 버전 없음), WORK_ACCESS_DENIED(열람 권한 없음)
     */
    @Transactional(readOnly = true)
    public WorkDTO getWork(Long workId, Long viewerId, Role viewerRole) {
        Work work = workRepository.findById(workId)
                .filter(w -> !w.isDeleted() && w.getDisplayVersionId() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        workAccessPolicy.checkCanView(work, viewerId, viewerRole);
        WorkVersion version = workVersionRepository.findById(work.getDisplayVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        return WorkDTO.of(work, version, workFileRepository.findByWorkVersionIdOrderBySortOrder(version.getId()));
    }

    /**
     * 이미지 출력 준비 — 권한을 확인하고 실제 파일 경로·MIME을 돌려준다 (API FL-01)
     * 호출: WorkFileController.image
     * @throws BusinessException NOT_FOUND(이미지 없음·파일 없음), WORK_ACCESS_DENIED(403 — 비공개·숨김 작품을 열람 권한 없이 요청)
     */
    @Transactional(readOnly = true)
    public WorkFileDTO openFile(Long fileId, Long viewerId, Role viewerRole) {
        WorkFile file = workFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        WorkVersion version = workVersionRepository.findById(file.getWorkVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Work work = workRepository.findById(version.getWorkId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        workAccessPolicy.checkCanView(work, viewerId, viewerRole);    // 권한 확인이 파일 접근보다 먼저
        return new WorkFileDTO(fileStorage.load(file.getOriginalKey()), file.getMimeType(), file.getStoredSizeBytes());
    }

    // 저장된 파일을 올린 순서대로 sort_order 1..N 으로 연결
    private void saveFiles(Long versionId, List<StoredFile> stored) {
        for (int i = 0; i < stored.size(); i++) {
            workFileRepository.save(WorkFile.create(versionId, i + 1, stored.get(i)));
        }
    }

    // D-18: 이미지 1~5장 (디스크에 쓰기 전에 거른다)
    private void validateImageCount(List<MultipartFile> images) {
        if (images == null || images.size() < MIN_IMAGES || images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_COUNT);
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.strip().length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
