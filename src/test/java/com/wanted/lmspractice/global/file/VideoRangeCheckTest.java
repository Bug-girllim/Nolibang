package com.wanted.lmspractice.global.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * [역할] 범위 밖·잘못된 Range 요청은 본문 없는 416, 정상 범위는 지금처럼 206 — VideoRangeCheck 와 학습자 영상 주소(/videos)
 * [근거] 체크리스트 ④ 검증에서 발견 — 범위 밖 요청에 416 과 함께 영상 파일 전체(약 6MB)를 본문으로 보냈다
 * [전제] MockMvc 부분은 로컬 MySQL(nolibang)에 seed.sql 적재, 저장소의 uploads/ 영상 파일 사용. 조회만 한다.
 *        시드: learnerB 수강 1(강의 1 색연필화 기초, 기간 안 ACTIVE) — 차시 3은 그 강의의 1차시
 *        (uploads/videos/drawing/색연필화/색연필화_입문_1차시_10분.mp4)
 * 🟡 미학습 기술: @SpringBootTest·MockMvc, HttpRange
 */
@SpringBootTest
@AutoConfigureMockMvc
class VideoRangeCheckTest {

    private final Resource tenBytes = new ByteArrayResource(new byte[10]);

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("Range 가 없거나 파일 안의 범위면 null(스프링이 200/206 처리), 범위 밖·잘못된 형식이면 본문 없는 416")
    void unit() throws Exception {
        assertThat(VideoRangeCheck.unsatisfiableOrNull(null, tenBytes)).isNull();
        assertThat(VideoRangeCheck.unsatisfiableOrNull("bytes=0-4", tenBytes)).isNull();
        assertThat(VideoRangeCheck.unsatisfiableOrNull("bytes=5-", tenBytes)).isNull();

        for (String bad : new String[] {"bytes=10-", "bytes=999-1000", "items=0-1", "bytes=abc"}) {
            ResponseEntity<Resource> response = VideoRangeCheck.unsatisfiableOrNull(bad, tenBytes);
            assertThat(response).as(bad).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
            assertThat(response.getBody()).isNull();
        }
    }

    @Test
    @DisplayName("학습자 영상: 정상 범위는 206, 범위 밖은 본문 없이 416")
    void lessonVideo() throws Exception {
        AuthDetails learner = new AuthDetails(userRepository.findByLoginId("learnerB").orElseThrow());

        mvc.perform(get("/videos/3").param("enrollmentId", "1").with(user(learner)).header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent());

        MvcResult result = mvc.perform(get("/videos/3").param("enrollmentId", "1").with(user(learner))
                        .header(HttpHeaders.RANGE, "bytes=999999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, org.hamcrest.Matchers.startsWith("bytes */")))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }
}
