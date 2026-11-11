package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.review.dto.FeedbackRefDTO;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFileDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFormDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFormOptionsDTO;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkFile;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.policy.WorkAccessPolicy;
import com.wanted.lmspractice.domain.work.policy.WorkLevelPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkFileRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import com.wanted.lmspractice.global.file.StoredFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 작품 저장·수정(최종 과제·일반 작품)과 이미지·작품 열람 — 파일 저장은 FileStorage, 열람 권한은 WorkAccessPolicy
 * [담당] 팀원 4 · #16, #19(일반 작품 등록·수정·공개 설정)   [기획] FR-022·027, D-17·D-18·D-22·D-29, NFR-01·05
 * [연결]
 *  - 호출받음: AssignmentService(#13, 계약 C8 저장·수정), WorkFileController(이미지 출력), #18·#14 화면(getWork),
 *             MyWorkController(#19, MW-01~05)
 *  - 호출함:   EnrollmentService.getOwnedEnrollment(본인 수강 확인), FileStorage, WorkAccessPolicy, Work·WorkVersion·WorkFile Repository,
 *             (#19) HobbyService(운영 중 취미), WorkLevelPolicy(수준 태그 ← BadgeService C12), ReviewQueryService.findLatestFeedbackForReference(C11)
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
    private static final int FIRST_VERSION_NO = 1;     // 일반 작품은 버전 1개(이전 시도 보관은 보류)

    private final WorkRepository workRepository;
    private final WorkVersionRepository workVersionRepository;
    private final WorkFileRepository workFileRepository;
    private final FileStorage fileStorage;
    private final WorkAccessPolicy workAccessPolicy;
    private final WorkLevelPolicy workLevelPolicy;
    private final EnrollmentService enrollmentService;
    private final HobbyService hobbyService;
    private final ReviewQueryService reviewQueryService;

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
        deleteOnRollback(stored);                                     // 호출한 쪽(AssignmentService)의 뒤 단계가 실패해도 파일을 남기지 않는다
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

        editVersion(version, title, description, images);
    }

    // ───────────────────────── #19 일반 작품 ─────────────────────────

    /**
     * 일반 작품 등록 (FR-027, API MW-02) — 수강·평가와 무관하므로 수료 상태에 영향이 없다.
     * 흐름: 입력 검증 → 운영 중 취미(D-29) → 수준 태그(WorkLevelPolicy) → 참고 피드백 본인 여부(D-17)
     *       → 이미지 저장 → 작품·버전 1·이미지 연결 → 표시본 지정. DB가 실패하면 저장한 파일을 지운다(최종 과제 저장과 같은 순서).
     * 호출: MyWorkController.create (POST /my/works)
     *
     * @param images 빈 파일 칸(선택 안 함)은 무시하고 1~5장이어야 한다
     * @return 새 works.id
     * @throws BusinessException INVALID_INPUT(제목·회고), HOBBY_NOT_FOUND, HOBBY_NOT_OPERATING, LEVEL_TAG_NOT_ALLOWED,
     *         ACCESS_DENIED(내 최근 공식 피드백이 아닌 것을 연결), INVALID_IMAGE_COUNT·INVALID_IMAGE_FILE·FILE_TOO_LARGE·FILE_SAVE_FAILED
     */
    @Transactional
    public Long saveGeneralWork(Long userId, WorkFormDTO dto, List<MultipartFile> images) {
        List<MultipartFile> files = selectedFiles(images);
        validateTitle(dto.getTitle());
        validateImageCount(files);
        checkGeneralWorkRules(userId, dto, null);

        List<StoredFile> stored = fileStorage.storeAll(files);       // 파일이 먼저 — 실패하면 storeAll 이 정리하고 던진다
        deleteOnRollback(stored);
        try {
            Work work = workRepository.save(Work.createGeneral(userId, dto.getHobbyId(), dto.getLevel(),
                    dto.getVisibility(), dto.getReflection(), dto.getReferenceReviewId(), LocalDateTime.now()));
            WorkVersion version = workVersionRepository.save(
                    WorkVersion.create(work.getId(), FIRST_VERSION_NO, dto.getTitle().strip(), dto.getDescription()));
            saveFiles(version.getId(), stored);
            work.display(version.getId());
            return work.getId();
        } catch (RuntimeException e) {
            fileStorage.deleteAllQuietly(stored);                     // DB 쪽 실패 — 방금 저장한 파일을 남기지 않는다(F-05)
            throw e;
        }
    }

    /**
     * 일반 작품 수정 화면의 기존 값 (API MW-03)
     * 호출: MyWorkController.editForm (GET /my/works/{workId}/edit)
     * @throws BusinessException WORK_NOT_FOUND (없음·남의 작품·최종 과제 — 최종 과제는 AS-03에서 수정)
     */
    @Transactional(readOnly = true)
    public WorkFormDTO getGeneralWorkForm(Long userId, Long workId) {
        Work work = getOwnedGeneralWork(userId, workId);
        return WorkFormDTO.from(work, getDisplayVersion(work));
    }

    /**
     * 일반 작품 수정 (API MW-04) — 지금 보여 줄 버전을 그대로 고친다(이전 시도 보관은 이번 범위 밖).
     * 수준 태그는 수정 시점의 배지로 다시 검사한다. created_at 이 바뀌지 않아 타임라인 순서가 유지되고, 관리자 숨김은 풀리지 않는다.
     * images 를 하나도 고르지 않으면 기존 이미지를 유지하고, 고르면 전체 교체한다(최종 과제 수정과 같은 순서).
     * 호출: MyWorkController.edit (POST /my/works/{workId}/edit)
     * @throws BusinessException WORK_NOT_FOUND + saveGeneralWork 와 같은 예외
     */
    @Transactional
    public void updateGeneralWork(Long userId, Long workId, WorkFormDTO dto, List<MultipartFile> images) {
        Work work = getOwnedGeneralWork(userId, workId);
        validateTitle(dto.getTitle());
        checkGeneralWorkRules(userId, dto, work);

        work.editGeneral(dto.getHobbyId(), dto.getLevel(), dto.getVisibility(), dto.getReflection(),
                dto.getReferenceReviewId(), LocalDateTime.now());
        editVersion(getDisplayVersion(work), dto.getTitle(), dto.getDescription(), selectedFiles(images));
    }

    /**
     * 공개 설정 변경 (API MW-05) — 일반 작품·최종 과제 모두 작성자 본인만.
     * 관리자 숨김 중이면 공개로 바꿔도 노출되지 않는다(Work.isPubliclyVisible).
     * 호출: MyWorkController.changeVisibility (POST /my/works/{workId}/visibility)
     * @throws BusinessException WORK_NOT_FOUND (없음·남의 작품 — 존재를 숨김)
     */
    /**
     * 일반 작품 삭제 (본인만, 논리 삭제) — 댓글·좋아요·파일 행은 남기되 작품이 보이지 않으니 함께 접근할 수 없다.
     * 호출: MyWorkController.delete (POST /my/works/{id}/delete)
     * 남의 작품·최종 과제·이미 삭제된 작품이면 존재를 숨기고 WORK_NOT_FOUND(404)
     */
    @Transactional
    public void deleteGeneralWork(Long userId, Long workId) {
        getOwnedGeneralWork(userId, workId).delete(LocalDateTime.now());
    }

    @Transactional
    public void changeVisibility(Long userId, Long workId, Visibility visibility) {
        Work work = workRepository.findById(workId)
                .filter(w -> w.isOwnedActiveBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        work.changeVisibility(visibility, LocalDateTime.now());
    }

    /**
     * 일반 작품 폼 선택지 (API MW-01 모델 hobbies·allowedLevels·reference)
     * 호출: MyWorkController (등록·수정 화면, 입력 오류로 다시 그릴 때)
     * @param hobbyId 폼에서 고른 취미 — 없으면 수준은 입문만, 참고 피드백은 없음
     */
    @Transactional(readOnly = true)
    public WorkFormOptionsDTO getFormOptions(Long userId, Long hobbyId) {
        List<HobbyDTO> operating = hobbyService.getCategories(Role.USER).stream()
                .flatMap(category -> category.getHobbies().stream())
                .filter(HobbyDTO::isOperating)                        // D-29: 운영 중 취미만
                .toList();
        FeedbackRefDTO reference = hobbyId == null ? null
                : reviewQueryService.findLatestFeedbackForReference(userId, hobbyId).orElse(null);
        return new WorkFormOptionsDTO(operating, workLevelPolicy.allowedLevels(userId, hobbyId), reference);
    }

    // 일반 작품 등록·수정 공통 업무 규칙. current 는 수정 중인 작품(등록이면 null)
    private void checkGeneralWorkRules(Long userId, WorkFormDTO dto, Work current) {
        checkOperatingHobby(dto.getHobbyId());
        workLevelPolicy.checkAllowed(userId, dto.getHobbyId(), dto.getLevel());
        checkReference(userId, dto.getHobbyId(), dto.getReferenceReviewId(), current);
    }

    // D-29: 운영 중인 세부 취미만. 학습자에게 보이지 않는(비공개) 취미는 HobbyService 가 HOBBY_NOT_FOUND 를 던진다
    private void checkOperatingHobby(Long hobbyId) {
        if (hobbyId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!hobbyService.getHobbySummary(hobbyId, Role.USER).isOperating()) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_OPERATING);
        }
    }

    /**
     * D-17·D-47: 참고 피드백은 "내 같은 취미의 최근 공식 피드백 1건"만 연결할 수 있다.
     * 수정 때는 이미 연결해 둔 피드백을 그대로 두는 것도 허용한다(취미를 바꾸지 않은 경우만 — 그 사이 더 최근 피드백이 생겨도 유지).
     * 그 밖의 id(남의 피드백·다른 취미·예전 피드백)는 ACCESS_DENIED (API MW-02).
     */
    private void checkReference(Long userId, Long hobbyId, Long referenceReviewId, Work current) {
        if (referenceReviewId == null) {
            return;
        }
        boolean keepsCurrent = current != null
                && referenceReviewId.equals(current.getReferenceReviewId())
                && hobbyId.equals(current.getHobbyId());
        if (keepsCurrent) {
            return;
        }
        boolean isLatestOwn = reviewQueryService.findLatestFeedbackForReference(userId, hobbyId)
                .map(ref -> referenceReviewId.equals(ref.getReviewRoundId()))
                .orElse(false);
        if (!isLatestOwn) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    // 본인 일반 작품만 — 없거나 남의 것·최종 과제·삭제된 작품이면 존재를 숨기고 404
    private Work getOwnedGeneralWork(Long userId, Long workId) {
        return workRepository.findById(workId)
                .filter(w -> w.isOwnedActiveBy(userId) && w.isGeneral())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
    }

    private WorkVersion getDisplayVersion(Work work) {
        if (work.getDisplayVersionId() == null) {
            throw new BusinessException(ErrorCode.WORK_NOT_FOUND);
        }
        return workVersionRepository.findById(work.getDisplayVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
    }

    // 파일 칸에서 아무것도 고르지 않으면 브라우저가 빈 파일 1개를 보낸다 — 실제로 고른 파일만 남긴다
    private List<MultipartFile> selectedFiles(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    // ───────────────────────── 공통 ─────────────────────────

    /**
     * 같은 버전의 제목·설명 수정 + (이미지를 골랐으면) 이미지 전체 교체. 최종 과제 수정(C8)과 일반 작품 수정(MW-04)이 함께 쓴다.
     * 교체 순서: 새 파일 저장 성공 → DB의 이미지 행 교체 → 이전 파일 삭제. 새 파일 저장이 실패하면 이전 이미지는 그대로다.
     */
    private void editVersion(WorkVersion version, String title, String description, List<MultipartFile> images) {
        boolean replaceImages = images != null && !images.isEmpty();
        if (replaceImages) {
            validateImageCount(images);
        }
        List<StoredFile> stored = replaceImages ? fileStorage.storeAll(images) : List.of();
        deleteOnRollback(stored);
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
        // 이전 파일은 커밋이 끝난 뒤에 지운다 — 커밋이 실패하면 DB는 옛 행 그대로이므로 파일이 남아 있어야 한다
        runAfterCommit(() -> oldKeys.forEach(fileStorage::deleteQuietly));
    }

    /** 방금 저장한 파일을 트랜잭션이 롤백으로 끝나면 지운다. 트랜잭션이 없으면 등록하지 않는다(호출한 쪽 catch 가 처리) */
    private void deleteOnRollback(List<StoredFile> stored) {
        if (stored.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    fileStorage.deleteAllQuietly(stored);
                }
            }
        });
    }

    /** 커밋 뒤에 실행. 트랜잭션이 없으면 바로 실행한다 */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
     * 내 수강 건의 최종 과제 — 지금 표시 중인 제출본(제목·설명·이미지). 아직 제출 전이거나 삭제됐으면 비어 있다.
     * 호출: AssignmentService.getAssignmentView (최종 과제 화면의 "제출한 과제" 칸). 수강 건 소유 확인은 호출한 쪽이 이미 끝냈다.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<WorkDTO> findFinalWork(Long userId, Long enrollmentId) {
        return workRepository.findByEnrollmentId(enrollmentId)
                .filter(w -> w.isOwnedActiveBy(userId) && w.getKind() == WorkKind.FINAL && w.getDisplayVersionId() != null)
                .flatMap(work -> workVersionRepository.findById(work.getDisplayVersionId())
                        .map(version -> WorkDTO.of(work, version,
                                workFileRepository.findByWorkVersionIdOrderBySortOrder(version.getId()))));
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
        return new WorkFileDTO(file.getOriginalKey(), file.getMimeType(), file.getStoredSizeBytes());
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
