package com.wanted.lmspractice.domain.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 회원 탈퇴 → 탈퇴 완료 안내 → 30일 재가입 제한 흐름을 실제 요청(MockMvc)으로 확인하는 통합 테스트
 * [근거] 체크리스트 ① "회원 탈퇴 후 세션·개인정보·재가입 제한" 검증에서 찾은 버그 2건을 고정한다
 *        - 탈퇴 후 세션 쿠키가 남아 탈퇴 완료 안내 대신 "세션 만료" 화면으로 가던 문제
 *        - 30일이 지나도 가입 화면의 이메일 중복 검사가 먼저 막아 같은 이메일로 재가입할 수 없던 문제
 * [전제] 로컬 MySQL(nolibang)에 schema.sql 적재. 테스트용 학습자를 직접 만들고, 각 테스트는 롤백된다.
 *        메일은 MockitoBean 으로 바꿔 실제로 보내지 않는다.
 * 🟡 미학습 기술: @SpringBootTest·MockMvc·@MockitoBean (작업분배 §5 허용)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalAndRejoinTest {

    private static final String EMAIL = "rejoin-test@example.com";
    private static final String PASSWORD = "Abcd123!";

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    @MockitoBean
    JavaMailSender mailSender;

    /** 이메일 인증을 마친 학습자를 만들고 로그인한 세션을 돌려준다 */
    private MockHttpSession activeLearnerSession() throws Exception {
        User user = User.createLearner("rejointest", passwordEncoder.encode(PASSWORD), "김탈퇴", EMAIL, "탈퇴테스트닉");
        user.verifyEmail(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/auth/login").session(session).with(csrf())
                        .param("loginId", "rejointest").param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private void withdraw(MockHttpSession session) throws Exception {
        mvc.perform(post("/my/account/withdraw").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("agree", "true"))
                .andExpect(redirectedUrl("/auth/withdrawn"))
                // 세션 쿠키를 지워야 다음 요청이 "세션 만료" 화면으로 새지 않고 탈퇴 완료 안내가 보인다
                .andExpect(cookie().maxAge("JSESSIONID", 0));
    }

    private ResultActions signupWithSameEmail(String loginId) throws Exception {
        return mvc.perform(post("/signup").with(csrf())
                .param("loginId", loginId)
                .param("password", PASSWORD).param("passwordConfirm", PASSWORD)
                .param("name", "김재가")
                .param("email", EMAIL)
                .param("nickname", ""));
    }

    @Test
    @DisplayName("탈퇴하면 세션 쿠키를 지우고 탈퇴 완료 안내로 보낸다. 30일 안에는 같은 이메일로 다시 가입할 수 없다")
    void withdrawClearsCookieAndBlocksRejoinWithin30Days() throws Exception {
        withdraw(activeLearnerSession());

        signupWithSameEmail("rejoinnew1")
                .andExpect(status().isOk())  // 같은 가입 화면을 오류와 함께 다시 보여 준다
                .andExpect(model().attributeHasFieldErrorCode("signupDTO", "email", "duplicate"));
    }

    @Test
    @DisplayName("탈퇴 완료 화면은 로그인 없이 열리고 완료 안내와 재가입 제한 일수를 보여 준다")
    void withdrawCompletePageIsPublic() throws Exception {
        mvc.perform(get("/auth/withdrawn"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/withdraw-complete"))
                .andExpect(content().string(containsString("회원 탈퇴가 완료되었습니다")))
                .andExpect(content().string(containsString("탈퇴일로부터 " + User.REJOIN_RESTRICTION_DAYS + "일")));
    }

    @Test
    @DisplayName("탈퇴 후 30일이 지나면 같은 이메일로 다시 가입할 수 있다")
    void rejoinAllowedAfter30Days() throws Exception {
        withdraw(activeLearnerSession());

        entityManager.flush();
        jdbcTemplate.update("UPDATE users SET withdrawn_at = ? WHERE email = ?",
                LocalDateTime.now().minusDays(31), EMAIL);
        entityManager.clear();

        signupWithSameEmail("rejoinnew2")
                .andExpect(redirectedUrl("/auth/verify/sent"));
    }
}
