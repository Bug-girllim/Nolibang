package com.wanted.lmspractice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.enrollment.scheduler.EnrollmentExpiryScheduler;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * [역할] P0 전체 흐름 통합 시연 테스트 — 실제 서버(랜덤 포트)를 띄우고 역할별 브라우저(쿠키 분리)로 HTTP 요청을 보내
 *        이슈 #22의 시나리오 1~6이 처음부터 끝까지 이어지는지 확인한다. 결과는 docs/TEST_REPORT.md에 기록한다.
 * [담당] 전원 · #22 (진행·기록 팀원 3)   [기획] 03 §4 수용 시나리오, DEV_REVIEW §3-2 시연 경로
 * [연결]
 *  - 호출함:   모든 P0 컨트롤러(HTTP), EnrollmentExpiryScheduler.expire(30일 만료 1회 실행), JdbcTemplate(결과 확인·시드 시각 조작)
 *  - 확인 방법: 화면·redirect·오류 코드(Accept: application/json → { code })는 HTTP로, 상태값·알림·배지는 DB로 본다
 * [전제] 로컬 MySQL nolibang. ⚠ 각 시나리오 전에 DB의 표를 모두 지우고 schema.sql → seed.sql을 다시 적재한다(끝나면 다시 시드 상태).
 *        그래서 평소 `gradlew test`에서는 돌지 않고, 환경 변수 NOLIBANG_IT=true일 때만 실행된다.
 *        실행: PowerShell `$env:NOLIBANG_IT='true'; .\gradlew.bat test --tests "*P0FlowIntegrationTest"`
 * [시드 조작] 영상 60% 규칙은 실제 재생 시간으로만 쌓인다(보고 1건 최대 15초). 600초 영상이면 차시당 6분이 걸리므로
 *        시연 강의의 차시 길이만 5초로 줄여 진짜 재생 보고로 60%(3초)를 채운다. 30일 만료는 수강 시각을 31일 앞당겨 확인한다.
 * 🟡 미학습 기술: @SpringBootTest(작업분배 §5 허용), java.net.http.HttpClient·OutputCaptureExtension
 *        — 로그인 세션·redirect·동시 요청을 실제 브라우저처럼 확인해야 해서 사용. 배운 MockMvc는 spring-security-test가 없어
 *          로그인 세션을 다루기 어렵고, 실제 동시 요청(시나리오 6)을 만들 수 없다. 인증 메일 링크는 SMTP가 없을 때 서버 로그에만 남아 로그를 읽는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.show-sql=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
@EnabledIfEnvironmentVariable(named = "NOLIBANG_IT", matches = "true")
class P0FlowIntegrationTest {

    // ── 시드 id (seed.sql) ──
    private static final long ADMIN = 1L, LEARNER = 2L, LEARNER2 = 3L, LEARNER3 = 4L;
    private static final long TEACHER_A = 6L, TEACHER_B = 7L;
    private static final String SEED_PW = "1234";
    private static final String ADMIN_PW = "Admin!2026";

    /** 50자 이상 공식 피드백 (O-04) */
    private static final String FEEDBACK_OK =
            "명암 단계가 분명하고 형태가 안정적입니다. 빛 방향을 끝까지 유지한 점이 좋습니다. 다음에는 배경 색을 한 단계 어둡게 깔아 보세요.";
    /** 49자 — 50자 미만이면 확정되지 않아야 한다 */
    private static final String FEEDBACK_SHORT = "가".repeat(49);

    /** 업로드 폴더는 임시 폴더로 바꾼다 — 시드 이미지를 복사하고, 시드에 없는 영상 파일(27개)은 작은 더미 파일로 만든다 */
    private static final Path UPLOAD_DIR = createUploadDir();

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", UPLOAD_DIR::toString);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EnrollmentExpiryScheduler expiryScheduler;

    /**
     * 시나리오 안의 확인 항목은 하나가 실패해도 끝까지 진행해 실패 항목을 한 번에 모은다(TEST_REPORT 기록용).
     * 다음 단계를 진행할 수 없는 실패(로그인·재생 보고 등 도우미 안의 확인)는 바로 멈춘다.
     */
    private SoftAssertions softly;

    /** 시나리오끼리 영향을 주지 않도록 매번 시드 상태에서 시작한다 */
    @BeforeEach
    void resetBeforeScenario() {
        reloadSeed();
        softly = new SoftAssertions();
    }

    @AfterEach
    void reportScenario() {
        softly.assertAll();
    }

    /** 끝나면 로컬 DB를 깨끗한 시드 상태로 돌려놓는다 */
    @AfterAll
    void resetAfterAll() {
        reloadSeed();
    }

