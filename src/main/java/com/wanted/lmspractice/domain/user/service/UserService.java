package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.auth.service.EmailVerificationService;
import com.wanted.lmspractice.domain.user.dto.AccountDTO;
import com.wanted.lmspractice.domain.user.dto.SignupDTO;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.policy.NicknamePolicy;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 학습자 가입(중복 검사·닉네임 생성·BCrypt·인증 메일 요청), 중복 확인, 첫 로그인 기록,
 *        #06 계정 설정(닉네임·비밀번호 변경)·메일 인증 비밀번호 재설정
 * [담당] 팀원 1 · #04, #06   [화면] C-03, C-05, U-S17   [기획] FR-001·002·003·004   [API] US-02·03·04·05
 * [연결]
 *  - 호출받음: SignupController(signup·중복 확인), AuthSuccessHandler(recordFirstLogin), AuthFailHandler(findStatusByLoginId),
 *             AccountController(getAccount·changeNickname·changePassword), AccountRecoveryController(resetPassword)
 *  - 호출함:   NicknamePolicy, UserRepository, PasswordEncoder(SecurityConfig의 BCrypt Bean),
 *             EmailVerificationService(sendVerification, consumeToken — RESET_PASSWORD 목적)
 * [기술] 🟢 @Service·@Transactional·JPA 변경 감지, 🟢 BCrypt
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final NicknamePolicy nicknamePolicy;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    /**
     * 학습자 가입 — 중복 검사 → 닉네임 확정 → 비밀번호 암호화 → 미인증 상태로 저장 → 인증 메일 발송 (US-02).
     * 가입 직후 자동 로그인하지 않는다(F-01). 메일 발송이 실패하면 예외로 이 트랜잭션 전체가 롤백된다.
     * 호출: SignupController.signup (POST /signup)
     *
     * @return 생성된 회원 id
     * @throws BusinessException DUPLICATE_LOGIN_ID, DUPLICATE_EMAIL, DUPLICATE_NICKNAME, INTERNAL_ERROR(메일 발송 실패)
     */
    @Transactional
    public Long signup(SignupDTO dto) {
        // 저장 전 정규화: 아이디·실명은 앞뒤 공백 제거, 이메일은 소문자로 통일(대소문자만 다른 중복 가입 방지)
        String loginId = dto.getLoginId().trim();
        String email = dto.getEmail().trim().toLowerCase();
        String nickname = nicknamePolicy.normalize(dto.getNickname());

        // 화면의 중복 확인 버튼과 가입 사이에 다른 사람이 가입할 수 있어서 여기서 다시 검사한다 (US-03)
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (nickname != null && !nicknamePolicy.isAvailable(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (nickname == null) {
            nickname = nicknamePolicy.generate();  // 미입력 시 "형용사+동물+번호" 자동 생성 (FR-002)
        }

        User user = User.createLearner(
                loginId,
                passwordEncoder.encode(dto.getPassword()),  // 평문은 저장하지 않는다 (NFR-02)
                dto.getName().trim(),
                email,
                nickname);
        // flush로 UNIQUE 위반(동시 가입)을 이 시점에 드러내고 id도 확정한다 — 메일 링크에 쓸 userId가 필요
        userRepository.saveAndFlush(user);

        emailVerificationService.sendVerification(user);  // 미인증(PENDING_EMAIL) 상태로 인증 메일 요청
        return user.getId();
    }

    /** 아이디 사용 가능 여부(true = 사용 가능). 호출: SignupController.checkId (US-03) */
    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        return loginId != null && !loginId.isBlank() && !userRepository.existsByLoginId(loginId.trim());
    }

    /** 이메일 사용 가능 여부(true = 사용 가능). 호출: SignupController.signup 검증 단계 */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return email != null && !email.isBlank() && !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /** 닉네임 사용 가능 여부(true = 사용 가능). 호출: SignupController.checkNickname (US-04) */
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        String normalized = nicknamePolicy.normalize(nickname);
        return normalized != null && nicknamePolicy.isAvailable(normalized);
    }

    /**
     * 첫 로그인 시각 기록 (FR-003). 처음이면 기록하고 true, 이미 있으면 false.
     * 호출: AuthSuccessHandler.onAuthenticationSuccess (학습자 로그인 성공 직후)
     */
    @Transactional
    public boolean recordFirstLogin(Long userId) {
        return userRepository.findById(userId)
                // LocalDateTime.now(): jdbc.time_zone=UTC 설정에서는 Hibernate가 JVM 기본 시간대 시각을 UTC로 바꿔 저장한다
                // (now(ZoneOffset.UTC)를 쓰면 KST 서버에서 9시간 어긋난다 — EmailVerificationService.utcNow 주석 참고)
                .map(user -> user.recordFirstLogin(LocalDateTime.now()))  // 변경 감지로 저장
                .orElse(false);
    }

    /**
     * 로그인 아이디의 계정 상태 조회 — 로그인이 막힌 이유(미인증/비활성)를 구분해 안내하려고 쓴다.
     * 호출: AuthFailHandler.onAuthenticationFailure (DisabledException일 때만)
     */
    @Transactional(readOnly = true)
    public Optional<UserStatus> findStatusByLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByLoginId(loginId.trim()).map(User::getStatus);
    }

    // ───────────────────────── #06 계정 설정·비밀번호 재설정 ─────────────────────────

    /**
     * 계정 설정 화면에 보여 줄 내 계정 정보 (U-S17).
     * 호출: AccountController.accountForm (GET /my/account)
     *
     * @throws BusinessException USER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public AccountDTO getAccount(Long userId) {
        return AccountDTO.from(findUser(userId));
    }

    /**
     * 닉네임 변경 (U-S17). 지금 닉네임과 같으면 아무것도 바꾸지 않는다.
     * 화면의 중복 확인(GET /signup/check-nickname)과 저장 사이에 다른 사람이 가져갈 수 있어 여기서 다시 확인한다.
     * 호출: AccountController.updateNickname (POST /my/account/nickname)
     *
     * @return 저장된 닉네임(앞뒤 공백 정리 후) — 컨트롤러가 세션의 헤더 표시 이름을 맞추는 데 쓴다
     * @throws BusinessException INVALID_INPUT(빈 값), DUPLICATE_NICKNAME, USER_NOT_FOUND
     */
    @Transactional
    public String changeNickname(Long userId, String nickname) {
        String normalized = nicknamePolicy.normalize(nickname);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);  // 계정 설정에서는 자동 생성하지 않는다
        }
        User user = findUser(userId);
        if (normalized.equals(user.getNickname())) {
            return normalized;
        }
        if (!nicknamePolicy.isAvailable(normalized)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(normalized);  // 변경 감지로 저장 (동시 변경은 users.nickname UNIQUE가 마지막으로 막는다)
        return normalized;
    }

    /**
     * 입력한 비밀번호가 현재 비밀번호와 같은지 (true = 일치). 화면에 칸별 오류로 보여 주려고 미리 확인할 때 쓴다.
     * 최종 판단은 changePassword가 다시 한다(SignupController의 중복 확인과 같은 방식).
     * 호출: AccountController.updatePassword
     */
    @Transactional(readOnly = true)
    public boolean isCurrentPassword(Long userId, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, findUser(userId).getPasswordHash());
    }

    /**
     * 닉네임을 이 회원이 쓸 수 있는지 (true = 사용 가능 또는 지금 내 닉네임). 호출: AccountController.updateNickname
     */
    @Transactional(readOnly = true)
    public boolean isNicknameAvailableFor(Long userId, String nickname) {
        String normalized = nicknamePolicy.normalize(nickname);
        return normalized != null
                && (normalized.equals(findUser(userId).getNickname()) || nicknamePolicy.isAvailable(normalized));
    }

    /**
     * 비밀번호 변경 (U-S17) — 현재 비밀번호 확인 → 새 비밀번호가 현재와 다른지 확인 → BCrypt로 저장.
     * 호출: AccountController.updatePassword (POST /my/account/password)
     *
     * @throws BusinessException PASSWORD_MISMATCH(현재 비밀번호 불일치), INVALID_NEW_PASSWORD(현재와 같음), USER_NOT_FOUND
     */
    @Transactional
    public void changePassword(Long userId, String current, String next) {
        User user = findUser(userId);
        if (current == null || !passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (current.equals(next)) {
            throw new BusinessException(ErrorCode.INVALID_NEW_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(next));
    }

    /**
     * 메일 인증을 거친 비밀번호 재설정 (C-05, FR-004) — RESET_PASSWORD 토큰을 1회 사용 처리하고 새 비밀번호로 바꾼다.
     * 토큰 사용 처리와 비밀번호 변경이 한 트랜잭션이라, 저장이 실패하면 토큰도 다시 쓸 수 있는 상태로 돌아간다.
     * 호출: AccountRecoveryController.resetPassword (POST /auth/recovery/password)
     *
     * @throws BusinessException INVALID_TOKEN(없음·재사용·가입 인증 토큰 등 목적 다름), TOKEN_EXPIRED
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Long userId = emailVerificationService.consumeToken(rawToken, TokenPurpose.RESET_PASSWORD);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
