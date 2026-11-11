package com.wanted.lmspractice.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * [역할] 오류 화면(templates/error)이 실제 이유(reason)를 설명 줄에 보여 주고, 이유가 없으면 기본 문구를 그대로 보여 주는지 확인
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 요청은 조회만 한다(DB를 바꾸지 않음).
 * 🟡 미학습 기술: @SpringBootTest·MockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorPageReasonRenderTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("없는 강의·취미는 404 화면 설명 줄에 실제 이유가 보인다")
    void notFoundPagesShowReason() throws Exception {
        mvc.perform(get("/courses/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(ErrorCode.COURSE_NOT_FOUND.getMessage())))
                .andExpect(content().string(not(containsString("주소가 변경되었거나 더 이상 이용할 수 없는 페이지입니다."))));
        mvc.perform(get("/hobbies/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(ErrorCode.HOBBY_NOT_FOUND.getMessage())));
    }

    @Test
    @DisplayName("없는 주소(일반 404)는 이유 없이 화면 기본 문구를 보여 준다")
    void unknownUrlKeepsDefaultText() throws Exception {
        // 비로그인은 없는 주소도 로그인 필요 화면으로 가므로 로그인한 학습자로 요청한다
        AuthDetails learner = new AuthDetails(userRepository.findByLoginId("learner").orElseThrow());
        mvc.perform(get("/no/such/page").with(user(learner)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("주소가 변경되었거나 더 이상 이용할 수 없는 페이지입니다.")));
    }
}
