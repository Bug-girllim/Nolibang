package com.wanted.lmspractice.domain.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 담당 분반이 있는 강사의 비활성화·취미 권한 회수 규칙 (AI-04·AI-06, D-52·D-59)
 *  - 평소: 담당 분반이 있으면 비활성화·(그 취미) 권한 회수를 막는다 → 관리자가 분반 담당을 먼저 바꾼다
 *  - 긴급 비활성화(갑작스러운 이탈·정지): 바로 비활성화하고 담당 분반을 담당 공백으로 → 신규 배정 제외·대시보드 담당 공백 표시
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. @Transactional이라 끝나면 롤백된다.
 *       시드: teacherD(9) 취미 2(디지털 드로잉), 강의 5(디지털 드로잉 중급)의 분반 6 하나만 담당 — 강의 5의 유일한 분반.
 *            learner2(3)는 강의 5 수강 기록이 없다. teacherA(6) 취미 1·2.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class InstructorDeactivationTest {

    private static final long TEACHER_A = 6L;
    private static final long TEACHER_D = 9L;
    private static final long HOBBY_PENCIL = 1L;
    private static final long HOBBY_DIGITAL = 2L;
    private static final long COURSE_DIGITAL_MID = 5L;
    private static final long SECTION_DIGITAL_MID_A = 6L;  // teacherD 담당
    private static final long LEARNER2 = 3L;

    @Autowired
    private SectionService sectionService;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private InstructorAccountService instructorAccountService;
    @Autowired
    private InstructorStatQuery statQuery;
    @Autowired
    private CourseSectionRepository sectionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private UserStatus statusOf(long userId) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(userId).map(User::getStatus).orElseThrow();
    }

    private Long instructorOf(long sectionId) {
        entityManager.flush();
        entityManager.clear();
        return sectionRepository.findById(sectionId).orElseThrow().getInstructorId();  // 담당 공백이면 null
    }

    @Test
    @DisplayName("담당 분반이 있는 강사는 평소 비활성화가 막히고, 계정·분반 담당은 그대로다")
    void normalDeactivationBlockedWhenAssigned() {
        assertThatThrownBy(() -> sectionService.deactivateInstructor(TEACHER_D, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_HAS_SECTIONS);

        assertThat(statusOf(TEACHER_D)).isEqualTo(UserStatus.ACTIVE);
        assertThat(instructorOf(SECTION_DIGITAL_MID_A)).isEqualTo(TEACHER_D);
    }

    @Test
    @DisplayName("긴급 비활성화하면 계정이 비활성이 되고 담당 분반이 담당 공백으로 바뀌어 대시보드에 나타난다")
    void emergencyDeactivationVacatesSections() {
        int released = sectionService.deactivateInstructor(TEACHER_D, true);

        assertThat(released).isEqualTo(1);
        assertThat(statusOf(TEACHER_D)).isEqualTo(UserStatus.INACTIVE);
        assertThat(instructorOf(SECTION_DIGITAL_MID_A)).isNull();
        assertThat(sectionService.getSection(SECTION_DIGITAL_MID_A).isVacant()).isTrue();
        assertThat(sectionService.getSectionsByInstructor(TEACHER_D)).isEmpty();
        // 관리자 대시보드 "담당 공백 분반"(A-S01)에 들어간다
        assertThat(statQuery.findVacantSections(LocalDateTime.now()))
                .extracting(SectionStatDTO::getSectionId).contains(SECTION_DIGITAL_MID_A);
    }

    @Test
    @DisplayName("긴급 비활성화로 담당 공백이 된 분반에는 신규 학습자가 배정되지 않는다 (D-52)")
    void vacatedSectionExcludedFromNewAssignment() {
        sectionService.deactivateInstructor(TEACHER_D, true);
        entityManager.flush();

        // 강의 5는 분반이 하나뿐이라 신청 가능한 분반이 없어진다
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER2, COURSE_DIGITAL_MID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NO_ASSIGNABLE_SECTION);
    }

    @Test
    @DisplayName("담당 공백이 된 분반에 새 강사를 지정하면 다시 배정 대상이 된다 (D-59)")
    void reassignAfterEmergencyDeactivation() {
        sectionService.deactivateInstructor(TEACHER_D, true);
        entityManager.flush();

        sectionService.changeInstructor(SECTION_DIGITAL_MID_A, TEACHER_A);  // teacherA는 취미 2 권한 있음

        assertThat(instructorOf(SECTION_DIGITAL_MID_A)).isEqualTo(TEACHER_A);
        assertThat(sectionService.getSection(SECTION_DIGITAL_MID_A).isVacant()).isFalse();
    }

    @Test
    @DisplayName("비활성 강사는 다시 담당으로 지정할 수 없다")
    void cannotReassignDeactivatedInstructor() {
        sectionService.deactivateInstructor(TEACHER_D, true);
        entityManager.flush();

        assertThatThrownBy(() -> sectionService.changeInstructor(SECTION_DIGITAL_MID_A, TEACHER_D))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED);
    }

    @Test
    @DisplayName("담당 분반이 없는 강사는 평소 비활성화가 바로 된다")
    void deactivateWithoutSections() {
        sectionService.deactivateInstructor(TEACHER_D, true);  // 먼저 분반을 비운다
        instructorAccountService.changeStatus(TEACHER_D, true);  // 다시 활성화

        int released = sectionService.deactivateInstructor(TEACHER_D, false);

        assertThat(released).isZero();
        assertThat(statusOf(TEACHER_D)).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("강사가 아닌 id는 USER_NOT_FOUND")
    void deactivateNonInstructor() {
        assertThatThrownBy(() -> sectionService.deactivateInstructor(LEARNER2, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("담당 중인 취미의 권한은 회수할 수 없고, 담당 분반이 없는 취미 권한은 회수된다")
    void revokeGrantBlockedOnlyForAssignedHobby() {
        assertThatThrownBy(() -> sectionService.revokeHobbyGrant(TEACHER_D, HOBBY_DIGITAL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GRANT_IN_USE);
        assertThat(instructorAccountService.hasHobbyGrant(TEACHER_D, HOBBY_DIGITAL)).isTrue();

        instructorAccountService.grantHobby(TEACHER_D, HOBBY_PENCIL);  // 담당 분반이 없는 취미
        sectionService.revokeHobbyGrant(TEACHER_D, HOBBY_PENCIL);
        assertThat(instructorAccountService.hasHobbyGrant(TEACHER_D, HOBBY_PENCIL)).isFalse();
    }
}