    // =====================================================================
    // 시나리오 1. X-01 정상 흐름
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("1. X-01 가입·인증 → 로그인 → 신청(자동 배정) → 3차시 60% → 제출 → 평가 시작 → 50자 승인 → 수료·배지·알림 → 강사 통계")
    void scenario1_happyPath(CapturedOutput output) throws Exception {
        // 1-1. 회원가입 → 인증 안내 (자동 로그인 없음, F-01)
        Web guest = new Web();
        softly.assertThat(guest.get("/signup").status).isEqualTo(200);
        Res signup = guest.post("/signup", form("loginId", "itlearner", "password", "Test!1234", "passwordConfirm", "Test!1234",
                "name", "통합시험", "email", "itlearner@nolibang.local", "nickname", ""));
        softly.assertThat(signup.location()).isEqualTo("/auth/verify/sent");
        long me = userId("itlearner");
        softly.assertThat(userStatus(me)).isEqualTo("PENDING_EMAIL");

        // 1-2. 인증 전 로그인 → 미인증 안내
        softly.assertThat(new Web().login("itlearner", "Test!1234")).isEqualTo("/auth/login?error=unverified");

        // 1-3. 메일 링크(SMTP 없음 → 서버 로그)로 인증
        String token = lastMatch(output.toString(), "/auth/verify\\?token=([A-Za-z0-9_-]+)");
        Res verified = guest.get("/auth/verify?token=" + token);
        softly.assertThat(verified.status).isEqualTo(200);
        softly.assertThat(userStatus(me)).isEqualTo("ACTIVE");
        softly.assertThat(guest.get("/auth/verify?token=" + token).body.contains(ErrorCode.INVALID_TOKEN.getMessage())).as("인증 링크는 1회용").isTrue(); // 1회용

        // 1-4. 첫 로그인 → 취미 탐색(FR-003)
        Web learner = new Web();
        softly.assertThat(learner.login("itlearner", "Test!1234")).isEqualTo("/hobbies");
        softly.assertThat(learner.get("/hobbies").status).isEqualTo(200);
        softly.assertThat(learner.get("/courses/1").status).isEqualTo(200);

        // 1-5. 수강 신청 → 남은 자리가 가장 많은 분반 자동 배정(D-52): 강의 1 A분반 4석 남음 / B분반 5석 남음 → B분반(teacherB)
        Res apply = learner.post("/enrollments", form("courseId", "1"));
        softly.assertThat(apply.location()).isEqualTo("/courses/1");
        long enrollmentId = jdbc.queryForObject(
                "SELECT id FROM enrollments WHERE user_id = ? AND course_id = 1 AND status = 'ACTIVE'", Long.class, me);
        softly.assertThat(jdbc.queryForObject("SELECT section_id FROM enrollments WHERE id = ?", Long.class, enrollmentId)).isEqualTo(2L);
        softly.assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(DAY, confirmed_at, expires_at) FROM enrollments WHERE id = ?",
                Integer.class, enrollmentId)).isEqualTo(30);
        softly.assertThat(learner.get("/my/learning").status).isEqualTo(200);

        // 1-6. 3차시를 모두 보기 전에는 제출 불가
        shortenLessons(1);
        List<Long> lessons = lessonIds(1);
        Res early = learner.postMultipart("/assignments", form("enrollmentId", String.valueOf(enrollmentId), "title", "이른 제출"), 1);
        shows(early, ErrorCode.LESSONS_NOT_COMPLETED.getMessage(), "flash 안내");
        softly.assertThat(roundCount(enrollmentId)).isZero();

        // 1-7. 차시 화면·영상 열람 + 실제 재생 60% (59%까지는 미완료, 위치만 건너뛰면 적립 안 됨)
        softly.assertThat(learner.get("/lessons/" + lessons.get(0) + "?enrollmentId=" + enrollmentId).status).isEqualTo(200);
        softly.assertThat(learner.get("/videos/" + lessons.get(0) + "?enrollmentId=" + enrollmentId).status).isEqualTo(200);
        skipAhead(learner, lessons.get(0), enrollmentId);             // 1초 재생 후 끝으로 건너뛰기 → 약 1초만 인정
        softly.assertThat(completedAt(enrollmentId, lessons.get(0))).isNull();
        play(learner, lessons.get(0), enrollmentId, 2);                // 누적 약 3초 = 60%
        softly.assertThat(completedAt(enrollmentId, lessons.get(0))).isNotNull();
        play(learner, lessons.get(1), enrollmentId, 2);                // 2초 = 40% → 아직 미완료
        softly.assertThat(completedAt(enrollmentId, lessons.get(1))).isNull();
        play(learner, lessons.get(1), enrollmentId, 2);                // 누적 4초 = 80%
        play(learner, lessons.get(2), enrollmentId, 4);
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_progresses WHERE enrollment_id = ? AND completed_at IS NOT NULL",
                Integer.class, enrollmentId)).isEqualTo(3);

        // 1-8. 최종 과제 제출 → 평가 대기, 마감 = 제출 + 7일, 담당 강사(teacherB)에게 제출 알림
        softly.assertThat(learner.get("/assignments?enrollmentId=" + enrollmentId).status).isEqualTo(200);
        Res submit = learner.postMultipart("/assignments",
                form("enrollmentId", String.valueOf(enrollmentId), "title", "통합 시험 정물", "description", "사과 정물입니다."), 2);
        softly.assertThat(submit.location()).isEqualTo("/assignments?enrollmentId=" + enrollmentId);
        long roundId = lastRoundId(enrollmentId);
        softly.assertThat(roundStatus(roundId)).isEqualTo("WAITING");
        softly.assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(DAY, submitted_at, due_at) FROM review_rounds WHERE id = ?",
                Integer.class, roundId)).isEqualTo(7);
        softly.assertThat(notificationCount(TEACHER_B, "REVIEW_SUBMITTED", "review_round_id", roundId)).isEqualTo(1);

        // 1-9. 평가 시작 전에는 수정 가능
        Res edit = learner.postMultipart("/assignments/" + roundId + "/edit",
                form("enrollmentId", String.valueOf(enrollmentId), "title", "통합 시험 정물(수정)", "description", "수정본"), 1);
        shows(edit, "제출물을 수정했습니다", "flash 안내");

        // 1-10. 강사 평가 시작 → 학습자 수정 잠금
        Web teacherB = new Web();
        softly.assertThat(teacherB.login("teacherB", SEED_PW)).isEqualTo("/instructor");
        softly.assertThat(teacherB.get("/instructor/reviews").status).isEqualTo(200);
        softly.assertThat(teacherB.get("/instructor/reviews/" + roundId).status).isEqualTo(200);
        softly.assertThat(teacherB.post("/instructor/reviews/" + roundId + "/start", form()).location())
                .isEqualTo("/instructor/reviews/" + roundId);
        softly.assertThat(roundStatus(roundId)).isEqualTo("IN_REVIEW");
        Res locked = learner.postMultipart("/assignments/" + roundId + "/edit",
                form("enrollmentId", String.valueOf(enrollmentId), "title", "잠금 후 수정"), 1);
        shows(locked, ErrorCode.SUBMISSION_LOCKED.getMessage(), "flash 안내");

        // 1-11. 49자 피드백은 확정 안 됨 → 50자 이상 승인
        Res shortApprove = teacherB.post("/instructor/reviews/" + roundId + "/approve", form("content", FEEDBACK_SHORT));
        softly.assertThat(shortApprove.status).isEqualTo(200);
        softly.assertThat(roundStatus(roundId)).isEqualTo("IN_REVIEW");
        Res approve = teacherB.post("/instructor/reviews/" + roundId + "/approve", form("content", FEEDBACK_OK));
        softly.assertThat(approve.location()).isEqualTo("/instructor/reviews?tab=DONE");

        // 1-12. 수료·배지·알림
        softly.assertThat(roundStatus(roundId)).isEqualTo("APPROVED");
        softly.assertThat(enrollmentStatus(enrollmentId)).isEqualTo("COMPLETED");
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM v_user_level_badges WHERE user_id = ? AND hobby_id = 1 AND level = 1",
                Integer.class, me)).isEqualTo(1);
        softly.assertThat(notificationCount(me, "REVIEW_APPROVED", "review_round_id", roundId)).isEqualTo(1);
        softly.assertThat(notificationCount(me, "COURSE_COMPLETED", "enrollment_id", enrollmentId)).isEqualTo(1);
        softly.assertThat(learner.get("/assignments/" + roundId + "/result").status).isEqualTo(200);
        softly.assertThat(learner.get("/notifications").status).isEqualTo(200);
        softly.assertThat(Long.parseLong(learner.get("/notifications/unread-count").body.trim())).isGreaterThanOrEqualTo(2);
        softly.assertThat(learner.get("/my/learning").status).isEqualTo(200);
        softly.assertThat(learner.get("/lessons/" + lessons.get(0) + "?enrollmentId=" + enrollmentId).status).isEqualTo(200); // 수료 후 다시 보기

        // 1-13. 강사 통계 — B분반 수료 1명
        softly.assertThat(teacherB.get("/instructor").status).isEqualTo(200);
        softly.assertThat(teacherB.get("/instructor/stats").status).isEqualTo(200);
        softly.assertThat(teacherB.get("/instructor/students").status).isEqualTo(200);
        softly.assertThat(jdbc.queryForObject("SELECT completed_count FROM v_section_stats_screen WHERE section_id = 2", Integer.class))
                .isEqualTo(1);
    }

    // =====================================================================
    // 시나리오 2. 대기열 → 자동 확정
    // =====================================================================

    @Test
    @Order(2)
    @DisplayName("2. 만석 강의 대기 → 다른 학습자 수료·취소 → 대기 1순위 자동 확정 + 알림")
    void scenario2_waitlist() {
        // 강의 4(디지털 드로잉 기초): A분반 정원 1 = learner2(e5, 평가 진행 중)로 만석, 대기 1번 = learner3(w2)
        Web learner = loginAs("learner", SEED_PW);
        Web learner2 = loginAs("learner2", SEED_PW);
        Web learner3 = loginAs("learner3", SEED_PW);

        // 2-1. 만석 → 대기 2번 등록, 중복 대기 불가
        softly.assertThat(learner.post("/enrollments", form("courseId", "4")).location()).isEqualTo("/courses/4");
        softly.assertThat(waitingIds(4)).containsExactly(LEARNER3, LEARNER);
        Res again = learner.post("/enrollments", form("courseId", "4"));
        shows(again, ErrorCode.ALREADY_WAITING.getMessage(), "flash 안내");
        softly.assertThat(learner.get("/courses/4").status).isEqualTo(200);

        // 2-2. 평가 진행 중인 수강은 취소 불가(자리를 비우지 못함)
        Res blocked = learner2.post("/enrollments/5/cancel", form());
        shows(blocked, ErrorCode.CANCEL_NOT_ALLOWED_DURING_REVIEW.getMessage(), "취소 불가 안내(flash errorMessage)가 보여야 한다");
        softly.assertThat(enrollmentStatus(5)).isEqualTo("ACTIVE");

        // 2-3. learner2 수료 승인 → 자리 반환 → 대기 1순위 learner3 자동 확정 + 알림
        Web teacherA = loginAs("teacherA", SEED_PW);
        softly.assertThat(teacherA.post("/instructor/reviews/4/approve", form("content", FEEDBACK_OK)).location())
                .isEqualTo("/instructor/reviews?tab=DONE");
        softly.assertThat(enrollmentStatus(5)).isEqualTo("COMPLETED");
        Long learner3Enrollment = activeEnrollmentId(LEARNER3, 4);
        softly.assertThat(learner3Enrollment).isNotNull();
        softly.assertThat(jdbc.queryForObject("SELECT status FROM waitlist_entries WHERE id = 2", String.class)).isEqualTo("CONFIRMED");
        softly.assertThat(notificationCount(LEARNER3, "WAITLIST_CONFIRMED", "enrollment_id", learner3Enrollment)).isEqualTo(1);
        softly.assertThat(waitingIds(4)).containsExactly(LEARNER);   // learner는 이제 대기 1번
        softly.assertThat(activeEnrollmentId(LEARNER, 4)).isNull();

        // 2-4. learner3 수강 취소 → 다음 1순위 learner 자동 확정 + 알림, learner3는 재신청 불가(D-55)
        Res cancel = learner3.post("/enrollments/" + learner3Enrollment + "/cancel", form());
        softly.assertThat(cancel.location()).isEqualTo("/my/learning");
        shows(cancel, "수강을 취소했습니다", "수강 취소 완료 안내(flash message)가 U-S08에 보여야 한다");
        softly.assertThat(enrollmentStatus(learner3Enrollment)).isEqualTo("CANCELLED");
        Long learnerEnrollment = activeEnrollmentId(LEARNER, 4);
        softly.assertThat(learnerEnrollment).isNotNull();
        softly.assertThat(notificationCount(LEARNER, "WAITLIST_CONFIRMED", "enrollment_id", learnerEnrollment)).isEqualTo(1);
        softly.assertThat(waitingIds(4)).isEmpty();
        Res reapply = learner3.post("/enrollments", form("courseId", "4"));
        shows(reapply, ErrorCode.CANNOT_REAPPLY.getMessage(), "flash 안내");

        // 2-5. 알림함·관리자 대기열 화면
        softly.assertThat(learner.get("/notifications").status).isEqualTo(200);
        softly.assertThat(loginAs("admin", ADMIN_PW).get("/admin/waitlists?courseId=4").status).isEqualTo(200);
    }

    // =====================================================================
    // 시나리오 3. 반려·재제출, 30일 만료, 만료 후 평가
    // =====================================================================

    @Test
    @Order(3)
    @DisplayName("3. 반려 → 재제출 → 다시 반려, 30일 만료(시각 조작), 만료 후 평가(승인은 수료 / 반려는 종료)")
    void scenario3_rejectResubmitExpiry() {
        Web learner = loginAs("learner", SEED_PW);
        Web teacherA = loginAs("teacherA", SEED_PW);
        Web teacherB = loginAs("teacherB", SEED_PW);

        // 3-1. 반려 결과 확인 → 정식 재제출(새 회차, 새 마감 7일)
        softly.assertThat(learner.get("/assignments/2/result").status).isEqualTo(200);
        softly.assertThat(learner.get("/assignments?enrollmentId=3").status).isEqualTo(200);
        Res resubmit = learner.postMultipart("/assignments/resubmit",
                form("enrollmentId", "3", "title", "색연필화 심화 — 재제출", "description", "채도 변화를 넣었습니다."), 1);
        shows(resubmit, "재제출되었습니다", "flash 안내");
        long round2 = lastRoundId(3);
        softly.assertThat(jdbc.queryForObject("SELECT round_no FROM review_rounds WHERE id = ?", Integer.class, round2)).isEqualTo(2);
        softly.assertThat(roundStatus(round2)).isEqualTo("WAITING");

        // 3-2. 반려 사유 49자는 확정 안 됨 → 50자 이상 반려 + 알림 → 다시 재제출
        teacherA.post("/instructor/reviews/" + round2 + "/start", form());
        softly.assertThat(teacherA.post("/instructor/reviews/" + round2 + "/reject", form("content", FEEDBACK_SHORT)).status).isEqualTo(200);
        softly.assertThat(roundStatus(round2)).isEqualTo("IN_REVIEW");
        softly.assertThat(teacherA.post("/instructor/reviews/" + round2 + "/reject", form("content", FEEDBACK_OK)).location())
                .isEqualTo("/instructor/reviews?tab=DONE");
        softly.assertThat(roundStatus(round2)).isEqualTo("REJECTED");
        softly.assertThat(notificationCount(LEARNER, "REVIEW_REJECTED", "review_round_id", round2)).isEqualTo(1);
        learner.postMultipart("/assignments/resubmit", form("enrollmentId", "3", "title", "색연필화 심화 — 3회차"), 1);
        long round3 = lastRoundId(3);
        softly.assertThat(roundStatus(round3)).isEqualTo("WAITING");

        // 3-3. 30일 만료 — 평가 대기 중인 수강(e3·e4)은 만료하지 않고, 제출 없는 수강(e6)은 만료 + 자리 반환
        shiftEnrollmentPast(3);
        shiftEnrollmentPast(4);
        shiftEnrollmentPast(6);
        expiryScheduler.expire();
        softly.assertThat(enrollmentStatus(3)).isEqualTo("ACTIVE");
        softly.assertThat(enrollmentStatus(4)).isEqualTo("ACTIVE");
        softly.assertThat(enrollmentStatus(6)).isEqualTo("EXPIRED");
        softly.assertThat(jdbc.queryForObject("SELECT active_count FROM v_section_seats WHERE section_id = 1", Integer.class)).isZero();

        // 3-4. 만료한 학습자: 영상·진도 차단, 재신청 불가
        Web learner3 = loginAs("learner3", SEED_PW);
        long course1Lesson = lessonIds(1).get(0);
        softly.assertThat(learner3.getJson("/videos/" + course1Lesson + "?enrollmentId=6").code())
                .isEqualTo(ErrorCode.VIDEO_ACCESS_DENIED.name());
        softly.assertThat(learner3.postJson("/lessons/" + course1Lesson + "/progress",
                form("enrollmentId", "6", "streamKey", "s-exp", "seq", "0", "playing", "true", "positionSec", "0")).code())
                .isEqualTo(ErrorCode.ENROLLMENT_EXPIRED.name());
        Res reapply = learner3.post("/enrollments", form("courseId", "1"));
        shows(reapply, ErrorCode.CANNOT_REAPPLY.getMessage(), "flash 안내");

        // 3-5. 만료 후 평가 — 승인: e4(teacherB) 수료
        teacherB.post("/instructor/reviews/3/start", form());
        softly.assertThat(teacherB.post("/instructor/reviews/3/approve", form("content", FEEDBACK_OK)).location())
                .isEqualTo("/instructor/reviews?tab=DONE");
        softly.assertThat(enrollmentStatus(4)).isEqualTo("COMPLETED");
        softly.assertThat(notificationCount(LEARNER, "COURSE_COMPLETED", "enrollment_id", 4L)).isEqualTo(1);

        // 3-6. 만료 후 평가 — 반려: e3는 재제출 기회 없이 종료(EXPIRED), 재제출 차단
        teacherA.post("/instructor/reviews/" + round3 + "/start", form());
        teacherA.post("/instructor/reviews/" + round3 + "/reject", form("content", FEEDBACK_OK));
        softly.assertThat(roundStatus(round3)).isEqualTo("REJECTED");
        softly.assertThat(enrollmentStatus(3)).isEqualTo("EXPIRED");
        Res late = learner.postMultipart("/assignments/resubmit", form("enrollmentId", "3", "title", "만료 후 재제출"), 1);
        softly.assertThat(late.status).isIn(302, 403, 404, 409);
        softly.assertThat(roundCount(3)).isEqualTo(3);
        softly.assertThat(learner.get("/my/learning").status).isEqualTo(200);
    }

    // =====================================================================
    // 시나리오 4. 관리자: 강사 생성 → 첫 로그인 변경 → 취미 권한 → 분반 담당 → 담당 변경 후 평가 승계
    // =====================================================================

    @Test
    @Order(4)
    @DisplayName("4. 강사 생성 → 첫 로그인 비밀번호 변경 → 취미 권한 → 분반 담당 지정 → 담당 변경 후 평가 승계")
    void scenario4_adminInstructorFlow() {
        Web admin = loginAs("admin", ADMIN_PW);
        softly.assertThat(admin.get("/admin").status).isEqualTo(200);

        // 4-1. 강사 생성(취미 1 권한) → 임시 비밀번호 1회 표시
        Res created = admin.post("/admin/instructors",
                form("loginId", "teacherE", "name", "신강사", "email", "teachere@nolibang.local", "hobbyIds", "1"));
        softly.assertThat(created.location()).isEqualTo("/admin/instructors");
        String tempPassword = htmlUnescape(lastMatch(created.next(), "임시 비밀번호 <b>([^<]+)</b>"));
        softly.assertThat(admin.get("/admin/instructors").body.contains(tempPassword)).as("임시 비밀번호는 다시 볼 수 없다").isFalse();   // 다시 볼 수 없음
        long teacherE = userId("teacherE");
        softly.assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE id = ?", Boolean.class, teacherE)).isTrue();

        // 4-2. 첫 로그인 → 비밀번호 변경 전에는 다른 화면 불가 → 변경 후 강사 홈
        Web teacher = new Web();
        softly.assertThat(teacher.login("teacherE", tempPassword)).isEqualTo("/auth/instructor/password");
        softly.assertThat(teacher.get("/instructor").location()).isEqualTo("/auth/instructor/password");
        softly.assertThat(teacher.get("/notifications").location()).isEqualTo("/auth/instructor/password");
        softly.assertThat(teacher.get("/auth/instructor/password").status).isEqualTo(200);
        softly.assertThat(teacher.post("/auth/instructor/password",
                form("newPassword", "short", "newPasswordConfirm", "short")).status).isEqualTo(200);   // 규칙 위반 → 같은 화면
        softly.assertThat(teacher.post("/auth/instructor/password",
                form("newPassword", "NewPass!234", "newPasswordConfirm", "NewPass!234")).location()).isEqualTo("/instructor");
        softly.assertThat(teacher.get("/instructor").status).isEqualTo(200);
        softly.assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE id = ?", Boolean.class, teacherE)).isFalse();
        softly.assertThat(new Web().login("teacherE", tempPassword)).isEqualTo("/auth/login?error");   // 임시 비밀번호는 더 못 씀
        teacher = loginAs("teacherE", "NewPass!234");

        // 4-3. 권한 없는 취미(디저트) 분반 담당은 거부, 취미 권한(디지털 드로잉) 추가
        softly.assertThat(admin.get("/admin/sections?courseId=2").status).isEqualTo(200);
        Res notGranted = admin.postJson("/admin/sections/9/instructor", form("instructorId", String.valueOf(teacherE)));
        softly.assertThat(notGranted.code()).isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED.name());
        softly.assertThat(admin.post("/admin/instructors/" + teacherE + "/hobbies", form("hobbyId", "2")).location())
                .isEqualTo("/admin/instructors?tab=hobby");
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instructor_hobby_grants WHERE instructor_id = ?", Integer.class, teacherE))
                .isEqualTo(2);

        // 4-4. 분반 담당 지정 — 새 분반 생성(강의 2) + 배정 알림
        Res newSection = admin.post("/admin/sections",
                form("courseId", "2", "name", "C분반", "capacity", "5", "instructorId", String.valueOf(teacherE)));
        softly.assertThat(newSection.location()).isEqualTo("/admin/sections?courseId=2");
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_sections WHERE course_id = 2 AND instructor_id = ?",
                Integer.class, teacherE)).isEqualTo(1);

        // 4-5. 담당 강사 없는 분반은 배정 제외: 강의 6 B분반(담당 없음)이 아니라 A분반(teacherA)으로 배정
        Web learner = loginAs("learner", SEED_PW);
        learner.post("/enrollments", form("courseId", "6"));
        long enrollmentId = activeEnrollmentId(LEARNER, 6);
        softly.assertThat(jdbc.queryForObject("SELECT section_id FROM enrollments WHERE id = ?", Long.class, enrollmentId)).isEqualTo(7L);
        completeAllLessons(enrollmentId, 6);   // 이 시나리오의 대상은 평가 승계라 진도는 DB로 채운다(진도 규칙은 시나리오 1)
        learner.postMultipart("/assignments", form("enrollmentId", String.valueOf(enrollmentId), "title", "고급 과제"), 1);
        long roundId = lastRoundId(enrollmentId);

        // 4-6. teacherA 평가 시작·초안 → 관리자가 담당을 teacherE로 변경 → teacherA는 더 처리 불가, teacherE가 이어서 승인
        Web teacherA = loginAs("teacherA", SEED_PW);
        teacherA.post("/instructor/reviews/" + roundId + "/start", form());
        teacherA.post("/instructor/reviews/" + roundId + "/draft", form("content", "초안: 레이어 정리가 좋습니다."));
        softly.assertThat(admin.post("/admin/sections/7/instructor", form("instructorId", String.valueOf(teacherE))).location())
                .isEqualTo("/admin/sections?courseId=6");
        softly.assertThat(notificationCount(teacherE, "SECTION_ASSIGNED", "section_id", 7L)).isEqualTo(1);
        softly.assertThat(teacherA.postJson("/instructor/reviews/" + roundId + "/approve", form("content", FEEDBACK_OK)).code())
                .isEqualTo(ErrorCode.NOT_ASSIGNED_INSTRUCTOR.name());
        softly.assertThat(teacher.get("/instructor/reviews?tab=IN_REVIEW").status).isEqualTo(200);
        softly.assertThat(teacher.get("/instructor/reviews/" + roundId).body.contains("초안: 레이어 정리가 좋습니다.")).as("이전 강사 초안 승계").isTrue();   // 초안 승계
        softly.assertThat(teacher.post("/instructor/reviews/" + roundId + "/approve", form("content", FEEDBACK_OK)).location())
                .isEqualTo("/instructor/reviews?tab=DONE");
        softly.assertThat(roundStatus(roundId)).isEqualTo("APPROVED");
        softly.assertThat(jdbc.queryForObject("SELECT reviewer_id FROM review_rounds WHERE id = ?", Long.class, roundId)).isEqualTo(teacherE);
        softly.assertThat(enrollmentStatus(enrollmentId)).isEqualTo("COMPLETED");
    }

    // =====================================================================
    // 시나리오 5. 권한
    // =====================================================================

    @Test
    @Order(5)
    @DisplayName("5. 역할별 URL 차단, 타인 비공개 이미지·영상 차단, 재신청 차단, 로그인 불가 계정")
    void scenario5_permissions() {
        Web anonymous = new Web();
        Web learner = loginAs("learner", SEED_PW);
        Web learner2 = loginAs("learner2", SEED_PW);
        Web teacherA = loginAs("teacherA", SEED_PW);
        Web teacherB = loginAs("teacherB", SEED_PW);
        Web admin = loginAs("admin", ADMIN_PW);

        // 5-1. 역할별 URL 차단
        softly.assertThat(anonymous.get("/my/learning").location()).endsWith("/auth/login");
        softly.assertThat(anonymous.get("/instructor").location()).endsWith("/auth/login");
        softly.assertThat(anonymous.get("/courses/1").status).isEqualTo(200);   // 탐색은 공개
        softly.assertThat(learner.get("/instructor").status).isEqualTo(403);
        softly.assertThat(learner.get("/admin").status).isEqualTo(403);
        softly.assertThat(teacherA.get("/admin/sections").status).isEqualTo(403);
        softly.assertThat(teacherA.get("/my/learning").status).isEqualTo(403);
        softly.assertThat(teacherA.post("/enrollments", form("courseId", "2")).status).as("권한 없는 POST도 403").isEqualTo(403);
        softly.assertThat(admin.get("/my/learning").status).isEqualTo(403);
        softly.assertThat(admin.get("/instructor").status).isEqualTo(403);

        // 5-2. 로그인 불가 계정
        softly.assertThat(new Web().login("newbie", SEED_PW)).isEqualTo("/auth/login?error=unverified");
        softly.assertThat(new Web().login("teacherC", SEED_PW)).isEqualTo("/auth/login?error=disabled");
        softly.assertThat(new Web().login("learner", "wrong-pw")).isEqualTo("/auth/login?error");

        // 5-3. 작품 이미지 권한 — 본인 비공개 O, 타인 비공개 X, 공개 O, 관리자 숨김은 타인 X
        long privateFile = fileIdOfWork(2), publicFile = fileIdOfWork(1), hiddenFile = fileIdOfWork(5);
        softly.assertThat(learner.get("/files/works/" + privateFile).status).isEqualTo(200);
        softly.assertThat(learner2.get("/files/works/" + privateFile).status).isIn(403, 404);
        softly.assertThat(learner2.get("/files/works/" + publicFile).status).isEqualTo(200);
        softly.assertThat(learner2.get("/files/works/" + hiddenFile).status).isIn(403, 404);
        softly.assertThat(learner.get("/works/2").status).isEqualTo(200);
        softly.assertThat(learner2.get("/works/2").status).isIn(403, 404);

        // 5-4. 영상·차시 권한 — 남의 수강 id로 접근하면 존재를 숨기고 404
        long course3Lesson = lessonIds(3).get(0);
        softly.assertThat(learner.get("/videos/" + course3Lesson + "?enrollmentId=3").status).isEqualTo(200);
        softly.assertThat(learner2.getJson("/videos/" + course3Lesson + "?enrollmentId=3").code())
                .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND.name());
        softly.assertThat(learner2.get("/lessons/" + course3Lesson + "?enrollmentId=3").status).isEqualTo(404);
        softly.assertThat(learner2.getJson("/videos/" + course3Lesson + "?enrollmentId=5").status).isIn(403, 404);   // 내 수강 + 다른 강의 차시
        softly.assertThat(learner2.get("/assignments?enrollmentId=3").status).isEqualTo(404);

        // 5-5. 담당이 아닌 강사는 평가 화면·처리 불가
        softly.assertThat(teacherB.getJson("/instructor/reviews/4").status).isIn(403, 404);
        softly.assertThat(teacherB.postJson("/instructor/reviews/3/start", form()).status).isEqualTo(302);   // 본인 담당은 가능
        softly.assertThat(teacherA.postJson("/instructor/reviews/3/approve", form("content", FEEDBACK_OK)).status).isIn(403, 404);
        softly.assertThat(roundStatus(3)).isEqualTo("IN_REVIEW");

        // 5-6. 재신청 차단 — 수료(e1)·취소한 강의는 다시 신청 불가, 수강 중이면 중복 신청 불가
        Res completed = learner.post("/enrollments", form("courseId", "1"));
        shows(completed, ErrorCode.CANNOT_REAPPLY.getMessage(), "flash 안내");
        Res active = learner.post("/enrollments", form("courseId", "7"));
        shows(active, ErrorCode.ALREADY_ENROLLED.getMessage(), "flash 안내");
        softly.assertThat(learner.post("/enrollments/2/cancel", form()).location()).isEqualTo("/my/learning");
        softly.assertThat(enrollmentStatus(2)).isEqualTo("CANCELLED");
        Res cancelled = learner.post("/enrollments", form("courseId", "2"));
        shows(cancelled, ErrorCode.CANNOT_REAPPLY.getMessage(), "flash 안내");
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ? AND course_id = 2", Integer.class, LEARNER))
                .isEqualTo(1);
    }

    // =====================================================================
    // 시나리오 6. 동시성
    // =====================================================================

    @Test
    @Order(6)
    @DisplayName("6. 마지막 자리 동시 신청은 1명만 확정, 승인 연타는 1번만 처리")
    void scenario6_concurrency() throws Exception {
        // 6-1. 강의 8(디저트 응용) 정원을 1로 줄이고 학습자 3명이 동시에 신청
        Web admin = loginAs("admin", ADMIN_PW);
        softly.assertThat(admin.post("/admin/sections/10/capacity", form("capacity", "1")).location())
                .isEqualTo("/admin/sections?courseId=8");
        List<Web> learners = List.of(loginAs("learner", SEED_PW), loginAs("learner2", SEED_PW), loginAs("learner3", SEED_PW));
        List<Res> results = concurrently(learners.stream()
                .map(web -> (Callable<Res>) () -> web.post("/enrollments", form("courseId", "8"))).toList());
        softly.assertThat(results).allSatisfy(res -> softly.assertThat(res.status).isEqualTo(302));
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE course_id = 8 AND status = 'ACTIVE'", Integer.class))
                .isEqualTo(1);
        softly.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM waitlist_entries WHERE course_id = 8 AND status = 'WAITING'", Integer.class))
                .isEqualTo(2);

        // 6-2. 같은 과제(e4, teacherB)에 승인 버튼을 동시에 두 번 → 1건만 성공, 수료·알림도 1번
        Web teacherB = loginAs("teacherB", SEED_PW);
        teacherB.post("/instructor/reviews/3/start", form());
        List<Res> approvals = concurrently(List.of(
                () -> teacherB.postJson("/instructor/reviews/3/approve", form("content", FEEDBACK_OK)),
                () -> teacherB.postJson("/instructor/reviews/3/approve", form("content", FEEDBACK_OK))));
        softly.assertThat(approvals).filteredOn(res -> res.status == 302).hasSize(1);
        softly.assertThat(approvals).filteredOn(res -> res.status != 302)
                .allSatisfy(res -> softly.assertThat(res.code()).isEqualTo(ErrorCode.INVALID_REVIEW_STATE.name()));
        softly.assertThat(roundStatus(3)).isEqualTo("APPROVED");
        softly.assertThat(enrollmentStatus(4)).isEqualTo("COMPLETED");
        softly.assertThat(notificationCount(LEARNER, "REVIEW_APPROVED", "review_round_id", 3L)).isEqualTo(1);
        softly.assertThat(notificationCount(LEARNER, "COURSE_COMPLETED", "enrollment_id", 4L)).isEqualTo(1);
    }

    // =====================================================================
    // 도우미 — 재생 보고
    // =====================================================================

    /** 실제 재생처럼 1초마다 재생 보고를 보낸다(새 재생 키). seconds초 동안 위치도 1초씩 앞으로 간다. 마지막엔 일시정지 보고 */
    private void play(Web web, long lessonId, long enrollmentId, int seconds) throws InterruptedException {
        String streamKey = UUID.randomUUID().toString();
        double position = currentPosition(enrollmentId, lessonId);
        report(web, lessonId, enrollmentId, streamKey, 0, true, position);
        for (int seq = 1; seq <= seconds; seq++) {
            Thread.sleep(1050);
            position += 1.0;
            report(web, lessonId, enrollmentId, streamKey, seq, true, position);
        }
        report(web, lessonId, enrollmentId, streamKey, seconds + 1, false, position);
    }

    /** 1초 재생한 뒤 위치를 끝(5초)으로 건너뛴다 — 실제 재생한 1초만 인정되어야 한다 */
    private void skipAhead(Web web, long lessonId, long enrollmentId) throws InterruptedException {
        String streamKey = UUID.randomUUID().toString();
        report(web, lessonId, enrollmentId, streamKey, 0, true, 0);
        Thread.sleep(1050);
        report(web, lessonId, enrollmentId, streamKey, 1, true, 5);
        report(web, lessonId, enrollmentId, streamKey, 2, false, 5);
        // 건너뛴 위치에서 다시 이어 보도록 기록 위치를 처음으로 돌린다(다음 play가 0초부터 1초씩 진행)
        jdbc.update("UPDATE lesson_progresses SET last_position_sec = 0 WHERE enrollment_id = ? AND lesson_id = ?", enrollmentId, lessonId);
    }

    private void report(Web web, long lessonId, long enrollmentId, String streamKey, long seq, boolean playing, double position) {
        Res res = web.postJson("/lessons/" + lessonId + "/progress", form("enrollmentId", String.valueOf(enrollmentId),
                "streamKey", streamKey, "seq", String.valueOf(seq), "playing", String.valueOf(playing),
                "positionSec", String.valueOf(position)));
        assertThat(res.status).as("재생 보고 응답 %s", res.body).isEqualTo(200);
    }

    private double currentPosition(long enrollmentId, long lessonId) {
        List<Double> rows = jdbc.queryForList("SELECT last_position_sec FROM lesson_progresses WHERE enrollment_id = ? AND lesson_id = ?",
                Double.class, enrollmentId, lessonId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    // =====================================================================
    // 도우미 — 시드 조작·조회
    // =====================================================================

    /** 시연 강의의 차시 길이를 5초로 줄인다(60% = 3초). 클래스 주석 [시드 조작] 참고 */
    private void shortenLessons(long courseId) {
        jdbc.update("UPDATE lessons SET duration_sec = 5 WHERE course_id = ?", courseId);
    }

    /** 진도 규칙이 대상이 아닌 시나리오에서 3차시 완료를 DB로 채운다 */
    private void completeAllLessons(long enrollmentId, long courseId) {
        jdbc.update("""
                INSERT INTO lesson_progresses (enrollment_id, lesson_id, course_id, played_sec, completed_at, last_position_sec)
                SELECT ?, l.id, l.course_id, l.duration_sec, UTC_TIMESTAMP(6), l.duration_sec FROM lessons l WHERE l.course_id = ?""",
                enrollmentId, courseId);
    }

    /** 수강 시각을 31일 앞당겨 기간을 지나게 한다(만료 기한 CHECK = 확정 + 30일을 지키도록 두 칸을 같이 옮긴다) */
    private void shiftEnrollmentPast(long enrollmentId) {
        jdbc.update("""
                UPDATE enrollments
                SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 31 DAY, expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY
                WHERE id = ?""", enrollmentId);
    }

    private long userId(String loginId) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    private String enrollmentStatus(long enrollmentId) {
        return jdbc.queryForObject("SELECT status FROM enrollments WHERE id = ?", String.class, enrollmentId);
    }

    private Long activeEnrollmentId(long userId, long courseId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM enrollments WHERE user_id = ? AND course_id = ? AND status = 'ACTIVE'",
                Long.class, userId, courseId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Long> waitingIds(long courseId) {
        return jdbc.queryForList("SELECT user_id FROM waitlist_entries WHERE course_id = ? AND status = 'WAITING' ORDER BY created_at, id",
                Long.class, courseId);
    }

    private List<Long> lessonIds(long courseId) {
        return jdbc.queryForList("SELECT id FROM lessons WHERE course_id = ? ORDER BY lesson_no", Long.class, courseId);
    }

    private Object completedAt(long enrollmentId, long lessonId) {
        List<Object> rows = jdbc.queryForList("SELECT completed_at FROM lesson_progresses WHERE enrollment_id = ? AND lesson_id = ?",
                Object.class, enrollmentId, lessonId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int roundCount(long enrollmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM review_rounds WHERE enrollment_id = ?", Integer.class, enrollmentId);
    }

    private long lastRoundId(long enrollmentId) {
        return jdbc.queryForObject("SELECT id FROM review_rounds WHERE enrollment_id = ? ORDER BY round_no DESC LIMIT 1",
                Long.class, enrollmentId);
    }

    private String roundStatus(long roundId) {
        return jdbc.queryForObject("SELECT status FROM review_rounds WHERE id = ?", String.class, roundId);
    }

    /** targetColumn은 이 클래스 안의 고정 문자열만 넘긴다(enrollment_id·review_round_id·section_id) */
    private int notificationCount(long userId, String kind, String targetColumn, long targetId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND kind = ? AND " + targetColumn + " = ?",
                Integer.class, userId, kind, targetId);
    }

    private long fileIdOfWork(long workId) {
        return jdbc.queryForObject("""
                SELECT f.id FROM work_files f JOIN works w ON w.display_version_id = f.work_version_id
                WHERE w.id = ? ORDER BY f.sort_order LIMIT 1""", Long.class, workId);
    }

    /** 모든 표·뷰를 지우고 schema.sql → seed.sql을 다시 적재한다 */
    private void reloadSeed() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            JdbcTemplate same = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            same.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (Map<String, Object> row : same.queryForList(
                    "SELECT table_name AS name, table_type AS type FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                String kind = "VIEW".equals(row.get("type")) ? "VIEW" : "TABLE";
                same.execute("DROP " + kind + " IF EXISTS `" + row.get("name") + "`");
            }
            same.execute("SET FOREIGN_KEY_CHECKS = 1");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("sql/schema.sql"), StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("sql/seed.sql"), StandardCharsets.UTF_8));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static Path createUploadDir() {
        try {
            Path dir = Files.createTempDirectory("nolibang-it-uploads");
            Path seed = Paths.get("uploads", "seed");
            Files.createDirectories(dir.resolve("seed"));
            try (var files = Files.list(seed)) {
                for (Path file : files.toList()) {
                    Files.copy(file, dir.resolve("seed").resolve(file.getFileName()));
                }
            }
            for (int course = 1; course <= 9; course++) {
                Path videoDir = Files.createDirectories(dir.resolve("videos").resolve("course-" + course));
                for (int lesson = 1; lesson <= 3; lesson++) {
                    Files.write(videoDir.resolve("lesson-" + lesson + ".mp4"), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'});
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // =====================================================================
    // 도우미 — HTTP
    // =====================================================================

    /** 폼 POST 뒤 redirect된 화면에 안내 문구가 보이는지 — 실패 메시지에 HTML 전체가 찍히지 않도록 boolean으로 확인한다 */
    private void shows(Res res, String text, String description) {
        softly.assertThat(res.next() != null && res.next().contains(text))
                .as("%s — redirect %s 화면에 「%s」", description, res.location(), text).isTrue();
    }

    private Web loginAs(String loginId, String password) {
        Web web = new Web();
        String location = web.login(loginId, password);
        assertThat(location).as("%s 로그인", loginId).doesNotStartWith("/auth/login");
        return web;
    }

    private static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();   // 모두 준비된 뒤 한꺼번에 출발
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Map<String, String> form(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String lastMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        assertThat(last).as("패턴 %s", regex).isNotNull();
        return last;
    }

    private static String htmlUnescape(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    /**
     * 응답 한 건 — redirect 위치는 경로만 남긴다.
     * next: 화면 폼 POST가 redirect되면 브라우저처럼 바로 따라간 화면(flash message·errorMessage가 여기서 보인다)
     */
    private record Res(int status, String location, String body, String next) {
        /** Accept: application/json 요청의 오류 본문 { code, message }에서 code */
        String code() {
            Matcher matcher = Pattern.compile("\"code\"\\s*:\\s*\"(\\w+)\"").matcher(body);
            return matcher.find() ? matcher.group(1) : "HTTP_" + status;
        }
    }

    /**
     * 브라우저 한 개 — 쿠키(세션)를 따로 갖는다. 화면 폼 POST(post·postMultipart)는 브라우저처럼 redirect를 한 번 따라가
     * flash를 소비한다(따라가지 않으면 다음 화면에 이전 flash가 남는다). JSON 요청(postJson)은 따라가지 않는다.
     */
    private class Web {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String login(String loginId, String password) {
            return post("/auth/login", form("loginId", loginId, "password", password)).location();
        }

        Res get(String path) {
            return send(request(path).GET().build());
        }

        Res getJson(String path) {
            return send(request(path).header("Accept", "application/json").GET().build());
        }

        Res post(String path, Map<String, String> fields) {
            return follow(send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(fields))).build()));
        }

        private Res follow(Res res) {
            if (res.status() != 302 || res.location() == null) {
                return res;
            }
            return new Res(res.status(), res.location(), res.body(), get(res.location()).body());
        }

        Res postJson(String path, Map<String, String> fields) {
            return send(request(path).header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(fields))).build());
        }

        /** 과제 제출 폼 — 시드 PNG를 images로 imageCount장 붙인다 */
        Res postMultipart(String path, Map<String, String> fields, int imageCount) {
            String boundary = "----nolibang" + UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try {
                for (Map.Entry<String, String> field : fields.entrySet()) {
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                            + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
                byte[] png = Files.readAllBytes(UPLOAD_DIR.resolve("seed").resolve("work1-1.png"));
                for (int i = 1; i <= imageCount; i++) {
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"it-" + i
                            + ".png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    body.write(png);
                    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return follow(send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build()));
        }

        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        }

        private Res send(HttpRequest request) {
            try {
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                String location = response.headers().firstValue("Location")
                        .map(value -> value.replaceFirst("^https?://[^/]+", ""))
                        .orElse(null);
                return new Res(response.statusCode(), location, new String(response.body(), StandardCharsets.UTF_8), null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private String encode(Map<String, String> fields) {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (!out.isEmpty()) {
                    out.append('&');
                }
                out.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
            }
            return out.toString();
        }
    }
}
