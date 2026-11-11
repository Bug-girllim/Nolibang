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
 * [역할] 강사용 알림(새 문의·과제 제출)의 이동 — 분반 담당이 바뀐 뒤 이전 강사의 옛 알림이 403 오류 화면으로 가지 않는지
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql. @Transactional이라 끝나면 롤백된다.
 *       시드: 문의 스레드 2(learner3 · 수강 e6 · 분반 1 teacherA), 평가 회차 4(수강 e5 · 분반 5 teacherA).
 *       teacherB(7)는 취미 1(색연필화) 권한이 있다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class NotificationInstructorLinkTest {

    private static final long TEACHER_A = 6L, TEACHER_B = 7L;
    private static final long THREAD = 2L, REVIEW_ROUND = 4L;
    private static final long SECTION_OF_THREAD = 1L, SECTION_OF_REVIEW = 5L;

    @Autowired
    NotificationService notificationService;
    @PersistenceContext
    EntityManager entityManager;

    private Long inquiryNotice;
    private Long reviewNotice;

    @BeforeEach
    void setUp() {
        // 시드 알림과 dedupe 키가 겹치지 않게 이 테스트만의 키를 쓴다
        notificationService.notify(TEACHER_A, NotificationKind.INQUIRY_RECEIVED, THREAD, "새 문의", "문의", "link-test:inquiry");
        entityManager.flush();
        inquiryNotice = latestId(TEACHER_A);
        notificationService.notify(TEACHER_A, NotificationKind.REVIEW_SUBMITTED, REVIEW_ROUND, "과제 제출", "제출", "link-test:review");
        entityManager.flush();
        reviewNotice = latestId(TEACHER_A);
    }

    private Long latestId(long userId) {
        return ((Number) entityManager.createNativeQuery(
                "SELECT MAX(id) FROM notifications WHERE user_id = :u").setParameter("u", userId).getSingleResult()).longValue();
    }

    private void changeInstructor(long sectionId, long instructorId) {
        entityManager.createNativeQuery("UPDATE course_sections SET instructor_id = " + instructorId + " WHERE id = " + sectionId)
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("지금 담당 강사면 문의·평가 화면으로 이동한다")
    void currentInstructorLinks() {
        assertThat(notificationService.open(TEACHER_A, inquiryNotice)).contains("/instructor/inquiries/" + THREAD);
        assertThat(notificationService.open(TEACHER_A, reviewNotice)).contains("/instructor/reviews/" + REVIEW_ROUND);
    }

    @Test
    @DisplayName("분반 담당이 바뀌면 이전 강사의 옛 알림은 이동하지 않고(알림함 안내), 새 담당 강사는 이동한다")
    void previousInstructorDoesNotLink() {
        changeInstructor(SECTION_OF_THREAD, TEACHER_B);
        changeInstructor(SECTION_OF_REVIEW, TEACHER_B);

        assertThat(notificationService.open(TEACHER_A, inquiryNotice)).isEmpty();
        assertThat(notificationService.open(TEACHER_A, reviewNotice)).isEmpty();

        notificationService.notify(TEACHER_B, NotificationKind.INQUIRY_RECEIVED, THREAD, "새 문의", "문의", "link-test:inquiry-b");
        entityManager.flush();
        assertThat(notificationService.open(TEACHER_B, latestId(TEACHER_B))).contains("/instructor/inquiries/" + THREAD);
    }

    @Test
    @DisplayName("분반이 담당 공백이 되면(긴급 비활성화) 옛 알림은 이동하지 않는다")
    void vacantSectionDoesNotLink() {
        entityManager.createNativeQuery("UPDATE course_sections SET instructor_id = NULL WHERE id = " + SECTION_OF_THREAD)
                .executeUpdate();
        entityManager.clear();

        assertThat(notificationService.open(TEACHER_A, inquiryNotice)).isEmpty();
    }
}
