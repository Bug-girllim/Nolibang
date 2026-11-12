package com.wanted.lmspractice.domain.auth.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.auth.service.AuthService;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강제로 끝난 세션의 안내 화면 구분 — 관리자가 비활성화한 강사는 "다른 기기 로그인"이 아니라 "비활성화된 계정" 안내
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql. teacherA(id 6) 활성 강사. @Transactional이라 끝나면 롤백된다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class SessionExpiredHandlerTest {

    private static final long TEACHER_A = 6L;

    @Autowired
    private SessionExpiredHandler handler;
    @Autowired
    private AuthService authService;
    @Autowired
    private InstructorAccountService instructorAccountService;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("활성 계정의 세션이 끝났으면 다른 기기 로그인 안내(E-DUP)")
    void activeAccountGoesToDuplicateNotice() {
        Object principal = authService.loadUserByUsername("teacherA");  // 세션에 저장돼 있던 로그인 정보

        assertThat(handler.targetUrl(principal)).isEqualTo(SessionExpiredHandler.DUPLICATE_LOGIN_URL);
    }

    @Test
    @DisplayName("관리자가 비활성화해 끝난 강사 세션은 비활성 계정 안내(로그인 화면)")
    void deactivatedInstructorGoesToDisabledNotice() {
        Object principal = authService.loadUserByUsername("teacherA");  // 비활성화 전에 로그인해 둔 정보
        instructorAccountService.changeStatus(TEACHER_A, false);
        entityManager.flush();

        assertThat(handler.targetUrl(principal)).isEqualTo(SessionExpiredHandler.DISABLED_URL);
    }

    @Test
    @DisplayName("로그인 정보가 아닌 값이면 기존 안내를 쓴다")
    void unknownPrincipal() {
        assertThat(handler.targetUrl("anonymousUser")).isEqualTo(SessionExpiredHandler.DUPLICATE_LOGIN_URL);
    }
}
