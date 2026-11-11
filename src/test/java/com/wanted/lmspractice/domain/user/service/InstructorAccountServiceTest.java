package com.wanted.lmspractice.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.auth.dto.PasswordChangeDTO;
import com.wanted.lmspractice.domain.user.dto.InstructorCreateDTO;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] InstructorAccountService 핵심 규칙 테스트 — 생성·재발급·비활성, 취미 권한 부여·회수(C3), 첫 로그인 비밀번호 변경
 * [담당] 팀원 1 · #05 (이슈 완료 조건 "서비스 테스트: 생성·재발급·비활성")
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다.
 *       시드: teacherA(id 6, 취미 1·2 권한), 취미 1 색연필화·3 디저트 만들기(운영 중), learner(USER).
 *       각 테스트는 @Transactional이라 끝나면 롤백된다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class InstructorAccountServiceTest {

    private static final long TEACHER_A_ID = 6L;
    private static final long COLORED_PENCIL_ID = 1L;
    private static final long DESSERT_ID = 3L;

    @Autowired
    private InstructorAccountService instructorAccountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SessionRegistry sessionRegistry;

    /** 시드와 겹치지 않는 강사 생성 입력 */
    private InstructorCreateDTO validDto() {
        return new InstructorCreateDTO("newTeacher1", "김강사", "New.Teacher1@Example.com", List.of(COLORED_PENCIL_ID));
    }

    private PasswordChangeDTO passwordDto(String password) {
        return new PasswordChangeDTO(password, password);
    }

    @Test
    @DisplayName("강사를 만들면 즉시 활성·변경 강제 상태로 저장되고, 반환된 임시 비밀번호가 해시와 맞는다")
    void create() {
        String tempPassword = instructorAccountService.create(validDto());

        User saved = userRepository.findByLoginId("newTeacher1").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);       // 메일 인증 없이 바로 로그인(D-51)
        assertThat(saved.requiresPasswordChange()).isTrue();               // 첫 로그인 변경 강제
        assertThat(saved.getEmail()).isEqualTo("new.teacher1@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo(tempPassword);   // 평문 저장 안 함
        assertThat(passwordEncoder.matches(tempPassword, saved.getPasswordHash())).isTrue();
        assertThat(tempPassword).hasSize(InstructorAccountService.TEMP_PASSWORD_LENGTH)
                .matches(".*[A-Za-z].*").matches(".*\\d.*").matches(".*[^A-Za-z0-9].*");
        assertThat(instructorAccountService.hasHobbyGrant(saved.getId(), COLORED_PENCIL_ID)).isTrue();
    }

    @Test
    @DisplayName("이미 있는 아이디·이메일로 만들면 DUPLICATE_LOGIN_ID·DUPLICATE_EMAIL")
    void duplicate() {
        InstructorCreateDTO sameId = validDto();
        sameId.setLoginId("teacherA");
        assertThatThrownBy(() -> instructorAccountService.create(sameId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);

        InstructorCreateDTO sameEmail = validDto();
        sameEmail.setEmail("TeacherA@nolibang.local");  // 대소문자만 달라도 중복
        assertThatThrownBy(() -> instructorAccountService.create(sameEmail))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("비공개 취미 권한을 함께 주면 HOBBY_NOT_FOUND로 생성 전체가 취소된다")
    void createWithPrivateHobby() {
        InstructorCreateDTO dto = validDto();
        dto.setHobbyIds(List.of(999L));
        assertThatThrownBy(() -> instructorAccountService.create(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HOBBY_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호를 바꾼 뒤 재발급하면 새 임시 비밀번호로 바뀌고 다시 변경이 강제된다")
    void reissueAfterChange() {
        String first = instructorAccountService.create(validDto());
        Long id = userRepository.findByLoginId("newTeacher1").orElseThrow().getId();
        instructorAccountService.changeInitialPassword(id, passwordDto("NewPass1!"));
        assertThat(userRepository.findById(id).orElseThrow().requiresPasswordChange()).isFalse();

        String second = instructorAccountService.reissue(id);

        User user = userRepository.findById(id).orElseThrow();
        assertThat(user.requiresPasswordChange()).isTrue();
        assertThat(passwordEncoder.matches(second, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(first, user.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("NewPass1!", user.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("비활성화하면 INACTIVE(로그인 불가), 다시 활성화하면 ACTIVE")
    void changeStatus() {
        instructorAccountService.changeStatus(TEACHER_A_ID, false);
        assertThat(userRepository.findById(TEACHER_A_ID).orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);

        instructorAccountService.changeStatus(TEACHER_A_ID, true);
        assertThat(userRepository.findById(TEACHER_A_ID).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("비활성화하면 이미 로그인한 그 강사의 세션이 만료되고, 다른 회원의 세션은 그대로다")
    void changeStatusExpiresLoggedInSessions() {
        User teacher = userRepository.findById(TEACHER_A_ID).orElseThrow();
        User learner = userRepository.findByLoginId("learner").orElseThrow();
        AuthDetails teacherPrincipal = new AuthDetails(teacher);
        AuthDetails learnerPrincipal = new AuthDetails(learner);
        sessionRegistry.registerNewSession("teacher-session", teacherPrincipal);
        sessionRegistry.registerNewSession("learner-session", learnerPrincipal);

        instructorAccountService.changeStatus(TEACHER_A_ID, false);

        assertThat(sessionRegistry.getSessionInformation("teacher-session").isExpired()).isTrue();
        assertThat(sessionRegistry.getSessionInformation("learner-session").isExpired()).isFalse();
    }

    @Test
    @DisplayName("활성화로 바꿀 때는 세션을 건드리지 않는다")
    void activateKeepsSessions() {
        sessionRegistry.registerNewSession("teacher-session", new AuthDetails(userRepository.findById(TEACHER_A_ID).orElseThrow()));

        instructorAccountService.changeStatus(TEACHER_A_ID, true);

        assertThat(sessionRegistry.getSessionInformation("teacher-session").isExpired()).isFalse();
    }

    @Test
    @DisplayName("강사가 아닌 회원 id로 재발급·상태 변경하면 USER_NOT_FOUND")
    void notInstructor() {
        Long learnerId = userRepository.findByLoginId("learner").orElseThrow().getId();
        assertThatThrownBy(() -> instructorAccountService.reissue(learnerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> instructorAccountService.changeStatus(learnerId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("취미 권한 부여·회수·재부여가 hasHobbyGrant(C3)와 목록에 반영된다")
    void grantAndRevoke() {
        assertThat(instructorAccountService.hasHobbyGrant(TEACHER_A_ID, DESSERT_ID)).isFalse();

        instructorAccountService.grantHobby(TEACHER_A_ID, DESSERT_ID);
        instructorAccountService.grantHobby(TEACHER_A_ID, DESSERT_ID);  // 이미 있으면 무시
        assertThat(instructorAccountService.hasHobbyGrant(TEACHER_A_ID, DESSERT_ID)).isTrue();

        instructorAccountService.revokeHobby(TEACHER_A_ID, DESSERT_ID);
        assertThat(instructorAccountService.hasHobbyGrant(TEACHER_A_ID, DESSERT_ID)).isFalse();

        instructorAccountService.grantHobby(TEACHER_A_ID, DESSERT_ID);  // 회수한 권한 되살리기(같은 기본키)
        InstructorDTO teacherA = instructorAccountService.getInstructors("teacherA").stream()
                .filter(dto -> dto.getId().equals(TEACHER_A_ID)).findFirst().orElseThrow();
        assertThat(teacherA.hasHobby(DESSERT_ID)).isTrue();
    }

    @Test
    @DisplayName("없는 취미로 부여·회수하면 HOBBY_NOT_FOUND (AI-05·06)")
    void unknownHobby() {
        assertThatThrownBy(() -> instructorAccountService.grantHobby(TEACHER_A_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HOBBY_NOT_FOUND);
        assertThatThrownBy(() -> instructorAccountService.revokeHobby(TEACHER_A_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HOBBY_NOT_FOUND);
    }

    @Test
    @DisplayName("새 비밀번호가 아이디·임시 비밀번호와 같으면 INVALID_NEW_PASSWORD")
    void unusableNewPassword() {
        String tempPassword = instructorAccountService.create(validDto());
        Long id = userRepository.findByLoginId("newTeacher1").orElseThrow().getId();

        assertThat(instructorAccountService.isUsableNewPassword(id, tempPassword)).isFalse();
        assertThat(instructorAccountService.isUsableNewPassword(id, "NEWTEACHER1")).isFalse();  // 대소문자만 다른 아이디
        assertThatThrownBy(() -> instructorAccountService.changeInitialPassword(id, passwordDto(tempPassword)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NEW_PASSWORD);
        assertThat(userRepository.findById(id).orElseThrow().requiresPasswordChange()).isTrue();
    }
}
