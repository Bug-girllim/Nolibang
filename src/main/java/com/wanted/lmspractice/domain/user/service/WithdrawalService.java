package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 학습자 회원 탈퇴 — 비밀번호 확인 → 대기 줄 취소 → 진행 중 수강 확인 → 계정 익명화(WITHDRAWN)
 * [담당] 팀원 1 · #06   [화면] U-S17 "회원 탈퇴" 탭   [기획] FR-002, 개인정보 처리방침 3항
 * [연결]
 *  - 호출받음: AccountController.withdraw (POST /my/account/withdraw)
 *  - 호출함:   UserRepository, PasswordEncoder, WaitlistService.cancelAllWaiting, EnrollmentService.hasActiveEnrollment
 *  - UserService와 나눈 이유: 수강 도메인 서비스를 부르는데, UserService는 여러 도메인이 의존하는 쪽이라 순환 의존을 피한다
 * [기술] 🟢 @Service·@Transactional·JPA 변경 감지, 🟢 BCrypt
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WaitlistService waitlistService;
    private final EnrollmentService enrollmentService;

    /**
     * 회원 탈퇴. 하나라도 실패하면 대기 취소까지 전부 되돌린다(한 트랜잭션).
     * 대기 줄을 먼저 비우는 이유: 자리가 나면 대기자가 자동으로 수강 확정되는데, 확정된 뒤에 탈퇴하면 정원만 차지하게 된다.
     * 대기 취소가 강의 잠금을 잡으므로, 그 뒤의 진행 중 수강 확인에는 동시에 확정되는 건이 끼어들지 못한다.
     * 호출: AccountController.withdraw
     *
     * @throws BusinessException USER_NOT_FOUND, ACCESS_DENIED(학습자 아님), PASSWORD_MISMATCH,
     *                           WITHDRAW_BLOCKED_ACTIVE_ENROLLMENT(진행 중 수강 있음)
     */
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != Role.USER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);  // 강사·관리자 계정은 관리자가 관리한다
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        waitlistService.cancelAllWaiting(userId);
        if (enrollmentService.hasActiveEnrollment(userId)) {
            throw new BusinessException(ErrorCode.WITHDRAW_BLOCKED_ACTIVE_ENROLLMENT);
        }

        // LocalDateTime.now(): UserService.recordFirstLogin 주석과 같은 이유(jdbc.time_zone=UTC 설정)
        user.withdraw(LocalDateTime.now(), passwordEncoder.encode(UUID.randomUUID().toString()));
    }

    /** 진행 중 수강이 있어 지금은 탈퇴할 수 없는지 — 탈퇴 탭에서 미리 안내하려고 쓴다. 호출: AccountController.accountForm */
    @Transactional(readOnly = true)
    public boolean isBlockedByActiveEnrollment(Long userId) {
        return enrollmentService.hasActiveEnrollment(userId);
    }
}
