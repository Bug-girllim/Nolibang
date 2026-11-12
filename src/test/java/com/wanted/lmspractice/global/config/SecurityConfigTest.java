package com.wanted.lmspractice.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * [역할] 역할별 URL 권한·CSRF·소유자 검사를 실제 로그인(formLogin)으로 확인하는 MockMvc 통합 테스트
 * [근거] docs/보안점검_CSRF_권한.md — 점검 표를 테스트로 고정했다. SecurityConfig·컨트롤러 권한이 바뀌면 여기서 먼저 깨진다.
 * [전제] 시드 데이터(seed.sql)가 들어 있는 DB. 시연 계정 비밀번호는 계정마다 다르다(아래 상수 — README "시연 계정" 표와 같은 값).
 *        learnerB·learnerC는 첫 로그인 기록이 이미 있어 로그인하면 "/"로 간다(없으면 /hobbies). teacherD는 임시 비밀번호 상태(must_change_password).
 *        소유자 검사 케이스는 시드 id에 기대므로 시드를 바꾸면 함께 고친다 — learnerB 소유: 작품 3(비공개 최종 과제, 이미지 파일 4)·작품 2(일반 작품),
 *        문의 1, 수강 1, 평가 회차 1 / 평가 회차 2 = teacherA 담당 분반(4)의 평가 대기 / 작품 1 = learnerB의 공개 작품.
 * [주의] 쓰기 요청은 모두 거부되어야 하는 것만 보낸다(DB를 바꾸지 않는다). 로그인은 같은 계정의 이전 세션을 끝내므로 테스트마다 새로 로그인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    private MockHttpSession login(String loginId, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/auth/login").session(session).with(csrf())
                .param("loginId", loginId).param("password", password))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    // 시연 계정 비밀번호 (seed.sql D1·관리자 행)
    private static final String LEARNER_B_PW = "demo_L02";
    private static final String LEARNER_C_PW = "demo_L03";
    private static final String TEACHER_A_PW = "demo_T01";
    private static final String TEACHER_B_PW = "demo_T02";
    private static final String TEACHER_C_PW = "demo_T03";
    private static final String TEACHER_D_PW = "demo_T04";
    private static final String NEWBIE_PW = "Newbie_01";
    private static final String ADMIN_PW = "demo_Admin01";

    private MockHttpSession as(String role) throws Exception {
        return switch (role) {
            case "USER" -> login("learnerB", LEARNER_B_PW);
            case "INSTRUCTOR" -> login("teacherA", TEACHER_A_PW);
            case "ADMIN" -> login("admin", ADMIN_PW);
            default -> throw new IllegalArgumentException(role);
        };
    }

    // ───────── 역할 × URL (GET) ─────────

    static Stream<Arguments> getMatrix() {
        String[] userUrls = {"/my/learning", "/my/works/new", "/my/works/growth", "/inquiries"};
        String[] sharedUrls = {"/works", "/notifications", "/my/account"};
        String[] instructorUrls = {"/instructor", "/instructor/reviews", "/instructor/inquiries",
                "/instructor/students", "/instructor/stats"};
        String[] adminUrls = {"/admin", "/admin/instructors", "/admin/sections", "/admin/waitlists",
                "/admin/hobbies", "/admin/works", "/admin/stats"};
        Stream.Builder<Arguments> b = Stream.builder();
        for (String u : userUrls) {
            b.add(Arguments.of("USER", u, 200)).add(Arguments.of("INSTRUCTOR", u, 403)).add(Arguments.of("ADMIN", u, 403));
        }
        for (String u : sharedUrls) {
            b.add(Arguments.of("USER", u, 200)).add(Arguments.of("INSTRUCTOR", u, 200)).add(Arguments.of("ADMIN", u, 200));
        }
        for (String u : instructorUrls) {
            b.add(Arguments.of("USER", u, 403)).add(Arguments.of("INSTRUCTOR", u, 200)).add(Arguments.of("ADMIN", u, 403));
        }
        for (String u : adminUrls) {
            b.add(Arguments.of("USER", u, 403)).add(Arguments.of("INSTRUCTOR", u, 403)).add(Arguments.of("ADMIN", u, 200));
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} GET {1} → {2}")
    @MethodSource("getMatrix")
    @DisplayName("역할별 GET 접근 제어")
    void getByRole(String role, String url, int expected) throws Exception {
        mvc.perform(get(url).session(as(role))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "비로그인 GET {0} → 로그인 필요 화면")
    @MethodSource("anonymousUrls")
    @DisplayName("비로그인은 보호된 URL에서 /error/401로 이동")
    void anonymousIsRedirected(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/401"));
    }

    static Stream<String> anonymousUrls() {
        return Stream.of("/my/learning", "/inquiries", "/works", "/notifications", "/my/account",
                "/instructor", "/admin", "/admin/works", "/files/works/1", "/some/unknown/url");
    }

    // ───────── 로그인 후 보던 화면으로 이어서 이동 (E-401 문구) ─────────

    /** 비로그인 브라우저가 화면을 열려다 /error/401로 간 뒤, 같은 세션으로 로그인했을 때의 이동 주소 */
    private String loginAfterVisiting(String url, String accept, String loginId, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(url).session(session).header("Accept", accept)).andExpect(redirectedUrl("/error/401"));
        return mvc.perform(post("/auth/login").session(session).with(csrf())
                        .param("loginId", loginId).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    private static final String HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Test
    @DisplayName("로그인 전에 보려던 화면(쿼리 포함)이 있으면 로그인 후 그 화면으로 간다")
    void loginReturnsToRequestedPage() throws Exception {
        assertThat(loginAfterVisiting("/my/account?tab=password", HTML, "learnerB", LEARNER_B_PW))
                .isEqualTo("/my/account?tab=password");
        assertThat(loginAfterVisiting("/instructor/reviews", HTML, "teacherA", TEACHER_A_PW))
                .isEqualTo("/instructor/reviews");
    }

    @Test
    @DisplayName("보려던 화면을 그 역할이 볼 수 없으면(학습자 화면 → 강사 로그인) 역할별 첫 화면으로 간다")
    void loginIgnoresPageTheRoleCannotOpen() throws Exception {
        assertThat(loginAfterVisiting("/my/learning", HTML, "teacherA", TEACHER_A_PW)).isEqualTo("/instructor");
        assertThat(loginAfterVisiting("/admin", HTML, "learnerB", LEARNER_B_PW)).isEqualTo("/");
    }

    @Test
    @DisplayName("이미지·fetch 처럼 화면이 아닌 요청은 저장하지 않는다 → 역할별 첫 화면")
    void nonPageRequestsAreNotSaved() throws Exception {
        assertThat(loginAfterVisiting("/files/works/1", "image/avif,image/webp,*/*;q=0.8", "learnerB", LEARNER_B_PW)).isEqualTo("/");
        assertThat(loginAfterVisiting("/notifications", "application/json", "learnerB", LEARNER_B_PW)).isEqualTo("/");
    }

    @Test
    @DisplayName("임시 비밀번호 강사는 보려던 화면이 있어도 비밀번호 변경 화면이 먼저")
    void temporaryPasswordBeatsRequestedPage() throws Exception {
        assertThat(loginAfterVisiting("/instructor/reviews", HTML, "teacherD", TEACHER_D_PW))
                .isEqualTo("/auth/instructor/password");
    }

    @ParameterizedTest(name = "비로그인 GET {0} → 200")
    @MethodSource("publicUrls")
    @DisplayName("공개 URL은 비로그인으로 열린다")
    void publicUrlsAreOpen(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    static Stream<String> publicUrls() {
        return Stream.of("/", "/hobbies", "/courses", "/auth/login", "/signup", "/privacy");
    }

    // ───────── 상태 변경 POST의 역할 제한 (CSRF 토큰은 붙여서 권한만 본다) ─────────

    @Test
    @DisplayName("학습자는 관리자·강사 POST 불가")
    void userCannotPostPrivileged() throws Exception {
        MockHttpSession s = as("USER");
        mvc.perform(post("/admin/works/1/hide").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/sections").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/instructor/reviews/1/approve").session(s).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("강사는 관리자·학습자 POST 불가")
    void instructorCannotPostPrivileged() throws Exception {
        MockHttpSession s = as("INSTRUCTOR");
        mvc.perform(post("/admin/works/1/hide").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/instructors").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/my/works").session(s).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 학습자·강사 POST 불가")
    void adminCannotPostLearnerOrInstructor() throws Exception {
        MockHttpSession s = as("ADMIN");
        mvc.perform(post("/enrollments").session(s).with(csrf()).param("courseId", "1")).andExpect(status().isForbidden());
        mvc.perform(post("/my/works/1/visibility").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/instructor/reviews/1/approve").session(s).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인 POST는 로그인 필요 화면으로")
    void anonymousPostIsRedirected() throws Exception {
        mvc.perform(post("/admin/works/1/hide").with(csrf())).andExpect(redirectedUrl("/error/401"));
        mvc.perform(post("/comments").with(csrf())).andExpect(redirectedUrl("/error/401"));
    }

    // ───────── CSRF ─────────

    @Test
    @DisplayName("CSRF 토큰 없는 POST는 403 (학습자)")
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        MockHttpSession s = as("USER");
        mvc.perform(get("/works/1").session(s)).andExpect(status().isOk());   // 화면을 한 번 열어 토큰이 발급된 상태(실제 사용 흐름)
        mvc.perform(post("/works/1/like").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/my/account/nickname").session(s).param("nickname", "해킹닉")).andExpect(status().isForbidden());
        mvc.perform(post("/enrollments/1/cancel").session(s)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF 토큰 없는 POST는 403 (관리자)")
    void adminPostWithoutCsrfTokenIsForbidden() throws Exception {
        MockHttpSession s = as("ADMIN");
        mvc.perform(get("/admin").session(s)).andExpect(status().isOk());   // 화면을 한 번 열어 토큰이 발급된 상태(실제 사용 흐름)
        mvc.perform(post("/admin/works/1/hide").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/instructors").session(s)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰 없는 로그인 요청은 로그인되지 않는다")
    void loginWithoutCsrfTokenDoesNotAuthenticate() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/auth/login").session(s).param("loginId", "learnerB").param("password", LEARNER_B_PW));
        mvc.perform(get("/my/learning").session(s)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/401"));
    }

    @Test
    @DisplayName("GET 로그아웃은 세션을 끝내지 않고, POST(토큰 포함)만 로그아웃")
    void logoutIsPostOnly() throws Exception {
        MockHttpSession s = as("USER");
        mvc.perform(get("/auth/logout").session(s));
        mvc.perform(get("/my/learning").session(s)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").session(s).with(csrf())).andExpect(redirectedUrl("/"));
    }

    // ───────── 소유자·담당자 검사 (읽기·쓰기 모두 거부) ─────────

    @Test
    @DisplayName("다른 학습자의 비공개 자원은 열리지 않는다")
    void learnerCannotReachOthersResources() throws Exception {
        MockHttpSession s = login("learnerC", LEARNER_C_PW);   // learnerB의 자원을 learnerC가 연다
        mvc.perform(get("/works/3").session(s)).andExpect(status().isForbidden());          // learnerB 비공개 최종 과제
        mvc.perform(get("/files/works/3").session(s)).andExpect(status().isForbidden());    // 작품 3의 이미지 (work_files id 3)
        mvc.perform(get("/my/works/2/edit").session(s)).andExpect(status().isNotFound());   // learnerB 일반 작품 — 남의 것이라 404
        mvc.perform(get("/inquiries/1").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/assignments/1/result").session(s)).andExpect(status().isNotFound());
        mvc.perform(post("/enrollments/1/cancel").session(s).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/my/works/3/visibility").session(s).with(csrf()).param("visibility", "PUBLIC"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 작품은 다른 학습자도 볼 수 있다 (대조군)")
    void learnerCanReachPublicWork() throws Exception {
        mvc.perform(get("/works/1").session(login("learnerC", LEARNER_C_PW))).andExpect(status().isOk());   // learnerB 공개 작품
    }

    @Test
    @DisplayName("다른 강사 담당 리뷰는 열리지 않는다")
    void instructorCannotReachOthersReview() throws Exception {
        MockHttpSession s = login("teacherB", TEACHER_B_PW);   // 평가 회차 2 = teacherA 담당 분반(4)의 평가 대기
        mvc.perform(get("/instructor/reviews/2").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/instructor/reviews/2/approve").session(s).with(csrf())).andExpect(status().isForbidden());
    }

    // ───────── 계정 상태 ─────────

    @Test
    @DisplayName("비활성 강사·미인증 학습자는 로그인되지 않는다")
    void inactiveOrUnverifiedCannotLogin() throws Exception {
        mvc.perform(post("/auth/login").with(csrf()).param("loginId", "teacherC").param("password", TEACHER_C_PW))
                .andExpect(redirectedUrl("/auth/login?error=disabled"));
        mvc.perform(post("/auth/login").with(csrf()).param("loginId", "newbie").param("password", NEWBIE_PW))
                .andExpect(redirectedUrl("/auth/login?error=unverified"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 비활성·미인증 계정도 아이디·비밀번호 불일치와 같은 안내 — 계정 존재·상태를 드러내지 않는다")
    void wrongPasswordHidesAccountStatus() throws Exception {
        mvc.perform(post("/auth/login").with(csrf()).param("loginId", "teacherC").param("password", "wrong"))
                .andExpect(redirectedUrl("/auth/login?error"));
        mvc.perform(post("/auth/login").with(csrf()).param("loginId", "newbie").param("password", "wrong"))
                .andExpect(redirectedUrl("/auth/login?error"));
        mvc.perform(post("/auth/login").with(csrf()).param("loginId", "nosuchuser").param("password", "wrong"))
                .andExpect(redirectedUrl("/auth/login?error"));
    }

    @Test
    @DisplayName("임시 비밀번호 강사는 비밀번호 변경 화면으로만 이동")
    void temporaryPasswordInstructorIsForcedToChange() throws Exception {
        MockHttpSession s = login("teacherD", TEACHER_D_PW);
        mvc.perform(get("/instructor").session(s)).andExpect(redirectedUrl("/auth/instructor/password"));
        mvc.perform(get("/works").session(s)).andExpect(redirectedUrl("/auth/instructor/password"));
        mvc.perform(get("/auth/instructor/password").session(s)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 아직 발급되지 않은 세션의 POST도 처리되지 않는다 (세션 만료 안내로 이동)")
    void postBeforeTokenIssuedIsBlocked() throws Exception {
        MockHttpSession s = as("USER");
        mvc.perform(post("/my/account/nickname").session(s).param("nickname", "해킹닉"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/401-expired"));
    }
}
