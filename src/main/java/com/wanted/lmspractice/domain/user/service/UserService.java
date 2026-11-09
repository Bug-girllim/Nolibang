package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.auth.service.EmailVerificationService;
import com.wanted.lmspractice.domain.user.dto.SignupDTO;
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
 * [역할] 학습자 가입(중복 검사·닉네임 생성·BCrypt·인증 메일 요청), 중복 확인, 첫 로그인 기록
 * [담당] 팀원 1 · #04 (#06 계정 설정에서 같은 서비스에 메서드 추가)
 *       [화면] C-03   [기획] FR-001·002·003   [API] US-02·03·04
 * [연결]
 *  - 호출받음: SignupController(signup·중복 확인), AuthSuccessHandler(recordFirstLogin), AuthFailHandler(findStatusByLoginId)
 *  - 호출함:   NicknamePolicy, UserRepository, PasswordEncoder(SecurityConfig의 BCrypt Bean), EmailVerificationService.sendVerification
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
}
