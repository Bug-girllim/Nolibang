package com.wanted.lmspractice.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
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
 * [역할] 강의 목록·상세 조립 테스트 — 수준 필터, 차시, 정원 현황·대기 인원, 내 신청 상태(비회원·학습자·강사·준비 중 취미), 없는 강의
 * [담당] 팀원 2 · #08   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 조회 전용이라 DB를 바꾸지 않는다.
 *       시드: 강의 9개(취미 3개 × 입문·중급·고급, 차시 3개씩). 강의 4(디지털 드로잉 기초)는 정원 1명이 차서 만석이고 대기 1명.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class CourseCatalogServiceTest {

    // 시드 id: 취미 1 색연필화, 2 디지털 드로잉, 4 목공(준비 중) / 강의 1 색연필화 기초, 4 디지털 드로잉 기초(만석)
    @Autowired
    private CourseCatalogService courseCatalogService;

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("강의 상세는 필수 3차시와 준비물·정원 현황을 담는다 (강의 9개가 같은 템플릿에 맞는 구조)")
    void courseDetailHasThreeLessonsAndSeats() {
        CourseDetailDTO course = courseCatalogService.getCourseDetail(1L, null);

        assertThat(course.getTitle()).isEqualTo("색연필화 기초");
        assertThat(course.getHobbyName()).isEqualTo("색연필화");
        assertThat(course.getLevel()).isEqualTo(CourseLevel.BASIC);
        assertThat(course.getLessons()).extracting(lesson -> lesson.getLessonNo()).containsExactly(1, 2, 3);
        assertThat(course.getLessons()).allSatisfy(lesson -> assertThat(lesson.getDurationMinutes()).isPositive());
        assertThat(course.getSupplies()).isNotEmpty();
        assertThat(course.getEnrollmentDays()).isEqualTo(30);
        // 시드: 강의 1은 분반 2개(정원 5+5, 활성 1명) → 남은 자리 9석
        assertThat(course.getSeats().getTotalCapacity()).isEqualTo(10);
        assertThat(course.getSeats().getRemainingSeats()).isEqualTo(9);
    }

    @Test
    @DisplayName("만석 강의는 남은 자리 0석·대기 인원이 보인다")
    void fullCourseShowsWaiting() {
        CourseDetailDTO course = courseCatalogService.getCourseDetail(4L, null);

        assertThat(course.getSeats().isFull()).isTrue();
        assertThat(course.getSeats().getWaitingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 강의는 COURSE_NOT_FOUND")
    void unknownCourse() {
        assertThatThrownBy(() -> courseCatalogService.getCourseDetail(9999L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    @DisplayName("강의 목록: level이 없으면 3개 수준 전체, 있으면 그 수준만")
    void courseListLevelFilter() {
        List<CourseDTO> all = courseCatalogService.getCourses(1L, null, null, null);
        List<CourseDTO> basic = courseCatalogService.getCourses(1L, CourseLevel.BASIC, null, null);

        assertThat(all).extracting(CourseDTO::getLevel)
                .containsExactly(CourseLevel.BASIC, CourseLevel.INTERMEDIATE, CourseLevel.ADVANCED);
        assertThat(basic).hasSize(1);
        assertThat(basic.get(0).getTitle()).isEqualTo("색연필화 기초");
    }

    @Test
    @DisplayName("내 신청 상태: 비회원은 로그인 안내, 강사·관리자는 학습자 전용, 학습자는 수강 도메인(#09 스텁)의 결과")
    void enrollStatusByViewer() {
        assertThat(stateOf(null, null)).isEqualTo(EnrollmentStatusDTO.LOGIN_REQUIRED);
        assertThat(stateOf(6L, Role.INSTRUCTOR)).isEqualTo(EnrollmentStatusDTO.NOT_LEARNER);
        assertThat(stateOf(1L, Role.ADMIN)).isEqualTo(EnrollmentStatusDTO.NOT_LEARNER);
        // #09 구현 전 스텁은 개인 수강 기록을 보지 않고 정원 현황만으로 정한다(빈자리 → CAN_APPLY). #09가 실제 상태를 돌려주면 이 기대값을 바꾼다.
        assertThat(stateOf(2L, Role.USER)).isEqualTo(EnrollmentStatusDTO.CAN_APPLY);
    }

    @Test
    @DisplayName("만석이거나 대기자가 있는 강의는 학습자에게 대기 신청으로 보인다 (D-52)")
    void fullCourseShowsWaitApply() {
        CourseDetailDTO full = courseCatalogService.getCourseDetail(4L, Role.USER);  // 정원 1명 만석 + 대기 1명

        assertThat(courseCatalogService.getEnrollStatus(full, 2L, Role.USER).getState())
                .isEqualTo(EnrollmentStatusDTO.CAN_WAIT);
    }

    private String stateOf(Long userId, Role role) {
        CourseDetailDTO course = courseCatalogService.getCourseDetail(1L, role);
        return courseCatalogService.getEnrollStatus(course, userId, role).getState();
    }

    @Test
    @DisplayName("준비 중 취미(목공)는 강의가 없고 신청 불가 — 신청 버튼이 나오지 않는다")
    void preparingHobbyHasNoApply() {
        HobbyDetailDTO hobby = courseCatalogService.getHobbyDetail(4L, 2L, Role.USER);

        assertThat(hobby.isOperating()).isFalse();
        assertThat(hobby.getLevels()).isEmpty();
    }

    @Test
    @DisplayName("세부 취미 상세는 수준 3개 강의와 각 신청 상태를 담는다")
    void hobbyDetailHasThreeLevels() {
        HobbyDetailDTO hobby = courseCatalogService.getHobbyDetail(1L, null, null);

        assertThat(hobby.getLevels()).hasSize(3);
        assertThat(hobby.getLevels()).allSatisfy(course -> {
            assertThat(course.getEnrollStatus().getState()).isEqualTo(EnrollmentStatusDTO.LOGIN_REQUIRED);
            assertThat(course.getSeats()).isNotNull();
        });
    }

    @Test
    @DisplayName("비공개 취미의 강의 목록은 관리자가 아니면 HOBBY_NOT_FOUND")
    void privateHobbyCourseListHidden() {
        assertThatThrownBy(() -> courseCatalogService.getCourses(8L, null, 2L, Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("관리자 취미 조회: 비공개 포함 4개 대분류, 취미별 공개/전체 강의 수와 차시 수")
    void adminCategoriesWithCounts() {
        List<CategoryDTO> categories = courseCatalogService.getAdminCategories();

        assertThat(categories).hasSize(4);
        var pencil = categories.get(0).getHobbies().get(0);
        assertThat(pencil.getName()).isEqualTo("색연필화");
        assertThat(pencil.getOpenCourseCount()).isEqualTo(3);
        assertThat(pencil.getTotalCourseCount()).isEqualTo(3);
        assertThat(pencil.getLessonCount()).isEqualTo(9);
        assertThat(categories.get(0).getOpenCourseCount()).isEqualTo(6);  // 미술·드로잉: 색연필화 3 + 디지털 드로잉 3
    }
}
