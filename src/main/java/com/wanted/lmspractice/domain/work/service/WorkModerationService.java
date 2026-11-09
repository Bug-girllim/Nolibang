package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.domain.work.dto.ModerationLogDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationSummaryDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationViewDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.ModerationAction;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkModerationLog;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.repository.WorkBoardQuery;
import com.wanted.lmspractice.domain.work.repository.WorkLikeRepository;
import com.wanted.lmspractice.domain.work.repository.WorkModerationLogRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자의 공개 작품 숨김·복구와 조치 이력 조회 — 숨김은 일반 노출만 막고 수료·배지·평가 의무에는 영향이 없다
 * [담당] 팀원 4 · #20   [화면] A-S08, A-S14, U-S16   [API] AW-01·02·03, MW-08   [기획] FR-036, Q-01·Q-02
 * [연결]
 *  - 호출받음: AdminWorkController(숨김·복구·이력·선택 패널), MyWorkController(작성자 사유 확인)
 *  - 호출함:   Work.hide/restore(상태 전이 규칙), WorkModerationLogRepository(이력), NotificationService.notify(C7, 같은 트랜잭션 안),
 *              WorkBoardQuery(요약 숫자), UserService.getPublicNames·HobbyService(표시 값)
 *  - 영향:     WorkAccessPolicy 가 works.admin_hidden 을 읽어 일반 사용자 목록·상세·이미지를 막고 작성자·담당 강사·관리자는 계속 열람한다.
 * [규칙] 숨김은 공개 작품만(비공개 → INVALID_INPUT), 이미 숨김이면 ALREADY_HIDDEN, 복구는 숨김 상태만(NOT_HIDDEN).
 *        복구해도 작성자가 비공개로 바꿨다면 공개되지 않는다(visibility 는 건드리지 않음). 작성자는 숨김을 해제할 수 없다(관리자 전용 URL).
 * [기술] 🟢 Entity 상태 메서드, 🟢 @Transactional
 */
@Service
@RequiredArgsConstructor
public class WorkModerationService {

    /** ❓ 임시값: 숨김 사유 최소 5자 (API §16 #16 "최소 길이 미정" — #20에서 확정 필요). 최대는 DB 칼럼 1000자 */
    public static final int MIN_HIDE_REASON_LENGTH = 5;
    public static final int MAX_REASON_LENGTH = 1000;
    /** 복구 사유를 비워 보냈을 때 이력·작성자 화면에 남기는 기본 문구 (API AW-03 사유는 선택) */
    public static final String DEFAULT_RESTORE_REASON = "관리자가 공개 노출 제한을 해제했습니다.";

    private final WorkRepository workRepository;
    private final WorkVersionRepository workVersionRepository;
    private final WorkLikeRepository workLikeRepository;
    private final WorkModerationLogRepository logRepository;
    private final WorkBoardQuery workBoardQuery;
    private final NotificationService notificationService;
    private final UserService userService;
    private final HobbyService hobbyService;

