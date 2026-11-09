package com.wanted.lmspractice.domain.hobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] HobbyService 노출 규칙 테스트 — 비회원·학습자·강사·관리자별로 운영/준비 중/비공개가 05 §3과 같게 보이는지 확인
 * [담당] 팀원 2 · #08 (이슈 완료 조건 "운영/준비 중/비공개 노출이 05 §3과 일치", "비공개 취미 직접 URL 접근 거부")
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다.
 *       시드: 미술·드로잉(운영)·요리·베이킹(운영)·공예(준비 중, 취미 4개)·사진·영상(비공개, 취미 2개). 조회 전용 테스트라 DB를 바꾸지 않는다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class HobbyServiceTest {

    // 시드 id: 1 색연필화(운영) · 4 목공(준비 중, 공예) · 8 사진 촬영(비공개, 사진·영상)
    private static final long OPERATING_HOBBY = 1L;
    private static final long PREPARING_HOBBY = 4L;
    private static final long PRIVATE_HOBBY = 8L;

    @Autowired
    private HobbyService hobbyService;

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("비회원·학습자는 운영 중 대분류만 보고, 강사는 준비 중(공예)까지, 비공개(사진·영상)는 관리자만 본다")
    void visibleCategoriesByRole() {
        for (Role role : new Role[] {null, Role.USER}) {
            List<CategoryDTO> categories = hobbyService.getCategories(role);

            assertThat(categories).as("role=%s", role).extracting(CategoryDTO::getName).containsExactly("미술·드로잉", "요리·베이킹");
            assertThat(categories).extracting(CategoryDTO::getState)
                    .containsExactly(ExposureState.OPERATING, ExposureState.OPERATING);
        }
        List<CategoryDTO> forInstructor = hobbyService.getCategories(Role.INSTRUCTOR);
        assertThat(forInstructor).extracting(CategoryDTO::getName).containsExactly("미술·드로잉", "요리·베이킹", "공예");
        assertThat(forInstructor).extracting(CategoryDTO::getState)
                .containsExactly(ExposureState.OPERATING, ExposureState.OPERATING, ExposureState.PREPARING);
    }

    @Test
    @DisplayName("관리자는 비공개 대분류와 그 아래 세부 취미까지 모두 본다")
    void adminSeesEverything() {
        List<CategoryDTO> categories = hobbyService.getCategories(Role.ADMIN);

        assertThat(categories).extracting(CategoryDTO::getName)
                .containsExactly("미술·드로잉", "요리·베이킹", "공예", "사진·영상");
        CategoryDTO photo = categories.get(3);
        assertThat(photo.getHobbies()).hasSize(2);
    }

    @Test
    @DisplayName("준비 중 대분류(공예)는 강사·관리자에게만 보이고 운영 중이 아니라 신청 대상이 아니다, 학습자·비회원은 HOBBY_NOT_FOUND")
    void preparingCategoryOnlyForStaff() {
        for (Role role : new Role[] {Role.INSTRUCTOR, Role.ADMIN}) {
            CategoryDTO craft = hobbyService.getCategory(3L, role);

            assertThat(craft.getHobbies()).as("role=%s", role).hasSize(4);
            assertThat(craft.getHobbies()).allSatisfy(hobby -> assertThat(hobby.isOperating()).isFalse());
            assertThat(craft.getOperatingCount()).isZero();
        }
        for (Role role : new Role[] {null, Role.USER}) {
            assertThatThrownBy(() -> hobbyService.getCategory(3L, role))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("비공개 취미의 URL을 학습자·비회원이 직접 열면 HOBBY_NOT_FOUND(존재를 숨김), 관리자는 열린다")
    void privateHobbyHiddenFromNonAdmin() {
        assertThatThrownBy(() -> hobbyService.getHobby(PRIVATE_HOBBY, Role.USER))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        assertThatThrownBy(() -> hobbyService.getHobby(PRIVATE_HOBBY, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        assertThatThrownBy(() -> hobbyService.getHobby(PRIVATE_HOBBY, Role.INSTRUCTOR))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));

        HobbyDetailDTO forAdmin = hobbyService.getHobby(PRIVATE_HOBBY, Role.ADMIN);
        assertThat(forAdmin.getState()).isEqualTo(ExposureState.PRIVATE);
    }

    @Test
    @DisplayName("비공개 대분류를 직접 열어도(categoryId) 관리자가 아니면 HOBBY_NOT_FOUND")
    void privateCategoryHiddenFromNonAdmin() {
        assertThatThrownBy(() -> hobbyService.getCategory(4L, Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        assertThat(hobbyService.getCategory(4L, Role.ADMIN).getName()).isEqualTo("사진·영상");
    }

    @Test
    @DisplayName("없는 취미는 HOBBY_NOT_FOUND")
    void unknownHobby() {
        assertThatThrownBy(() -> hobbyService.getHobby(9999L, Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("운영 중 취미 상세는 모두, 준비 중 취미 상세는 강사·관리자만 (학습자·비회원은 URL을 직접 열어도 HOBBY_NOT_FOUND)")
    void hobbyDetailState() {
        HobbyDetailDTO operating = hobbyService.getHobby(OPERATING_HOBBY, null);
        assertThat(operating.getName()).isEqualTo("색연필화");
        assertThat(operating.getCategoryName()).isEqualTo("미술·드로잉");
        assertThat(operating.isOperating()).isTrue();

        HobbyDetailDTO preparing = hobbyService.getHobby(PREPARING_HOBBY, Role.INSTRUCTOR);
        assertThat(preparing.getState()).isEqualTo(ExposureState.PREPARING);
        assertThat(preparing.isOperating()).isFalse();
        assertThat(hobbyService.getHobby(PREPARING_HOBBY, Role.ADMIN).getState()).isEqualTo(ExposureState.PREPARING);

        for (Role role : new Role[] {null, Role.USER}) {
            assertThatThrownBy(() -> hobbyService.getHobby(PREPARING_HOBBY, role))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        }
    }
}
