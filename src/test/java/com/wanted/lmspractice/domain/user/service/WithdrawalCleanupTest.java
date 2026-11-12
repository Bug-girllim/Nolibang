package com.wanted.lmspractice.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.user.repository.WithdrawnEmailHoldRepository;
import com.wanted.lmspractice.domain.work.entity.WorkComment;
import com.wanted.lmspractice.domain.work.policy.CommentMaskPolicy;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 회원 탈퇴 체크리스트(U-15, D-25) — 계정 익명화·이메일 확인값·연관 데이터 삭제·댓글/좋아요 익명 보존을 실제 DB로 확인한다
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리
 * [전제] 로컬 MySQL(nolibang)에 schema.sql(+ migration_withdrawal_privacy.sql)·seed.sql 적재. 각 테스트는 롤백된다.
 *        시드 학습자 learnerB(id 3, 비밀번호 demo_L02)는 진행 중 수강(수강 1·2, 수강 2는 평가 대기)이 있어 탈퇴가 막히므로,
 *        준비 단계에서 진행 중 수강을 만료로 바꾼다. learnerB에게는 작품 1·2·3·6, 문의 1·2, 수료 배지(수강 3), 평가 회차 1·2, 알림이 있다.
 *        다른 학습자 learnerC(id 4)의 공개 일반 작품(work 4)에 learnerB 의 댓글·좋아요를 넣어 "남의 작품" 경우를 만든다
 *        (learnerB 의 work 4 좋아요는 시드에 이미 있다). 남의 답글·내 작품에 남이 단 댓글은 learnerA(id 2)가 단다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class WithdrawalCleanupTest {

    private static final long LEARNER_ID = 3L;     // seed learnerB (탈퇴하는 학습자)
    private static final long LEARNER_C_ID = 4L;   // seed learnerC
    private static final long LEARNER_A_ID = 2L;   // seed learnerA
    private static final long OTHERS_WORK_ID = 4L; // learnerC 의 공개 일반 작품
    private static final long MY_PUBLIC_WORK_ID = 2L;  // learnerB 의 공개 일반 작품 (시드 댓글 2개·좋아요 2개)
    private static final String PASSWORD = "demo_L02";

    @Autowired
    WithdrawalService withdrawalService;
    @Autowired
    WithdrawnEmailHoldService holdService;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WithdrawnEmailHoldRepository holdRepository;
    @Autowired
    SessionRegistry sessionRegistry;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;

    private String originalEmail;
    private String originalRealName;
    private long normalCommentId;
    private long deletedCommentId;
    private long replyByOtherId;
    private int anonymousLikesBefore;

    @BeforeEach
    void prepare() {
        User learner = userRepository.findById(LEARNER_ID).orElseThrow();
        originalEmail = learner.getEmail();
        originalRealName = learner.getRealName();

        // 진행 중 수강이 있으면 탈퇴가 막힌다 → 이 테스트에서는 만료로 바꾼다(CHECK: ACTIVE 가 아니면 ended_at 필수)
        jdbc.update("UPDATE enrollments SET status = 'EXPIRED', ended_at = NOW(6) WHERE user_id = ? AND status = 'ACTIVE'", LEARNER_ID);

        // 남의 작품에 단 정상 댓글(본문에 개인정보) · 자발 삭제 댓글 · 그 댓글에 달린 남의 답글
        normalCommentId = insertComment(OTHERS_WORK_ID, null, LEARNER_ID,
                "연락 주세요 010-1234-5678 또는 " + originalEmail + " 저는 " + originalRealName + " 입니다. 색감이 정말 좋아요", false);
        deletedCommentId = insertComment(OTHERS_WORK_ID, null, LEARNER_ID, WorkComment.DELETED_BODY, true);
        replyByOtherId = insertComment(OTHERS_WORK_ID, normalCommentId, LEARNER_A_ID, "저도 그렇게 생각해요", false);

        // 남의 작품에 누른 좋아요(익명 집계로 남아야 함) · 내 작품에 남이 단 댓글·좋아요(작품과 함께 지워져야 함)
        jdbc.update("INSERT IGNORE INTO work_likes (work_id, user_id) VALUES (?, ?)", OTHERS_WORK_ID, LEARNER_ID);
        insertComment(MY_PUBLIC_WORK_ID, null, LEARNER_A_ID, "멋진 작품이에요", false);
        jdbc.update("INSERT IGNORE INTO work_likes (work_id, user_id) VALUES (?, ?)", MY_PUBLIC_WORK_ID, LEARNER_C_ID);
        anonymousLikesBefore = jdbc.queryForObject("SELECT anonymous_like_count FROM works WHERE id = ?", Integer.class, OTHERS_WORK_ID);

        // 인증 비밀정보·개인 게임 기록
        jdbc.update("INSERT INTO auth_tokens (user_id, purpose, token_hash, expires_at) "
                + "VALUES (?, 'RESET_PASSWORD', UNHEX(SHA2('withdraw-test-token', 256)), NOW(6) + INTERVAL 1 DAY)", LEARNER_ID);
        jdbc.update("INSERT INTO quest_claims (user_id, quest_key, period_key, reward_xp) VALUES (?, 'TEST_QUEST', 'T-1', 10)", LEARNER_ID);
    }

    private long insertComment(long workId, Long parentId, long authorId, String body, boolean deleted) {
        jdbc.update("INSERT INTO work_comments (work_id, parent_id, author_id, body, status, deleted_at) VALUES (?, ?, ?, ?, ?, ?)",
                workId, parentId, authorId, body, deleted ? "DELETED" : "NORMAL", deleted ? LocalDateTime.now() : null);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void withdrawLearner() {
        withdrawalService.withdraw(LEARNER_ID, PASSWORD);
        entityManager.flush();   // 변경 감지로 남은 UPDATE·INSERT 를 DB 에 보낸 뒤 JdbcTemplate 으로 확인한다
        entityManager.clear();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("[1·6] 탈퇴하면 로그인 불가 상태가 되고, 이미 로그인한 세션도 만료되며, 식별정보·비밀번호·토큰이 정리된다")
    void accountAnonymizedAndSessionsExpired() {
        User before = userRepository.findById(LEARNER_ID).orElseThrow();
        sessionRegistry.registerNewSession("withdraw-other-device", new AuthDetails(before));
        String oldHash = before.getPasswordHash();
        String oldLoginId = before.getLoginId();

        withdrawLearner();

        User after = userRepository.findById(LEARNER_ID).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(new AuthDetails(after).isEnabled()).isFalse();   // 로그인 시 DisabledException
        assertThat(sessionRegistry.getSessionInformation("withdraw-other-device").isExpired()).isTrue();
        assertThat(after.getLoginId()).isNotEqualTo(oldLoginId).startsWith(User.ANONYMIZED_PREFIX);
        assertThat(after.getRealName()).isEqualTo(User.ANONYMIZED_REAL_NAME);
        assertThat(after.getNickname()).startsWith(User.ANONYMIZED_PREFIX);
        assertThat(after.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(count("SELECT COUNT(*) FROM auth_tokens WHERE user_id = ?", LEARNER_ID)).isZero();
        assertThat(userRepository.findByLoginId(oldLoginId)).isEmpty();
    }

    @Test
    @DisplayName("[2·3·4] 이메일 원문은 즉시 지워지고 확인값(원문 아님)만 탈퇴 시각 + 30×24시간으로 남아 같은 이메일 가입을 막는다")
    void emailReplacedByHold() {
        withdrawLearner();

        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", originalEmail)).isZero();
        assertThat(userRepository.findById(LEARNER_ID).orElseThrow().getEmail()).endsWith("@" + User.ANONYMIZED_EMAIL_DOMAIN);

        Map<String, Object> hold = jdbc.queryForMap("SELECT * FROM withdrawn_email_holds WHERE email_hash = ?", holdService.hash(originalEmail));
        assertThat(hold.get("email_hash").toString()).hasSize(64).doesNotContain(originalEmail);
        LocalDateTime withdrawnAt = (LocalDateTime) hold.get("withdrawn_at");
        LocalDateTime expiresAt = (LocalDateTime) hold.get("expires_at");
        assertThat(java.time.Duration.between(withdrawnAt, expiresAt).toHours()).isEqualTo(30 * 24);
        assertThat(count("SELECT COUNT(*) FROM withdrawn_email_holds WHERE email_hash = ?", originalEmail)).isZero();

        // 대소문자·공백이 달라도 같은 이메일로 본다(가입 정규화와 같은 기준)
        assertThat(userService.isEmailAvailableForSignup(originalEmail)).isFalse();
        assertThat(userService.isEmailAvailableForSignup("  " + originalEmail.toUpperCase() + " ")).isFalse();
        // 경계 확인은 JPA 로 읽은 값으로 한다(JdbcTemplate 은 DB 의 UTC 값을 그대로, JPA 는 서버 시간대로 바꿔 읽는다)
        LocalDateTime heldFrom = holdRepository.findById(holdService.hash(originalEmail)).orElseThrow().getWithdrawnAt();
        assertThat(holdService.isHeld(originalEmail, heldFrom.plusDays(30).minusSeconds(1))).isTrue();
        assertThat(holdService.isHeld(originalEmail, heldFrom.plusDays(30))).isFalse();
    }

    @Test
    @DisplayName("[5] 30일이 지나면 확인값이 정리되고 같은 이메일을 다시 쓸 수 있다")
    void expiredHoldPurgedAndRejoinAllowed() {
        withdrawLearner();
        LocalDateTime past = LocalDateTime.now().minusDays(31);
        jdbc.update("UPDATE withdrawn_email_holds SET withdrawn_at = ?, expires_at = ? WHERE email_hash = ?",
                past, past.plusDays(User.REJOIN_RESTRICTION_DAYS), holdService.hash(originalEmail));

        assertThat(userService.isEmailAvailableForSignup(originalEmail)).isTrue();   // 정리 전이라도 만료 시각으로 판단
        assertThat(holdService.purgeExpired(LocalDateTime.now())).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM withdrawn_email_holds WHERE email_hash = ?", holdService.hash(originalEmail))).isZero();
    }

    @Test
    @DisplayName("[7·8·9·10·16·17] 본인 작품·이미지 행, 수강·진도·평가(공식 피드백)·대기·배지 기록, 1:1 문의, 알림이 FK 오류 없이 삭제된다")
    void learningAndWorkDataDeleted() {
        assertThat(count("SELECT COUNT(*) FROM works WHERE author_id = ?", LEARNER_ID)).isPositive();
        assertThat(count("SELECT COUNT(*) FROM inquiry_threads WHERE learner_id = ?", LEARNER_ID)).isPositive();

        withdrawLearner();

        assertThat(count("SELECT COUNT(*) FROM works WHERE author_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM work_versions v LEFT JOIN works w ON w.id = v.work_id WHERE w.id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM work_files f LEFT JOIN work_versions v ON v.id = f.work_version_id WHERE v.id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM work_comments WHERE work_id = ?", MY_PUBLIC_WORK_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM work_likes WHERE work_id = ?", MY_PUBLIC_WORK_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM enrollments WHERE user_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM waitlist_entries WHERE user_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM v_user_level_badges WHERE user_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM lesson_progresses lp LEFT JOIN enrollments e ON e.id = lp.enrollment_id WHERE e.id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM review_rounds rr LEFT JOIN enrollments e ON e.id = rr.enrollment_id WHERE e.id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM inquiry_threads WHERE learner_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", LEARNER_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM quest_claims WHERE user_id = ?", LEARNER_ID)).isZero();
        // 다른 학습자의 데이터는 그대로
        assertThat(count("SELECT COUNT(*) FROM works WHERE id = ?", OTHERS_WORK_ID)).isOne();
    }

    @Test
    @DisplayName("[11·12·13·14] 남의 작품 댓글은 식별정보를 가리고 계정 연결을 끊어 남기고, 자발 삭제 댓글은 삭제 상태 그대로, 남의 답글은 유지된다")
    void commentsAnonymized() {
        withdrawLearner();

        Map<String, Object> normal = jdbc.queryForMap("SELECT * FROM work_comments WHERE id = ?", normalCommentId);
        String body = (String) normal.get("body");
        assertThat(normal.get("author_id")).isNull();
        assertThat(normal.get("status")).isEqualTo("NORMAL");
        assertThat(body).doesNotContain("010-1234-5678").doesNotContain(originalEmail).doesNotContain(originalRealName)
                .contains(CommentMaskPolicy.MASK).contains("색감이 정말 좋아요");

        Map<String, Object> deleted = jdbc.queryForMap("SELECT * FROM work_comments WHERE id = ?", deletedCommentId);
        assertThat(deleted.get("author_id")).isNull();
        assertThat(deleted.get("status")).isEqualTo("DELETED");
        assertThat(deleted.get("body")).isEqualTo(WorkComment.DELETED_BODY);

        Map<String, Object> reply = jdbc.queryForMap("SELECT * FROM work_comments WHERE id = ?", replyByOtherId);
        assertThat(((Number) reply.get("author_id")).longValue()).isEqualTo(LEARNER_A_ID);
        assertThat(((Number) reply.get("parent_id")).longValue()).isEqualTo(normalCommentId);

        assertThat(count("SELECT COUNT(*) FROM work_comments WHERE author_id = ?", LEARNER_ID)).isZero();
    }

    @Test
    @DisplayName("[15] 좋아요 계정 연결은 지우고, 남의 작품에 누른 좋아요는 익명 수로 남아 좋아요 수가 그대로다")
    void likesAnonymized() {
        int memberLikesBefore = count("SELECT COUNT(*) FROM work_likes WHERE work_id = ?", OTHERS_WORK_ID);

        withdrawLearner();

        assertThat(count("SELECT COUNT(*) FROM work_likes WHERE user_id = ?", LEARNER_ID)).isZero();
        int memberLikesAfter = count("SELECT COUNT(*) FROM work_likes WHERE work_id = ?", OTHERS_WORK_ID);
        int anonymousAfter = count("SELECT anonymous_like_count FROM works WHERE id = ?", OTHERS_WORK_ID);
        assertThat(anonymousAfter).isEqualTo(anonymousLikesBefore + 1);
        assertThat(memberLikesAfter + anonymousAfter).isEqualTo(memberLikesBefore + anonymousLikesBefore);
    }

    @Test
    @DisplayName("이전 방식으로 원문 이메일이 남은 탈퇴 계정은 원문을 지우고, 30일 안이면 확인값을 만든다")
    void legacyWithdrawnEmailMigrated() {
        LocalDateTime recent = LocalDateTime.now().minusDays(1);
        LocalDateTime old = LocalDateTime.now().minusDays(40);
        jdbc.update("INSERT INTO users (login_id, password_hash, real_name, email, nickname, role, status, withdrawn_at) "
                + "VALUES ('legacy_a', 'x', '탈퇴회원', 'legacy-a@example.com', 'legacyA', 'USER', 'WITHDRAWN', ?), "
                + "('legacy_b', 'x', '탈퇴회원', 'legacy-b@example.com', 'legacyB', 'USER', 'WITHDRAWN', ?)", recent, old);

        assertThat(holdService.migrateLegacyWithdrawnEmails(LocalDateTime.now())).isGreaterThanOrEqualTo(2);
        entityManager.flush();

        assertThat(count("SELECT COUNT(*) FROM users WHERE email IN ('legacy-a@example.com', 'legacy-b@example.com')")).isZero();
        assertThat(holdService.isHeld("legacy-a@example.com", LocalDateTime.now())).isTrue();
        assertThat(holdService.isHeld("legacy-b@example.com", LocalDateTime.now())).isFalse();
    }
}
