package com.wanted.lmspractice.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.dto.CourseFormDTO;
import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자 운영 메뉴 편집 기능 테스트 — 대분류 편집·삭제(A-S02), 세부 취미 편집(A-S03), 강의 편집(A-S06), 관리자 대기 취소(A-S12)
 * [담당] 팀원 2 · #08·#10
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. @Transactional이라 테스트가 끝나면 바꾼 데이터는 되돌린다.
 *       시드: 대분류 1 미술·드로잉(세부 취미 2개)·3 공예, 세부 취미 1 색연필화, 강의 1 색연필화 기초, 대기 줄 2(WAITING, 강의 4)
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class AdminEditFeaturesTest {

    @Autowired
    private HobbyService hobbyService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private WaitlistService waitlistService;

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("대분류 편집: 편집 패널은 현재 값으로 채워지고, 저장하면 이름·상태가 바뀐다. 이름 중복은 자기 자신만 빼고 본다")
    void updateCategory() {
        CategoryFormDTO current = hobbyService.getCategoryForm(1L);
        assertThat(current.getName()).isEqualTo("미술·드로잉");
        assertThat(current.getState()).isEqualTo(ExposureState.OPERATING);

        assertThat(hobbyService.isCategoryNameAvailable("미술·드로잉", 1L)).isTrue();   // 자기 이름 그대로 저장 가능
        assertThat(hobbyService.isCategoryNameAvailable("공예", 1L)).isFalse();         // 다른 대분류 이름은 중복

        hobbyService.updateCategory(1L, new CategoryFormDTO("  미술·그림  ", ExposureState.PREPARING));

        CategoryDTO changed = hobbyService.getCategory(1L, Role.ADMIN);
        assertThat(changed.getName()).isEqualTo("미술·그림");
        assertThat(changed.getState()).isEqualTo(ExposureState.PREPARING);
        assertThat(hobbyService.getCategories(null)).extracting(CategoryDTO::getId).doesNotContain(1L);
    }

    @Test
    @DisplayName("대분류 삭제: 세부 취미가 없으면 지워지고, 세부 취미가 있으면 INVALID_INPUT으로 막는다")
    void deleteCategory() {
        Long emptyId = hobbyService.createCategory(new CategoryFormDTO("지울 대분류", ExposureState.PRIVATE));

        assertThat(hobbyService.deleteCategory(emptyId)).isEqualTo("지울 대분류");
        assertThat(hobbyService.categoryExists(emptyId)).isFalse();

        assertThatThrownBy(() -> hobbyService.deleteCategory(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> hobbyService.deleteCategory(999_999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("세부 취미 편집: 대분류를 옮기고 이름·소개·상태를 바꿀 수 있다. 같은 대분류 안 이름 중복은 자기 자신만 빼고 본다")
    void updateHobby() {
        HobbyFormDTO current = hobbyService.getHobbyForm(1L);
        assertThat(current.getName()).isEqualTo("색연필화");
        assertThat(current.getCategoryId()).isEqualTo(1L);

        assertThat(hobbyService.isHobbyNameAvailable(1L, "색연필화", 1L)).isTrue();
        assertThat(hobbyService.isHobbyNameAvailable(1L, "디지털 드로잉", 1L)).isFalse();

        hobbyService.updateHobby(1L, new HobbyFormDTO(2L, "색연필 일러스트", "새 소개", ExposureState.PREPARING));

        assertThat(hobbyService.getCategory(2L, Role.ADMIN).getHobbies())
                .filteredOn(hobby -> hobby.getId().equals(1L))
                .singleElement()
                .satisfies(hobby -> {
                    assertThat(hobby.getName()).isEqualTo("색연필 일러스트");
                    assertThat(hobby.getDescription()).isEqualTo("새 소개");
                    assertThat(hobby.getState()).isEqualTo(ExposureState.PREPARING);
                });
        assertThat(hobbyService.getCategory(1L, Role.ADMIN).getHobbies()).extracting(HobbyDTO::getId).doesNotContain(1L);

        assertThatThrownBy(() -> hobbyService.updateHobby(1L, new HobbyFormDTO(999_999L, "x", null, ExposureState.PRIVATE)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("강의 편집: 내용이 바뀌고, 비공개(CLOSED)로 바꾸면 학습자 목록에서 빠지며 관리자는 계속 본다")
    void updateCourse() {
        CourseFormDTO current = courseService.getCourseForm(1L);
        assertThat(current.getTitle()).isEqualTo("색연필화 기초");
        assertThat(current.getStatus()).isEqualTo(CourseStatus.OPEN);

        String title = courseService.updateCourse(1L, new CourseFormDTO(
                " 색연필화 첫걸음 ", "기초 소개", "스케치북 A4\n색연필 24색", "첫 작품", "완성작 1장 제출", CourseStatus.CLOSED));

        assertThat(title).isEqualTo("색연필화 첫걸음");
        CourseDetailDTO detail = courseService.getCourseDetail(1L, Role.ADMIN);
        assertThat(detail.getTitle()).isEqualTo("색연필화 첫걸음");
        assertThat(detail.getSupplies()).containsExactly("스케치북 A4", "색연필 24색");
        assertThat(detail.getAssignmentTitle()).isEqualTo("첫 작품");
        assertThat(courseService.getCourseStatuses()).containsEntry(1L, CourseStatus.CLOSED);
        assertThat(courseService.getCourses(1L, null, Role.USER)).extracting(CourseDTO::getId).doesNotContain(1L);
        assertThat(courseService.getCourses(1L, null, Role.ADMIN)).extracting(CourseDTO::getId).contains(1L);

        assertThatThrownBy(() -> courseService.getCourseForm(999_999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    @DisplayName("관리자 대기 취소: 대기 중인 줄이 취소되고 그 강의 id를 돌려준다. 이미 취소된 줄은 NOT_FOUND")
    void cancelWaitlistByAdmin() {
        final long waitingEntry = 2L;  // 시드: 강의 4 대기 1순위
        assertThat(waitlistService.getWaitlist(4L)).hasSize(1);

        Long courseId = waitlistService.cancelByAdmin(waitingEntry);

        assertThat(courseId).isEqualTo(4L);
        assertThat(waitlistService.getWaitlist(4L)).isEmpty();
        assertThatThrownBy(() -> waitlistService.cancelByAdmin(waitingEntry))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
