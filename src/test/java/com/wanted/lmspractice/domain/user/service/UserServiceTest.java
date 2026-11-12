package com.wanted.lmspractice.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.user.dto.SignupDTO;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] UserService.signup 핵심 규칙 테스트 — 중복 3종, 닉네임 자동 생성, 비밀번호 암호화, 미인증 상태 저장
 * [담당] 팀원 1 · #04 (이슈 완료 조건 "signup 서비스 테스트")
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. 시드 계정 learnerB(이메일 learnerb@nolibang.local, 닉네임 차분한수달)와 겹치는 값으로 중복을 검사한다.
 *       각 테스트는 @Transactional이라 끝나면 롤백되어 DB에 가입 기록이 남지 않는다.
 *       SMTP가 없으면 메일은 보내지 않고 로그로 대신하므로(EmailVerificationService) 테스트 중 메일이 나가지 않는다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 시드와 겹치지 않는 정상 가입 입력 */
    private SignupDTO validDto() {
        SignupDTO dto = new SignupDTO();
        dto.setLoginId("newlearner1");
        dto.setPassword("Passw0rd!");
        dto.setPasswordConfirm("Passw0rd!");
        dto.setName("김가입");
        dto.setEmail("New.Learner1@Example.com");
        dto.setNickname("테스트닉네임");
        return dto;
    }

    @Test
    @DisplayName("닉네임을 입력해 가입하면 미인증 상태로 저장되고 비밀번호는 암호화된다")
    void signupWithNickname() {
        Long userId = userService.signup(validDto());

        User saved = userRepository.findById(userId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL);  // 가입 직후 로그인 불가(F-01)
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getNickname()).isEqualTo("테스트닉네임");
        assertThat(saved.getEmail()).isEqualTo("new.learner1@example.com");  // 소문자로 정규화
        assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd!");
        assertThat(passwordEncoder.matches("Passw0rd!", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("닉네임을 비우면 자동 생성되고, 생성된 닉네임은 겹치지 않는다")
    void signupWithoutNicknameGeneratesOne() {
        SignupDTO dto = validDto();
        dto.setNickname("");  // 폼에서 비워 보내면 빈 문자열이 온다

        Long userId = userService.signup(dto);

        String nickname = userRepository.findById(userId).orElseThrow().getNickname();
        assertThat(nickname).isNotBlank();
        assertThat(nickname).matches(".+ \\d{1,3}");  // "형용사+동물 번호(1~999)"
    }

    @Test
    @DisplayName("이미 있는 아이디로 가입하면 DUPLICATE_LOGIN_ID")
    void duplicateLoginId() {
        SignupDTO dto = validDto();
        dto.setLoginId("learnerB");  // 시드 계정

        assertThatThrownBy(() -> userService.signup(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    @DisplayName("이미 있는 이메일(대소문자만 달라도)로 가입하면 DUPLICATE_EMAIL")
    void duplicateEmail() {
        SignupDTO dto = validDto();
        dto.setEmail("LEARNERB@nolibang.local");  // 시드 이메일과 대소문자만 다름

        assertThatThrownBy(() -> userService.signup(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("이미 있는 닉네임으로 가입하면 DUPLICATE_NICKNAME")
    void duplicateNickname() {
        SignupDTO dto = validDto();
        dto.setNickname("차분한수달");  // 시드 닉네임(learnerB)

        assertThatThrownBy(() -> userService.signup(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("중복 확인: true = 사용 가능, false = 사용 중 (US-03·04)")
    void availability() {
        assertThat(userService.isLoginIdAvailable("learnerB")).isFalse();
        assertThat(userService.isLoginIdAvailable("brandnewid")).isTrue();
        assertThat(userService.isNicknameAvailable("차분한수달")).isFalse();
        assertThat(userService.isNicknameAvailable("아무도안쓰는닉네임")).isTrue();
    }

    @Test
    @DisplayName("첫 로그인은 한 번만 true — 학습자 이후 로그인은 홈으로 (FR-003)")
    void recordFirstLoginOnlyOnce() {
        Long userId = userService.signup(validDto());

        assertThat(userService.recordFirstLogin(userId)).isTrue();
        assertThat(userService.recordFirstLogin(userId)).isFalse();
    }
}
