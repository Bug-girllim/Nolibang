package com.wanted.lmspractice.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.wanted.lmspractice.domain.game.service.GameService;
import com.wanted.lmspractice.global.file.FileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [역할] 회원 탈퇴의 커밋·롤백 경계 확인 — 체크리스트 18(도중 실패 시 전부 되돌림)과 17(커밋 뒤 실제 이미지 파일 삭제)
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리
 * [방법] 테스트 트랜잭션 안에서는 커밋·롤백 결과를 볼 수 없으므로 @Transactional 을 붙이지 않는다.
 *        준비·정리는 TransactionTemplate 으로 직접 커밋하고, 실패 경우는 탈퇴의 마지막 단계(GameService)가 예외를 던지게 바꾼다.
 *        정리(@AfterEach)는 탈퇴가 성공해 익명화된 경우(아이디가 바뀜·댓글 작성자 NULL·익명 좋아요 수 증가)까지 되돌린다.
 * 🟡 미학습 기술: @SpringBootTest·@MockitoSpyBean·TransactionTemplate — 실제 커밋/롤백을 확인하려면 테스트 트랜잭션 밖에서 실행해야 한다
 */
@SpringBootTest
class WithdrawalTransactionTest {

    private static final String LOGIN_ID = "txtestlearner";
    private static final String EMAIL = "withdraw-tx-test@example.com";
    private static final String PASSWORD = "Abcd123!";
    private static final String COMMENT_MARK = "탈퇴트랜잭션확인댓글";
    private static final long OTHERS_WORK_ID = 4L;   // seed learner2 의 최종 과제

    @Autowired
    WithdrawalService withdrawalService;
    @Autowired
    WithdrawnEmailHoldService holdService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    FileStorage fileStorage;

    @Value("${file.upload-dir}")
    String uploadDir;

    @MockitoSpyBean
    GameService gameService;

    private long userId;
    private long workId;
    private int anonymousLikesBefore;

    @BeforeEach
    void prepare() {
        anonymousLikesBefore = jdbc.queryForObject("SELECT anonymous_like_count FROM works WHERE id = ?", Integer.class, OTHERS_WORK_ID);
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO users (login_id, password_hash, real_name, email, nickname, role, status, email_verified_at) "
                    + "VALUES (?, ?, '김트랜', ?, '트랜잭션테스트닉', 'USER', 'ACTIVE', NOW(6))", LOGIN_ID, passwordEncoder.encode(PASSWORD), EMAIL);
            userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO works (author_id, kind, hobby_id, level, visibility) VALUES (?, 'GENERAL', 1, 1, 'PRIVATE')", userId);
            workId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO work_versions (work_id, version_no, title) VALUES (?, 1, '트랜잭션 확인 작품')", workId);
            jdbc.update("INSERT INTO work_likes (work_id, user_id) VALUES (?, ?)", OTHERS_WORK_ID, userId);
            jdbc.update("INSERT INTO work_comments (work_id, author_id, body) VALUES (?, ?, ?)",
                    OTHERS_WORK_ID, userId, COMMENT_MARK + " 010-9999-8888 좋은 작품이에요");
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM work_comments WHERE body LIKE ?", COMMENT_MARK + "%");
            jdbc.update("DELETE FROM work_likes WHERE user_id = ?", userId);
            jdbc.update("DELETE f FROM work_files f JOIN work_versions v ON v.id = f.work_version_id JOIN works w ON w.id = v.work_id "
                    + "WHERE w.author_id = ?", userId);
            jdbc.update("DELETE v FROM work_versions v JOIN works w ON w.id = v.work_id WHERE w.author_id = ?", userId);
            jdbc.update("DELETE FROM works WHERE author_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            jdbc.update("DELETE FROM withdrawn_email_holds WHERE email_hash = ?", holdService.hash(EMAIL));
            jdbc.update("UPDATE works SET anonymous_like_count = ? WHERE id = ?", anonymousLikesBefore, OTHERS_WORK_ID);
        });
    }

    @Test
    @DisplayName("[18] 마지막 단계에서 실패하면 계정·이메일·작품·댓글·좋아요가 모두 탈퇴 전 그대로다")
    void failureRollsBackEverything() {
        doThrow(new IllegalStateException("테스트용 실패")).when(gameService).deleteAllOfUserForWithdrawal(anyLong());

        assertThatThrownBy(() -> withdrawalService.withdraw(userId, PASSWORD)).isInstanceOf(IllegalStateException.class);

        Map<String, Object> user = jdbc.queryForMap("SELECT status, email, login_id FROM users WHERE id = ?", userId);
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user.get("email")).isEqualTo(EMAIL);
        assertThat(user.get("login_id")).isEqualTo(LOGIN_ID);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM works WHERE id = ?", Integer.class, workId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_likes WHERE user_id = ?", Integer.class, userId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_comments WHERE author_id = ? AND body LIKE '%010-9999-8888%'",
                Integer.class, userId)).isOne();
        assertThat(jdbc.queryForObject("SELECT anonymous_like_count FROM works WHERE id = ?", Integer.class, OTHERS_WORK_ID))
                .isEqualTo(anonymousLikesBefore);
        assertThat(holdService.isHeld(EMAIL, LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("[17·7] 탈퇴가 커밋되면 작품 이미지 실제 파일이 지워지고, 작품·이미지 행과 원래 아이디가 사라진다")
    void commitDeletesImageFiles() throws Exception {
        String key = "works/withdraw-tx-test-" + System.nanoTime() + ".png";
        Path file = Path.of(uploadDir).toAbsolutePath().normalize().resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        tx.executeWithoutResult(status -> {
            Long versionId = jdbc.queryForObject("SELECT id FROM work_versions WHERE work_id = ?", Long.class, workId);
            jdbc.update("INSERT INTO work_files (work_version_id, sort_order, original_key, original_name, mime_type, "
                    + "source_size_bytes, stored_size_bytes) VALUES (?, 1, ?, 'a.png', 'image/png', 3, 3)", versionId, key);
        });
        assertThat(fileStorage.load(key)).exists();

        withdrawalService.withdraw(userId, PASSWORD);

        assertThat(Files.exists(file)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM works WHERE author_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_files WHERE original_key = ?", Integer.class, key)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = ?", Integer.class, LOGIN_ID)).isZero();
        assertThat(holdService.isHeld(EMAIL, LocalDateTime.now())).isTrue();
    }
}
