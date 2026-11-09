package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.ModerationLogDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationSummaryDTO;
import com.wanted.lmspractice.domain.work.dto.ModerationViewDTO;
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
 *       시드: 작품 1(공개·작성자 learner), 작품 5(공개 + 관리자 숨김 + 이력 1건, 작성자 learner), 작품 2(비공개), 관리자 admin.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용), ReflectionTestUtils (작성자의 비공개 전환 메서드가 #19 소관이라 필드를 직접 세팅)
 */
@SpringBootTest
@Transactional
class WorkModerationServiceTest {

    private static final Long VISIBLE_WORK = 1L, HIDDEN_WORK = 5L, PRIVATE_WORK = 2L;
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
    private UserRepository userRepository;

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
        Long admin = userId("admin"), author = userId("learner");
        long unreadBefore = notificationService.countUnread(author);

        moderationService.hide(admin, VISIBLE_WORK, REASON);

        assertThat(boardIds("learner2")).doesNotContain(VISIBLE_WORK);
        assertThatThrownBy(() -> workBoardService.getDetail(VISIBLE_WORK, userId("learner2"), Role.USER))
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

        assertThatThrownBy(() -> moderationService.hide(admin, HIDDEN_WORK, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_HIDDEN));
        assertThatThrownBy(() -> moderationService.hide(admin, PRIVATE_WORK, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, VISIBLE_WORK, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, VISIBLE_WORK, "  짧음  "))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, VISIBLE_WORK, "가".repeat(1001)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> moderationService.hide(admin, 999999L, REASON))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThat(workRepository.findById(VISIBLE_WORK).orElseThrow().isAdminHidden()).isFalse();   // 거부된 요청은 상태를 바꾸지 않는다
    }

    @Test
    @DisplayName("복구: 게시판에 다시 보이고 복구 이력·작성자 알림이 생기며, 사유를 비우면 기본 문구가 남는다")
    void restore() {
        Long admin = userId("admin"), author = userId("learner");
        long unreadBefore = notificationService.countUnread(author);

        moderationService.restore(admin, HIDDEN_WORK);

        assertThat(boardIds("learner2")).contains(HIDDEN_WORK);
        assertThat(workBoardService.getDetail(HIDDEN_WORK, userId("learner2"), Role.USER)).isNotNull();
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
        moderationService.restore(admin, HIDDEN_WORK, "  확인 결과 문제가 없어 복구합니다.  ");
        assertThat(moderationService.getTarget(HIDDEN_WORK).getLatest().getReason()).isEqualTo("확인 결과 문제가 없어 복구합니다.");

        assertThatThrownBy(() -> moderationService.restore(admin, HIDDEN_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_HIDDEN));
        assertThatThrownBy(() -> moderationService.restore(admin, VISIBLE_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_HIDDEN));
    }

    @Test
    @DisplayName("작성자가 그 사이 비공개로 바꿨다면 복구해도 공개되지 않는다 (작성자는 숨김을 풀 수 없고 공개 설정은 그대로)")
    void restoreKeepsPrivate() {
        var work = workRepository.findById(HIDDEN_WORK).orElseThrow();
        ReflectionTestUtils.setField(work, "visibility", Visibility.PRIVATE);   // 작성자가 비공개로 전환(#19 기능을 대신해 필드 직접 세팅)

        moderationService.restore(userId("admin"), HIDDEN_WORK);

        assertThat(workRepository.findById(HIDDEN_WORK).orElseThrow().getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(boardIds("learner2")).doesNotContain(HIDDEN_WORK);
        assertThatThrownBy(() -> workBoardService.getDetail(HIDDEN_WORK, userId("learner2"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("작성자 본인만 조치 사유를 볼 수 있다 — 다른 학습자·없는 작품은 WORK_NOT_FOUND")
    void authorViewOwnerOnly() {
        assertThat(moderationService.getAuthorView(userId("learner"), HIDDEN_WORK).getLogs()).hasSize(1);   // 시드의 숨김 이력

        assertThatThrownBy(() -> moderationService.getAuthorView(userId("learner2"), HIDDEN_WORK))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThatThrownBy(() -> moderationService.getAuthorView(userId("learner"), 999999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }

    @Test
    @DisplayName("관리자 목록: 공개 작품 전체(숨김 포함), hidden 필터, 요약 숫자, 전체 이력")
    void adminListAndSummary() {
        Long admin = userId("admin");

        WorkSearchDTO all = new WorkSearchDTO();
        assertThat(workBoardService.searchForAdmin(all, admin).getItems()).extracting(WorkDTO::getWorkId)
                .containsExactlyInAnyOrder(VISIBLE_WORK, HIDDEN_WORK);   // 비공개 작품(2·3·4·6)은 관리 대상이 아니다

        WorkSearchDTO hiddenOnly = new WorkSearchDTO();
        hiddenOnly.setHidden(true);
        List<WorkDTO> hidden = workBoardService.searchForAdmin(hiddenOnly, admin).getItems();
        assertThat(hidden).extracting(WorkDTO::getWorkId).containsExactly(HIDDEN_WORK);
        assertThat(hidden.get(0).isHidden()).isTrue();
        assertThat(hidden.get(0).getHiddenReason()).contains("외부 판매 링크");

        WorkSearchDTO visibleOnly = new WorkSearchDTO();
        visibleOnly.setHidden(false);
        assertThat(workBoardService.searchForAdmin(visibleOnly, admin).getItems()).extracting(WorkDTO::getWorkId)
                .containsExactly(VISIBLE_WORK);

        ModerationSummaryDTO summary = moderationService.getSummary(admin);
        assertThat(summary.getTotal()).isEqualTo(2);
        assertThat(summary.getVisible()).isEqualTo(1);
        assertThat(summary.getHidden()).isEqualTo(1);
        assertThat(summary.getFinalCount()).isEqualTo(1);
        assertThat(summary.getGeneralCount()).isEqualTo(1);

        moderationService.hide(admin, VISIBLE_WORK, REASON);
        assertThat(moderationService.getAllHistory()).extracting(ModerationLogDTO::getWorkId)
                .containsExactly(VISIBLE_WORK, HIDDEN_WORK);   // 최신순
    }
}
