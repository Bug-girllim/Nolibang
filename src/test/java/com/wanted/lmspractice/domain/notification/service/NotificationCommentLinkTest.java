package com.wanted.lmspractice.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 댓글 알림(공개 댓글·내 작품 댓글)의 이동 주소가 작품의 공개 상태를 따르는지 — 숨김·비공개·삭제 후 처리
 * [근거] 체크리스트 ⑥ "삭제·숨김 이후 알림 링크 처리" 검증에서 발견 — 관리자가 숨긴 작품의 댓글 알림을 강사가 누르면
 *        403 오류 화면이 떴다(삭제된 작품은 알림함으로 돌아가 "볼 수 없어요" 안내가 나오는데, 숨김은 그렇지 않았다)
 * [전제] 로컬 MySQL(nolibang)에 seed.sql 적재. 시드: 작품 2(learnerB(3) 의 공개 일반 작품), 댓글 1(teacherA)·답글 2(learnerB) — 둘 다 작품 2.
 *        알림은 테스트에서 만들고, 상태 변경은 네이티브 SQL — 모두 롤백된다.
 */
@SpringBootTest
@Transactional
class NotificationCommentLinkTest {

    private static final long LEARNER = 3L, TEACHER_B = 7L, WORK = 2L, COMMENT = 1L;   // learnerB · teacherB · 작품 2 · 댓글 1

    @Autowired
    NotificationService notificationService;
    @PersistenceContext
    EntityManager entityManager;

    private Long instructorNotice;
    private Long authorNotice;

    @BeforeEach
    void setUp() {
        // 시드에 같은 사건의 알림(dedupe 키)이 있으면 새로 만들지 않으므로 이 테스트만의 키를 쓴다
        notificationService.notify(TEACHER_B, NotificationKind.PUBLIC_COMMENT, COMMENT, "공개 댓글", "새 댓글", "link-test:public");
        notificationService.notify(LEARNER, NotificationKind.COMMENT_ON_MY_WORK, COMMENT, "내 작품 댓글", "새 댓글", "link-test:mine");
        entityManager.flush();
        instructorNotice = latestId(TEACHER_B);
        authorNotice = latestId(LEARNER);
    }

    private Long latestId(long userId) {
        return ((Number) entityManager.createNativeQuery(
                "SELECT MAX(id) FROM notifications WHERE user_id = :u").setParameter("u", userId).getSingleResult()).longValue();
    }

    private void sql(String statement) {
        entityManager.createNativeQuery(statement).executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("공개 작품이면 강사·작성자 모두 작품으로 이동한다")
    void publicWorkLinks() {
        assertThat(notificationService.open(TEACHER_B, instructorNotice)).contains("/works/" + WORK);
        assertThat(notificationService.open(LEARNER, authorNotice)).contains("/works/" + WORK);
    }

    @Test
    @DisplayName("관리자가 숨긴 작품: 강사 알림은 이동하지 않고(알림함 안내), 작성자 알림은 그대로 작품으로 이동한다")
    void hiddenWorkLinksOnlyForAuthor() {
        sql("UPDATE works SET admin_hidden = TRUE, hidden_reason = 'qa', hidden_at = NOW(6), hidden_by = 1 WHERE id = " + WORK);

        assertThat(notificationService.open(TEACHER_B, instructorNotice)).isEmpty();
        assertThat(notificationService.open(LEARNER, authorNotice)).contains("/works/" + WORK);
    }

    @Test
    @DisplayName("작성자가 비공개로 바꾼 작품도 강사 알림은 이동하지 않는다. 삭제된 작품은 아무도 이동하지 않는다")
    void privateOrDeletedWork() {
        sql("UPDATE works SET visibility = 'PRIVATE' WHERE id = " + WORK);
        assertThat(notificationService.open(TEACHER_B, instructorNotice)).isEmpty();
        assertThat(notificationService.open(LEARNER, authorNotice)).contains("/works/" + WORK);

        // 삭제(논리 삭제는 display_version_id 를 비운다 — ck_works_deleted)
        sql("UPDATE works SET deleted_at = NOW(6), display_version_id = NULL WHERE id = " + WORK);
        assertThat(notificationService.open(TEACHER_B, instructorNotice)).isEmpty();
        assertThat(notificationService.open(LEARNER, authorNotice)).isEmpty();
    }
}
