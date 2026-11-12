package com.wanted.lmspractice.domain.course.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 신규 탐색에는 숨겨진 강의(닫힌 강의·준비 중 취미의 강의)의 상세를, 수강·대기 기록이 있는 학습자만 열 수 있는지 확인 (D-20)
 * [근거] 체크리스트 ② "비공개 강의의 기존 수강생 접근" 검증에서 발견 — 나의 학습 카드·학습 사이드 메뉴의 [강의 상세] 링크가
 *        강의가 닫히면 404가 됐다. 차시·영상·과제는 이미 열려 있었다(PreparingHobbyEnrolledAccessTest).
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 시드: learnerB — 강의 8(디저트 응용) 수료, 강의 1(색연필화 기초) 수강 중 / learnerA — 수강·대기 기록 없음.
 *        상태 변경은 네이티브 SQL로 하고 각 테스트는 롤백된다(쓰기 트랜잭션 안이라 취미 목록 캐시를 거치지 않는다).
 * 🟡 미학습 기술: @SpringBootTest·MockMvc·spring-security-test user() (작업분배 §5 허용)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseDetailEnrolledAccessTest {

    private static final long COMPLETED_COURSE = 8L, LEARNING_COURSE = 1L, HOBBY = 1L;   // 강의 1의 취미 = 색연필화(1)

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;
    @PersistenceContext
    EntityManager entityManager;

    private RequestPostProcessor as(String loginId) {
        return user(new AuthDetails(userRepository.findByLoginId(loginId).orElseThrow()));
    }

    private void sql(String statement) {
        entityManager.createNativeQuery(statement).executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("공개 강의는 지금처럼 열리고 안내 문구 표시(hiddenForEnrolled)는 없다")
    void openCourseIsUnchanged() throws Exception {
        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("learnerB")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hiddenForEnrolled", false));
    }

    @Test
    @DisplayName("닫힌 강의: 수강 중·수료한 학습자는 상세를 열고 자기 상태가 보인다. 기록 없는 학습자·비회원은 404")
    void closedCourseOpensOnlyForLearnersWithRecord() throws Exception {
        sql("UPDATE courses SET status = 'CLOSED' WHERE id IN (" + COMPLETED_COURSE + ", " + LEARNING_COURSE + ")");

        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("learnerB")))
                .andExpect(status().isOk())
                .andExpect(view().name("course/detail"))
                .andExpect(model().attribute("hiddenForEnrolled", true))
                .andExpect(model().attribute("enrollStatus",
                        Matchers.hasProperty("state", Matchers.is(EnrollmentStatusDTO.LEARNING))));
        mvc.perform(get("/courses/" + COMPLETED_COURSE).with(as("learnerB")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("enrollStatus",
                        Matchers.hasProperty("state", Matchers.is(EnrollmentStatusDTO.COMPLETED))));

        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("learnerA"))).andExpect(status().isNotFound());
        mvc.perform(get("/courses/" + LEARNING_COURSE)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("준비 중 취미의 강의: 수강 중인 학습자는 상세를 열고, 기록 없는 학습자는 404")
    void preparingHobbyCourseOpensOnlyForLearnersWithRecord() throws Exception {
        sql("UPDATE hobbies SET state = 'PREPARING' WHERE id = " + HOBBY);

        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("learnerB")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hiddenForEnrolled", true))
                .andExpect(model().attribute("enrollStatus",
                        Matchers.hasProperty("state", Matchers.is(EnrollmentStatusDTO.LEARNING))));
        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("learnerA"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("강사·관리자는 이 예외 경로를 타지 않는다 — 강사에게 닫힌 강의는 지금처럼 404")
    void staffDoNotUseEnrolledPath() throws Exception {
        sql("UPDATE courses SET status = 'CLOSED' WHERE id = " + LEARNING_COURSE);

        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("teacherA"))).andExpect(status().isNotFound());
        mvc.perform(get("/courses/" + LEARNING_COURSE).with(as("admin")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hiddenForEnrolled", false));
    }
}
