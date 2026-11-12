package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.LikeResultDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [역할] 좋아요 동시 클릭 확인 — 같은 사용자가 같은 작품에 좋아요를 동시에 두 번 눌러도 500(PK 충돌) 없이 차례로 토글된다
 * [담당] 팀원 2 · 좋아요 동시 클릭 수정
 * [방법] 두 요청이 각자 커밋해야 충돌이 드러나므로 @Transactional 을 붙이지 않는다(WithdrawalTransactionTest 와 같은 방식).
 *        시드에서 작품 2(공개)에 좋아요를 누르지 않은 learnerA(좋아요·댓글 이력 없음)를 쓰고, 끝나면 그 좋아요 행을 지워 시드 상태로 되돌린다.
 *        준비·정리 DELETE 는 TransactionTemplate 으로 커밋한다 — 커넥션이 autocommit=false(application.yaml)라 그냥 실행하면 반영되지 않는다.
 * 🟡 미학습 기술: @SpringBootTest, ExecutorService·CountDownLatch(두 요청을 동시에 출발시키려고)
 */
@SpringBootTest
class LikeToggleConcurrencyTest {

    private static final long PUBLIC_WORK = 2L;   // learnerB 의 공개 일반 작품(시드 좋아요: learnerB·learnerC)

    @Autowired
    CommentService commentService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;

    private long userId;

    @BeforeEach
    void prepare() {
        userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'learnerA'", Long.class);
        removeLike();
    }

    @AfterEach
    void cleanUp() {
        removeLike();
    }

    @Test
    @DisplayName("같은 작품에 좋아요를 동시에 두 번 누르면 둘 다 성공하고, 하나는 추가·하나는 취소되어 결국 누르지 않은 상태가 된다")
    void concurrentToggle() throws Exception {
        long before = likeCount();
        CountDownLatch start = new CountDownLatch(1);
        Callable<LikeResultDTO> click = () -> {
            start.await();
            return commentService.toggleLike(userId, Role.USER, PUBLIC_WORK);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<LikeResultDTO>> futures = new ArrayList<>(List.of(pool.submit(click), pool.submit(click)));
            start.countDown();
            List<Boolean> liked = new ArrayList<>();
            for (Future<LikeResultDTO> future : futures) {
                liked.add(future.get(30, TimeUnit.SECONDS).isLiked());   // 예외(PK 충돌)가 나면 여기서 실패한다
            }
            assertThat(liked).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM work_likes WHERE work_id = ? AND user_id = ?",
                Integer.class, PUBLIC_WORK, userId);
        assertThat(rows).isZero();
        assertThat(likeCount()).isEqualTo(before);
    }

    private void removeLike() {
        tx.executeWithoutResult(status ->
                jdbc.update("DELETE FROM work_likes WHERE work_id = ? AND user_id = ?", PUBLIC_WORK, userId));
    }

    private long likeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM work_likes WHERE work_id = ?", Long.class, PUBLIC_WORK);
    }
}
