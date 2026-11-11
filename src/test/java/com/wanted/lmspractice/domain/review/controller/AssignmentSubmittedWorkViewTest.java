package com.wanted.lmspractice.domain.review.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 최종 과제 화면(U-S07)의 「제출한 작품」 — 학습자가 낸 제출물(제목·이미지)을 보고, 수정·재제출 폼이 그 값으로 채워지는지
 * [근거] 체크리스트 ⑤ "과제 저장·수정·재제출·미리보기" 검증에서 발견 — 강사 평가 화면에는 제출 이미지가 보이는데
 *        학습자 화면에는 제출물이 없어 자기가 낸 내용을 확인할 수 없었다
 * [전제] 로컬 MySQL(nolibang)에 seed.sql 적재. 시드: learner 수강 4(디저트 기초) — 1회차 평가 대기, 이미지 1장 /
 *        learner 수강 2(색연필화 응용) — 아직 제출 없음. 조회만 하고 롤백한다.
 * 🟡 미학습 기술: @SpringBootTest·MockMvc·spring-security-test user()
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssignmentSubmittedWorkViewTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;
    @PersistenceContext
    EntityManager entityManager;

    private AuthDetails learner() {
        return new AuthDetails(userRepository.findByLoginId("learner").orElseThrow());
    }

    @Test
    @DisplayName("낸 과제가 있으면 「제출한 작품」에 제목·이미지가 보이고, 수정 폼 제목이 그 제목으로 채워진다")
    void showsSubmittedWork() throws Exception {
        String title = (String) entityManager.createNativeQuery(
                "SELECT v.title FROM review_rounds r JOIN work_versions v ON v.id = r.work_version_id WHERE r.enrollment_id = 4")
                .getSingleResult();

        mvc.perform(get("/assignments").param("enrollmentId", "4").with(user(learner())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("제출한 작품")))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*src=\"/files/works/\\d+\".*")))
                .andExpect(model().attribute("form", Matchers.hasProperty("title", Matchers.is(title))));
    }

    @Test
    @DisplayName("아직 낸 과제가 없으면 「제출한 작품」이 없다")
    void noSubmissionNoBox() throws Exception {
        mvc.perform(get("/assignments").param("enrollmentId", "2").with(user(learner())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"submitted-work\""))));
    }
}
