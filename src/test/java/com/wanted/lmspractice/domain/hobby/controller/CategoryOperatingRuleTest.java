package com.wanted.lmspractice.domain.hobby.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
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
 * [역할] "운영 중 대분류에는 운영 중인 세부 취미가 1개 이상 있어야 한다"(05 §3)와 조건이 깨졌을 때의 재설정 표시(D-31) 테스트
 * [근거] 체크리스트 ② 검증 후 팀 결정 — 세부 취미가 없는 대분류를 운영 중으로 저장하면 학습자에게 빈 대분류가 보였다
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 시드: 미술·드로잉(1, 운영 — 색연필화 1·디지털 드로잉 2 운영),
 *        공예(3, 준비 중 — 목공 4 등 모두 준비 중). 각 테스트는 롤백된다.
 * 🟡 미학습 기술: @SpringBootTest·MockMvc·spring-security-test user() (작업분배 §5 허용)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryOperatingRuleTest {

    private static final long ART = 1L, CRAFT = 3L, COLORED_PENCIL = 1L, DIGITAL_DRAWING = 2L, WOODWORK = 4L;

    @Autowired
    MockMvc mvc;
    @Autowired
    HobbyService hobbyService;
    @Autowired
    UserRepository userRepository;

    private RequestPostProcessor admin() {
        return user(new AuthDetails(userRepository.findByLoginId("admin").orElseThrow()));
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("서비스: 새 대분류·운영 중 세부 취미가 없는 대분류는 운영 중으로 만들 수 없고, 운영 중 세부 취미가 생기면 된다")
    void serviceRejectsOperatingWithoutOperatingHobby() {
        assertThatThrownBy(() -> hobbyService.createCategory(new CategoryFormDTO("음악", ExposureState.OPERATING)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CATEGORY_NEEDS_OPERATING_HOBBY));
        assertThatThrownBy(() -> hobbyService.updateCategory(CRAFT, new CategoryFormDTO("공예", ExposureState.OPERATING)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CATEGORY_NEEDS_OPERATING_HOBBY));
        assertThat(hobbyService.getCategory(CRAFT, Role.ADMIN).getState()).isEqualTo(ExposureState.PREPARING);  // 그대로

        hobbyService.changeHobbyState(WOODWORK, ExposureState.OPERATING);
        hobbyService.updateCategory(CRAFT, new CategoryFormDTO("공예", ExposureState.OPERATING));
        assertThat(hobbyService.getCategory(CRAFT, Role.ADMIN).getState()).isEqualTo(ExposureState.OPERATING);
    }

    @Test
    @DisplayName("D-31: 운영 중 대분류의 세부 취미가 모두 운영 중이 아니게 되면 자동으로 바꾸지 않고 재설정 필요로 표시한다. 이름만 편집은 막지 않는다")
    void brokenConditionIsFlaggedNotAutoChanged() {
        assertThat(hobbyService.categoryNeedingStateReset(ART)).isNull();

        hobbyService.changeHobbyState(COLORED_PENCIL, ExposureState.PREPARING);
        hobbyService.changeHobbyState(DIGITAL_DRAWING, ExposureState.PREPARING);

        CategoryDTO art = hobbyService.getCategory(ART, Role.ADMIN);
        assertThat(art.getState()).isEqualTo(ExposureState.OPERATING);   // 자동 변경 없음
        assertThat(art.isStateResetNeeded()).isTrue();
        assertThat(hobbyService.categoryNeedingStateReset(ART)).isEqualTo("미술·드로잉");
        hobbyService.updateCategory(ART, new CategoryFormDTO("미술·그림", ExposureState.OPERATING));  // 이미 운영 중 → 이름 편집 허용
    }

    @Test
    @DisplayName("화면: 운영 중으로 대분류 추가 → 노출 상태 칸 오류, 상태 칸 [편집] → 오류 안내 후 상태 그대로")
    void adminScreensShowReason() throws Exception {
        mvc.perform(post("/admin/categories").with(admin()).with(csrf())
                        .param("name", "음악").param("state", "OPERATING"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("categoryFormDTO", "state", "needsHobby"));

        mvc.perform(post("/admin/categories/" + CRAFT).with(admin()).with(csrf())
                        .param("name", "공예").param("state", "OPERATING"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("categoryFormDTO", "state", "needsHobby"));

        mvc.perform(post("/admin/hobbies/categories/" + CRAFT + "/state").with(admin()).with(csrf())
                        .param("state", "OPERATING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", ErrorCode.CATEGORY_NEEDS_OPERATING_HOBBY.getMessage()));
        assertThat(hobbyService.getCategory(CRAFT, Role.ADMIN).getState()).isEqualTo(ExposureState.PREPARING);
    }

    @Test
    @DisplayName("화면: 세부 취미 상태를 바꿔 조건이 깨지면 안내 문구가 붙고, 관리자 목록에 '상태 재설정 필요'가 보인다")
    void hobbyChangeShowsResetNotice() throws Exception {
        hobbyService.changeHobbyState(COLORED_PENCIL, ExposureState.PREPARING);

        mvc.perform(post("/admin/hobbies/" + DIGITAL_DRAWING + "/state").with(admin()).with(csrf())
                        .param("state", "PREPARING").param("categoryState", "OPERATING"))
                .andExpect(flash().attribute("message", Matchers.containsString("대분류 상태 재설정 필요")));

        mvc.perform(get("/admin/hobbies").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("상태 재설정 필요")));
    }
}
