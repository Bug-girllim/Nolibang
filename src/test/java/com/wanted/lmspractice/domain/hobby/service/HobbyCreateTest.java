package com.wanted.lmspractice.domain.hobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자 대분류·세부 취미 추가(A-S02·A-S03) 테스트 — 추가된 항목이 목록 맨 뒤에 보이는지, 이름 중복·노출 규칙이 맞는지
 * [담당] 팀원 2 · #08
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. @Transactional이라 테스트가 끝나면 추가한 데이터는 되돌린다.
 *       시드: 미술·드로잉(id 1, 운영)·요리·베이킹·공예·사진·영상, 세부 취미 색연필화(미술·드로잉)
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class HobbyCreateTest {

    private static final long ART_CATEGORY = 1L;

    @Autowired
    private HobbyService hobbyService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("대분류를 추가하면 관리자 목록 맨 뒤에 같은 이름·상태로 보이고, 세부 취미는 아직 없다")
    void createCategory() {
        Long id = hobbyService.createCategory(new CategoryFormDTO("  음악·악기  ", ExposureState.PRIVATE));

        List<CategoryDTO> categories = hobbyService.getCategories(Role.ADMIN);
        CategoryDTO last = categories.get(categories.size() - 1);
        assertThat(last.getId()).isEqualTo(id);
        assertThat(last.getName()).isEqualTo("음악·악기");   // 앞뒤 공백은 빼고 저장
        assertThat(last.getState()).isEqualTo(ExposureState.PRIVATE);
        assertThat(last.getHobbies()).isEmpty();
        assertThat(last.getCode()).startsWith("cat-");
    }

    @Test
    @DisplayName("정렬 순서가 127을 넘어도(DB INT) 추가·목록 조회가 된다 — 예전 TINYINT 매핑은 128 이상에서 목록 전체가 깨졌다")
    void sortOrderAbove127() {
        jdbcTemplate.update("UPDATE categories SET sort_order = 200 WHERE id = ?", ART_CATEGORY);

        Long id = hobbyService.createCategory(new CategoryFormDTO("음악·악기", ExposureState.PRIVATE));   // 200 + 1

        List<CategoryDTO> categories = hobbyService.getCategories(Role.ADMIN);
        assertThat(categories.get(categories.size() - 1).getId()).isEqualTo(id);
        assertThat(categories).extracting(CategoryDTO::getId).contains(ART_CATEGORY);
    }

    @Test
    @DisplayName("이미 있는 대분류 이름은 중복으로 본다")
    void categoryNameDuplicate() {
        assertThat(hobbyService.isCategoryNameAvailable("미술·드로잉")).isFalse();
        assertThat(hobbyService.isCategoryNameAvailable("음악·악기")).isTrue();
    }

    @Test
    @DisplayName("세부 취미를 추가하면 그 대분류의 맨 뒤에 보이고, 강의는 0개다")
    void createHobby() {
        Long id = hobbyService.createHobby(
                new HobbyFormDTO(ART_CATEGORY, "수채화", "물감으로 그리는 그림", ExposureState.OPERATING));

        CategoryDTO art = hobbyService.getCategory(ART_CATEGORY, Role.ADMIN);
        HobbyDTO last = art.getHobbies().get(art.getHobbies().size() - 1);
        assertThat(last.getId()).isEqualTo(id);
        assertThat(last.getName()).isEqualTo("수채화");
        assertThat(last.getDescription()).isEqualTo("물감으로 그리는 그림");
        assertThat(last.getState()).isEqualTo(ExposureState.OPERATING);
        assertThat(last.getCode()).startsWith("hobby-");
        // 운영 중 대분류의 운영 중 세부 취미라 비회원에게도 보인다
        assertThat(hobbyService.getCategory(ART_CATEGORY, null).getHobbies()).extracting(HobbyDTO::getName).contains("수채화");
    }

    @Test
    @DisplayName("비공개 대분류에 추가한 세부 취미는 운영 중으로 만들어도 비회원에게 보이지 않는다")
    void hobbyUnderPrivateCategoryIsHidden() {
        Long categoryId = hobbyService.createCategory(new CategoryFormDTO("음악·악기", ExposureState.PRIVATE));
        hobbyService.createHobby(new HobbyFormDTO(categoryId, "기타", null, ExposureState.OPERATING));

        assertThat(hobbyService.getCategories(null)).extracting(CategoryDTO::getId).doesNotContain(categoryId);
        assertThat(hobbyService.getCategory(categoryId, Role.ADMIN).getHobbies()).extracting(HobbyDTO::getName).containsExactly("기타");
    }

    @Test
    @DisplayName("대분류 상태를 바꾸면 노출 규칙이 바로 따라간다 (공예: 준비 중 → 운영 중이면 비회원에게 보임, 비공개면 강사에게도 숨김)")
    void changeCategoryState() {
        final long craft = 3L;     // 시드: 공예(준비 중)
        final long woodwork = 4L;  // 시드: 목공(준비 중, 공예)
        assertThat(hobbyService.getCategories(null)).extracting(CategoryDTO::getId).doesNotContain(craft);

        // 05 §3: 운영 중 세부 취미가 없으면 운영 중으로 바꿀 수 없다 → 목공을 먼저 운영 중으로
        assertThatThrownBy(() -> hobbyService.changeCategoryState(craft, ExposureState.OPERATING))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_NEEDS_OPERATING_HOBBY));
        hobbyService.changeHobbyState(woodwork, ExposureState.OPERATING);
        String name = hobbyService.changeCategoryState(craft, ExposureState.OPERATING);

        assertThat(name).isEqualTo("공예");
        assertThat(hobbyService.getCategories(null)).extracting(CategoryDTO::getId).contains(craft);

        hobbyService.changeCategoryState(craft, ExposureState.PRIVATE);
        assertThat(hobbyService.getCategories(Role.INSTRUCTOR)).extracting(CategoryDTO::getId).doesNotContain(craft);
        assertThat(hobbyService.getCategory(craft, Role.ADMIN).getState()).isEqualTo(ExposureState.PRIVATE);

        assertThatThrownBy(() -> hobbyService.changeCategoryState(999_999L, ExposureState.OPERATING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("세부 취미 상태를 바꾸면 노출 규칙이 따라간다 (색연필화 비공개 → 비회원에게 숨김, 대분류가 준비 중이면 세부 취미가 운영 중이어도 숨김)")
    void changeHobbyState() {
        final long coloredPencil = 1L;  // 시드: 색연필화(운영 중, 미술·드로잉)
        final long woodwork = 4L;       // 시드: 목공(준비 중, 공예 — 대분류 준비 중)

        String name = hobbyService.changeHobbyState(coloredPencil, ExposureState.PRIVATE);

        assertThat(name).isEqualTo("색연필화");
        assertThat(hobbyService.getCategory(ART_CATEGORY, null).getHobbies()).extracting(HobbyDTO::getId).doesNotContain(coloredPencil);
        assertThat(hobbyService.getCategory(ART_CATEGORY, Role.ADMIN).getHobbies())
                .filteredOn(hobby -> hobby.getId().equals(coloredPencil))
                .extracting(HobbyDTO::getState).containsExactly(ExposureState.PRIVATE);

        hobbyService.changeHobbyState(woodwork, ExposureState.OPERATING);
        assertThat(hobbyService.getCategories(null)).extracting(CategoryDTO::getName).doesNotContain("공예");

        assertThatThrownBy(() -> hobbyService.changeHobbyState(999_999L, ExposureState.OPERATING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("같은 대분류 안에서만 세부 취미 이름이 중복이고, 없는 대분류에는 추가할 수 없다")
    void hobbyNameDuplicateAndMissingCategory() {
        assertThat(hobbyService.isHobbyNameAvailable(ART_CATEGORY, "색연필화")).isFalse();
        assertThat(hobbyService.isHobbyNameAvailable(2L, "색연필화")).isTrue();
        assertThat(hobbyService.categoryExists(999_999L)).isFalse();

        assertThatThrownBy(() -> hobbyService.createHobby(
                new HobbyFormDTO(999_999L, "없는 대분류", null, ExposureState.PRIVATE)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }
}