    /**
     * 공개 작품 숨김 (AW-02) — 작품 상태 변경 → 이력 저장 → 작성자에게 WORK_HIDDEN 알림 (한 트랜잭션)
     * 호출: AdminWorkController.hide
     * @throws BusinessException WORK_NOT_FOUND, ALREADY_HIDDEN, INVALID_INPUT(사유 없음·너무 짧거나 긺·비공개 작품)
     */
    @Transactional
    public void hide(Long adminId, Long workId, String reason) {
        String cleaned = reason == null ? "" : reason.strip();
        if (cleaned.length() < MIN_HIDE_REASON_LENGTH || cleaned.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Work work = findWork(workId);
        work.hide(adminId, cleaned, LocalDateTime.now());
        WorkModerationLog log = logRepository.save(WorkModerationLog.record(workId, ModerationAction.HIDE, cleaned, adminId));
        // 같은 작품을 숨김·복구·재숨김 할 수 있으므로 이력 id 를 넣어 알림 중복 방지 키를 만든다
        notificationService.notify(work.getAuthorId(), NotificationKind.WORK_HIDDEN, workId, "작품 숨김",
                "「" + titleOf(work) + "」의 공개 노출이 제한되었습니다.", "WORK_HIDDEN:" + workId + ":" + log.getId());
    }

    /** 숨김 복구 (AW-03) — 사유 없이 호출하는 형태. 기본 문구를 이력에 남긴다 */
    @Transactional
    public void restore(Long adminId, Long workId) {
        restore(adminId, workId, null);
    }

    /**
     * 숨김 복구 (AW-03) — 복구 사유는 선택(비우면 기본 문구). 사유는 작성자 화면에 표시된다(F-10 ⑤).
     * 복구해도 작성자가 비공개로 바꿨다면 공개되지 않는다.
     * @throws BusinessException WORK_NOT_FOUND, NOT_HIDDEN, INVALID_INPUT(사유가 너무 긺)
     */
    @Transactional
    public void restore(Long adminId, Long workId, String reason) {
        String cleaned = reason == null || reason.isBlank() ? DEFAULT_RESTORE_REASON : reason.strip();
        if (cleaned.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Work work = findWork(workId);
        work.restore();
        WorkModerationLog log = logRepository.save(WorkModerationLog.record(workId, ModerationAction.RESTORE, cleaned, adminId));
        notificationService.notify(work.getAuthorId(), NotificationKind.WORK_RESTORED, workId, "작품 복구",
                "「" + titleOf(work) + "」의 공개 노출 제한이 해제되었습니다.", "WORK_RESTORED:" + workId + ":" + log.getId());
    }

    /** 관리자 화면 머리 숫자 — 공개 작품 전체·게시 중·숨김·최종 과제·일반 작품 */
    @Transactional(readOnly = true)
    public ModerationSummaryDTO getSummary(Long adminId) {
        return new ModerationSummaryDTO(
                countAdmin(adminId, null, null),
                countAdmin(adminId, false, null),
                countAdmin(adminId, true, null),
                countAdmin(adminId, null, WorkKind.FINAL),
                countAdmin(adminId, null, WorkKind.GENERAL));
    }

    /** 전체 조치 이력 — 최신순 (A-S08 "조치 이력" 탭) */
    @Transactional(readOnly = true)
    public List<ModerationLogDTO> getAllHistory() {
        return toLogDtos(logRepository.findAllByOrderByIdDesc());
    }

    /**
     * 관리자가 선택한 작품 한 건의 요약 + 이력 (A-S08 선택 패널)
     * @throws BusinessException WORK_NOT_FOUND (비공개 작품은 관리 대상이 아니라 목록에 없지만 이력 확인은 허용한다)
     */
    @Transactional(readOnly = true)
    public ModerationViewDTO getTarget(Long workId) {
        return buildView(findWork(workId));
    }

    /**
     * 작성자 본인의 조치 사유 확인 (MW-08, U-S16) — 본인 작품이 아니면 존재를 숨기고 WORK_NOT_FOUND
     * 숨김 중에도 작성자는 작품 원문을 열람할 수 있다(Q-02, WorkAccessPolicy).
     */
    @Transactional(readOnly = true)
    public ModerationViewDTO getAuthorView(Long userId, Long workId) {
        Work work = workRepository.findById(workId)
                .filter(w -> w.isAuthor(userId) && !w.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        return buildView(work);
    }

    private ModerationViewDTO buildView(Work work) {
        WorkVersion version = workVersionRepository.findById(work.getDisplayVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        WorkDTO dto = WorkDTO.of(work, version, List.of());
        dto.setAuthorNickname(userService.getPublicNames(Set.of(work.getAuthorId())).get(work.getAuthorId()));
        HobbyDTO hobby = hobbyService.getHobbiesIgnoringVisibility(List.of(work.getHobbyId())).get(work.getHobbyId());
        dto.setHobbyName(hobby == null ? "" : hobby.getName());
        dto.setLikeCount(workLikeRepository.countByWorkId(work.getId()));
        dto.setHidden(work.isAdminHidden());
        dto.setHiddenReason(work.getHiddenReason());
        return new ModerationViewDTO(dto, toLogDtos(logRepository.findByWorkIdOrderByIdDesc(work.getId())));
    }

    private List<ModerationLogDTO> toLogDtos(List<WorkModerationLog> logs) {
        Map<Long, Work> works = workRepository.findAllById(logs.stream().map(WorkModerationLog::getWorkId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Work::getId, w -> w));
        Map<Long, String> titles = new HashMap<>();
        works.values().forEach(w -> titles.put(w.getId(), titleOf(w)));
        return logs.stream().map(log -> ModerationLogDTO.of(log, titles.getOrDefault(log.getWorkId(), ""))).toList();
    }

    private long countAdmin(Long adminId, Boolean hidden, WorkKind kind) {
        WorkSearchDTO cond = new WorkSearchDTO();
        cond.setHidden(hidden);
        cond.setKind(kind);
        return workBoardQuery.countForAdmin(cond, null, adminId);
    }

    private Work findWork(Long workId) {
        return workRepository.findById(workId)
                .filter(work -> !work.isDeleted() && work.getDisplayVersionId() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
    }

    private String titleOf(Work work) {
        return workVersionRepository.findById(work.getDisplayVersionId()).map(WorkVersion::getTitle).orElse("작품");
    }
}
