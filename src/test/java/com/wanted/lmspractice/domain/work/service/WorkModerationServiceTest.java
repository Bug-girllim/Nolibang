package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.ModerationLogDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationSummaryDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationViewDTO;
import com.wanted.lmspractice.domain.work.dto.TimelineDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.ModerationAction;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] WorkModerationService 통합 테스트 — 숨김·복구 규칙, 이력, 알림, 숨김 작품의 노출 차단과 작성자 열람, 복구 후 공개 여부
 * [담당] 팀원 4 · #20   [전제] 로컬 MySQL(nolibang)에 schema.sql(H5 work_moderation_logs 포함)·seed.sql 적재. 변경은 테스트 트랜잭션이 롤백한다.
 *       시드: 작품 2(공개·일반 작품·작성자 learnerB), 작품 6(공개 + 관리자 숨김 + 이력 1건, 일반 작품, 작성자 learnerB),
 *             작품 3(learnerB 비공개 최종 과제). 공개 작품은 1(최종 과제)·2·4·5·6(일반 작품) 5개 — 이 중 숨김은 6뿐. 관리자 admin.
 *             다른 학습자 시점은 learnerC 로 본다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용), ReflectionTestUtils (작성자의 비공개 전환 메서드가 #19 소관이라 필드를 직접 세팅)
 */
@SpringBootTest
@Transactional
class WorkModerationServiceTest {

    private static final Long VISIBLE_WORK = 2L, HIDDEN_WORK = 6L, PRIVATE_WORK = 3L;
    private static final String REASON = "타인의 얼굴이 식별되는 사진이 포함되어 있습니다.";

    @Autowired
    private WorkModerationService moderationService;
    @Autowired
    private WorkBoardService workBoardService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private WorkRepository workRepository;
    @Autowired
    private WorkService workService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GrowthService growthService;

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    private List<Long> boardIds(String loginId) {
        return workBoardService.search(new WorkSearchDTO(), userId(loginId), Role.USER).getItems()
                .stream().map(WorkDTO::getWorkId).toList();
    }

    @Test
    @DisplayName("숨김: 일반 게시판·상세에서 사라지고 작성자·관리자는 계속 열람하며, 이력 1건과 작성자 알림 1건이 생긴다")
    void hide() {
        Long admin = userId("admin"), author = userId("learnerB");
        long unreadBefore = notificationService.countUnread(author);

        moderationService.hide(admin, Role.ADMIN, VISIBLE_WORK, REASON);

        assertThat(boardIds("learnerC")).doesNotContain(VISIBLE_WORK);
        assertThatThrownBy(() -> workBoardService.getDetail(VISIBLE_WORK, userId("learnerC"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
        assertThat(workBoardService.getDetail(VISIBLE_WORK, author, Role.USER).isOwner()).isTrue();   // 작성자 열람 유지(Q-02)
        assertThat(workBoardService.getDetail(VISIBLE_WORK, admin, Role.ADMIN)).isNotNull();

        ModerationViewDTO view = moderationService.getAuthorView(author, VISIBLE_WORK);
        assertThat(view.getWork().isHidden()).isTrue();
        assertThat(view.getWork().getHiddenReason()).isEqualTo(REASON);
        assertThat(view.getLogs()).hasSize(1);
        assertThat(view.getLatest().getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(view.getLatest().getReason()).isEqualTo(REASON);
        assertThat(notificationService.countUnread(author)).isEqualTo(unreadBefore + 1);
    }

    @Test
    @DisplayName("숨김 거부: 이미 숨김·비공개 작품·사유 없음/너무 짧음/너무 김·없는 작품")
    void hideRejections() {
        Long admin = userId("admin");

        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, HIDDEN_WORK, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_HIDDEN));
        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, PRIVATE_WORK, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, VISIBLE_WORK, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, VISIBLE_WORK, "  짧음  "))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, VISIBLE_WORK, "가".repeat(1001)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, Role.ADMIN, 999999L, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThat(workRepository.findById(VISIBLE_WORK).orElseThrow().isAdminHidden()).isFalse();   // 거부된 요청은 상태를 바꾸지 않는다
    }

    @Test
    @DisplayName("권한: 관리자가 아니면(학습자·강사·역할 없음·id 없음) 숨김·복구 모두 ACCESS_DENIED, 상태·이력은 그대로")
    void moderationRequiresAdmin() {
        Long author = userId("learnerB");
        int logsBefore = moderationService.getAllHistory().size();
        boolean visibleHiddenBefore = workRepository.findById(VISIBLE_WORK).orElseThrow().isAdminHidden();
        boolean hiddenHiddenBefore = workRepository.findById(HIDDEN_WORK).orElseThrow().isAdminHidden();

        for (Role role : new Role[]{Role.USER, Role.INSTRUCTOR, null}) {
            assertThatThrownBy(() -> moderationService.hide(author, role, VISIBLE_WORK, REASON))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));
            assertThatThrownBy(() -> moderationService.restore(author, role, HIDDEN_WORK, null))   // 작성자 본인도 숨김을 풀 수 없다
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));
        }
        assertThatThrownBy(() -> moderationService.hide(null, Role.ADMIN, VISIBLE_WORK, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));

        // 권한 검사가 작품 조회·상태 변경보다 먼저라 거부된 요청은 아무것도 바꾸지 않는다
        assertThat(workRepository.findById(VISIBLE_WORK).orElseThrow().isAdminHidden()).isEqualTo(visibleHiddenBefore);
        assertThat(workRepository.findById(HIDDEN_WORK).orElseThrow().isAdminHidden()).isEqualTo(hiddenHiddenBefore);
        assertThat(moderationService.getAllHistory()).hasSize(logsBefore);
    }

    @Test
    @DisplayName("복구: 게시판에 다시 보이고 복구 이력·작성자 알림이 생기며, 사유를 비우면 기본 문구가 남는다")
    void restore() {
        Long admin = userId("admin"), author = userId("learnerB");
        long unreadBefore = notificationService.countUnread(author);

        moderationService.restore(admin, Role.ADMIN, HIDDEN_WORK);

        assertThat(boardIds("learnerC")).contains(HIDDEN_WORK);
        assertThat(workBoardService.getDetail(HIDDEN_WORK, userId("learnerC"), Role.USER)).isNotNull();
        ModerationViewDTO view = moderationService.getAuthorView(author, HIDDEN_WORK);
        assertThat(view.getWork().isHidden()).isFalse();
        assertThat(view.getLogs()).extracting(ModerationLogDTO::getAction)
                .containsExactly(ModerationAction.RESTORE, ModerationAction.HIDE);   // 최신순: 복구 → 시드의 숨김
        assertThat(view.getLatest().getReason()).isEqualTo(WorkModerationService.DEFAULT_RESTORE_REASON);
        assertThat(notificationService.countUnread(author)).isEqualTo(unreadBefore + 1);
    }

    @Test
    @DisplayName("복구 사유를 적으면 그대로 이력에 남고, 숨김 상태가 아니면 NOT_HIDDEN")
    void restoreWithReasonAndRejection() {
        Long admin = userId("admin");
        moderationService.restore(admin, Role.ADMIN, HIDDEN_WORK, "  확인 결과 문제가 없어 복구합니다.  ");
        assertThat(moderationService.getTarget(HIDDEN_WORK).getLatest().getReason()).isEqualTo("확인 결과 문제가 없어 복구합니다.");

        assertThatThrownBy(() -> moderationService.restore(admin, Role.ADMIN, HIDDEN_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_HIDDEN));
        assertThatThrownBy(() -> moderationService.restore(admin, Role.ADMIN, VISIBLE_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_HIDDEN));
    }

    @Test
    @DisplayName("작성자가 그 사이 비공개로 바꿨다면 복구해도 공개되지 않는다 (작성자는 숨김을 풀 수 없고 공개 설정은 그대로)")
    void restoreKeepsPrivate() {
        var work = workRepository.findById(HIDDEN_WORK).orElseThrow();
        ReflectionTestUtils.setField(work, "visibility", Visibility.PRIVATE);   // 작성자가 비공개로 전환(#19 기능을 대신해 필드 직접 세팅)

        moderationService.restore(userId("admin"), Role.ADMIN, HIDDEN_WORK);

        assertThat(workRepository.findById(HIDDEN_WORK).orElseThrow().getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(boardIds("learnerC")).doesNotContain(HIDDEN_WORK);
        assertThatThrownBy(() -> workBoardService.getDetail(HIDDEN_WORK, userId("learnerC"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("작성자 본인만 조치 사유를 볼 수 있다 — 다른 학습자·없는 작품은 WORK_NOT_FOUND")
    void authorViewOwnerOnly() {
        assertThat(moderationService.getAuthorView(userId("learnerB"), HIDDEN_WORK).getLogs()).hasSize(1);   // 시드의 숨김 이력

        assertThatThrownBy(() -> moderationService.getAuthorView(userId("learnerC"), HIDDEN_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThatThrownBy(() -> moderationService.getAuthorView(userId("learnerB"), 999999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }

    @Test
    @DisplayName("관리자 목록: 공개 작품 전체(숨김 포함), hidden 필터, 요약 숫자, 전체 이력")
    void adminListAndSummary() {
        Long admin = userId("admin");

        WorkSearchDTO all = new WorkSearchDTO();
        assertThat(workBoardService.searchForAdmin(all, admin).getItems()).extracting(WorkDTO::getWorkId)
                .containsExactlyInAnyOrder(1L, VISIBLE_WORK, 4L, 5L, HIDDEN_WORK);   // 비공개 작품(3·7)은 관리 대상이 아니다

        WorkSearchDTO hiddenOnly = new WorkSearchDTO();
        hiddenOnly.setHidden(true);
        List<WorkDTO> hidden = workBoardService.searchForAdmin(hiddenOnly, admin).getItems();
        assertThat(hidden).extracting(WorkDTO::getWorkId).containsExactly(HIDDEN_WORK);
        assertThat(hidden.get(0).isHidden()).isTrue();
        assertThat(hidden.get(0).getHiddenReason()).contains("외부 판매 링크");

        WorkSearchDTO visibleOnly = new WorkSearchDTO();
        visibleOnly.setHidden(false);
        assertThat(workBoardService.searchForAdmin(visibleOnly, admin).getItems()).extracting(WorkDTO::getWorkId)
                .containsExactlyInAnyOrder(1L, VISIBLE_WORK, 4L, 5L);

        ModerationSummaryDTO summary = moderationService.getSummary(admin);
        assertThat(summary.getTotal()).isEqualTo(5);
        assertThat(summary.getVisible()).isEqualTo(4);
        assertThat(summary.getHidden()).isEqualTo(1);
        assertThat(summary.getFinalCount()).isEqualTo(1);     // 작품 1
        assertThat(summary.getGeneralCount()).isEqualTo(4);   // 작품 2·4·5·6

        moderationService.hide(admin, Role.ADMIN, VISIBLE_WORK, REASON);
        assertThat(moderationService.getAllHistory()).extracting(ModerationLogDTO::getWorkId)
                .containsExactly(VISIBLE_WORK, HIDDEN_WORK);   // 최신순
    }

    @Test
    @DisplayName("#163 숨김 이력이 있는 작품을 작성자가 삭제해도 전체 이력이 열리고, 제목은 '(삭제된 작품)'으로 나온다")
    void historyWithDeletedWork() {
        workService.deleteGeneralWork(userId("learnerB"), HIDDEN_WORK);   // 시드: 숨김 + 이력 1건인 일반 작품
        workRepository.flush();

        assertThat(moderationService.getAllHistory())
                .filteredOn(log -> log.getWorkId().equals(HIDDEN_WORK))
                .isNotEmpty()
                .extracting(ModerationLogDTO::getWorkTitle)
                .containsOnly(WorkModerationService.DELETED_WORK_TITLE);
    }

    @Test
    @DisplayName("#167 성장 기록: 숨김 중이면 숨김 사유 링크, 복구된 뒤에도 이력이 있으면 조치 이력 링크(moderated), 이력 없는 작품은 링크 없음")
    void growthLinkAfterRestore() {
        Long author = userId("learnerB");
        assertThat(timelineItem(author, HIDDEN_WORK).isAdminHidden()).isTrue();          // 숨김 중 → [숨김 사유]
        assertThat(timelineItem(author, HIDDEN_WORK).isModerated()).isTrue();

        moderationService.restore(userId("admin"), Role.ADMIN, HIDDEN_WORK, "확인 결과 문제가 없어 복구합니다.");

        TimelineDTO.Item restored = timelineItem(author, HIDDEN_WORK);
        assertThat(restored.isAdminHidden()).isFalse();
        assertThat(restored.isModerated()).isTrue();                                      // 복구 후에도 [조치 이력]으로 U-S16 이동 가능
        assertThat(timelineItem(author, VISIBLE_WORK).isModerated()).isFalse();           // 조치 이력 없는 작품은 링크 없음
    }

    private TimelineDTO.Item timelineItem(Long userId, Long workId) {
        return growthService.getTimeline(userId, null).getItems().stream()
                .filter(item -> item.getWorkId().equals(workId)).findFirst().orElseThrow();
    }
}
