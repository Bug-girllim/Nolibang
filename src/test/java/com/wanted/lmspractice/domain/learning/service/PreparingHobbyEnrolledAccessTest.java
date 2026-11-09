package com.wanted.lmspractice.domain.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] D-20 + 새 노출 정책 검증 — 취미가 준비 중으로 바뀌면 학습자의 탐색·신규 신청에서는 숨겨지지만,
 *        이미 수강 중인 학습자의 학습(차시 화면)·나의 학습 기록은 그대로 열린다
 * [담당] 팀원 3 · #12 (노출 정책 변경 반영)   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 변경은 테스트 트랜잭션이 롤백한다.
 *       시드: learner(2)의 수강 3 = 색연필화 심화(강의 3, 취미 1, 차시 9·8·7), 기간 안 ACTIVE.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class PreparingHobbyEnrolledAccessTest {

    private static final Long LEARNER = 2L, ENROLLMENT = 3L, COURSE = 3L, FIRST_LESSON = 9L, HOBBY = 1L;

    @Autowired
    private ProgressService progressService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private HobbyService hobbyService;
    @PersistenceContext
    private EntityManager entityManager;

    private void makeHobbyPreparing() {
        entityManager.createNativeQuery("UPDATE hobbies SET state = 'PREPARING' WHERE id = " + HOBBY).executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("취미가 준비 중이 돼도 수강 중인 학습자의 차시 화면은 그대로 열린다 (D-20)")
    void enrolledLearnerKeepsLessonAccess() {
        makeHobbyPreparing();

        assertThat(progressService.getLessonView(LEARNER, ENROLLMENT, FIRST_LESSON)).isNotNull();
        assertThat(courseService.getCourseDetailForEnrolled(COURSE).getHobbyName()).isEqualTo("색연필화");
    }

    @Test
    @DisplayName("같은 취미가 준비 중이면 학습자·비회원의 신규 탐색은 막힌다 — 강의 상세·강의 목록·취미 상세 모두 찾을 수 없음")
    void browsingIsHiddenForLearnersButNotStaff() {
        makeHobbyPreparing();

        for (Role role : new Role[] {null, Role.USER}) {
            assertThatThrownBy(() -> courseService.getCourseDetail(COURSE, role))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COURSE_NOT_FOUND));
            assertThatThrownBy(() -> courseService.getCourses(HOBBY, null, role))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
            assertThatThrownBy(() -> hobbyService.getHobby(HOBBY, role))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        }
        for (Role role : new Role[] {Role.INSTRUCTOR, Role.ADMIN}) {
            assertThat(courseService.getCourseDetail(COURSE, role)).as("role=%s", role).isNotNull();
            assertThat(hobbyService.getHobby(HOBBY, role)).isNotNull();
        }
    }
}
