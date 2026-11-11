package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.auth.service.LoginSessionService;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.domain.game.service.GameService;
import com.wanted.lmspractice.domain.inquiry.service.InquiryService;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.service.ReviewService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.repository.AuthTokenRepository;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.service.CommentService;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 학습자 회원 탈퇴 — 본인 확인 후 계정 익명화와 연관 데이터 삭제·익명 보존을 한 트랜잭션으로 처리한다
 * [담당] 팀원 1 · #06 (팀원 2 · 회원 탈퇴 개인정보 정리: 연관 데이터 정리 추가)   [화면] U-S17 "회원 탈퇴" 탭
 * [기획] FR-040, 02-workflow U-15, D-25(댓글 본문 비식별), D-07(이메일 확인값 30일), 개인정보 처리방침 3항
 * [연결]
 *  - 호출받음: AccountController.withdraw (POST /my/account/withdraw)
 *  - 호출함:   (본인 확인) UserRepository, PasswordEncoder / (수강) WaitlistService, EnrollmentService
 *             (정리) NotificationService, InquiryService, CommentService, WorkService, ReviewService, ProgressService, GameService,
 *             AuthTokenRepository / (계정) WithdrawnEmailHoldService, LoginSessionService
 *  - UserService와 나눈 이유: 여러 도메인 서비스를 부르는데, UserService는 여러 도메인이 의존하는 쪽이라 순환 의존을 피한다
 * [원칙] 다른 도메인의 표는 그 도메인 서비스의 ...ForWithdrawal 메서드로만 지운다(Repository 직접 주입 금지).
 *        모든 단계가 이 메서드의 트랜잭션에 합류하므로 하나라도 실패하면 전부 롤백된다(U-15 "일부만 삭제된 채 완료 안내 금지").
 *        실제 이미지 파일은 커밋이 끝난 뒤에만 지운다(WorkService) — 롤백되면 파일도 그대로 남는다.
 * [기술] 🟢 @Service·@Transactional·JPA 변경 감지, 🟢 BCrypt
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final WaitlistService waitlistService;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;
    private final InquiryService inquiryService;
    private final CommentService commentService;
    private final WorkService workService;
    private final ReviewService reviewService;
    private final ProgressService progressService;
    private final GameService gameService;
    private final WithdrawnEmailHoldService withdrawnEmailHoldService;
    private final LoginSessionService loginSessionService;

    /**
     * 회원 탈퇴. 하나라도 실패하면 대기 취소까지 전부 되돌린다(한 트랜잭션).
     * 대기 줄을 먼저 비우는 이유: 자리가 나면 대기자가 자동으로 수강 확정되는데, 확정된 뒤에 탈퇴하면 정원만 차지하게 된다.
     * 대기 취소가 강의 잠금을 잡으므로, 그 뒤의 진행 중 수강 확인에는 동시에 확정되는 건이 끼어들지 못한다.
     * 정리 순서는 FK 방향을 따른다 — 가리키는 쪽(알림·문의·작품 연결)을 먼저, 가리켜지는 쪽(평가·작품·수강)을 나중에 지운다.
     * 호출: AccountController.withdraw
     *
     * @throws BusinessException USER_NOT_FOUND, ACCESS_DENIED(학습자 아님), PASSWORD_MISMATCH,
     *                           WITHDRAW_BLOCKED_ACTIVE_ENROLLMENT(진행 중 수강 있음)
     */
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = findUser(userId);
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

        // 계정을 익명화하기 전에 원래 값을 잡아 둔다 — 댓글 본문에서 가릴 값과 재가입 확인값에 쓴다(DB에는 남기지 않는다)
        String email = user.getEmail();
        List<String> identifiers = Stream.of(user.getRealName(), user.getLoginId(), user.getNickname(), email)
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime now = LocalDateTime.now();  // UserService.recordFirstLogin 주석과 같은 이유(jdbc.time_zone=UTC 설정)

        deleteRelatedData(userId, identifiers);

        // 위 단계의 bulk 쿼리가 영속성 컨텍스트를 비우므로(clearAutomatically) 계정은 다시 읽어 바꾼다
        User fresh = findUser(userId);
        fresh.withdraw(now, passwordEncoder.encode(UUID.randomUUID().toString()));  // 아이디·닉네임·실명·이메일·비밀번호 익명화
        authTokenRepository.deleteAllByUserId(userId);          // 이메일 인증·비밀번호 재설정 토큰(인증 비밀정보)
        withdrawnEmailHoldService.hold(email, now);             // 이메일 원문 대신 확인값만 30×24시간 보관
        loginSessionService.expireAll(userId);                  // 다른 기기에 남은 세션도 끊는다(현재 세션은 AccountController가 로그아웃)
    }

    /** 진행 중 수강이 있어 지금은 탈퇴할 수 없는지 — 탈퇴 탭에서 미리 안내하려고 쓴다. 호출: AccountController.accountForm */
    @Transactional(readOnly = true)
    public boolean isBlockedByActiveEnrollment(Long userId) {
        return enrollmentService.hasActiveEnrollment(userId);
    }

    /**
     * 탈퇴자의 연관 데이터 정리(U-15 4~11번). 순서가 FK 를 지키는 데 필요하므로 바꾸지 않는다.
     *  1 알림(지워질 대상을 가리킴) → 2 문의 스레드 → 3 남의 작품 댓글 익명화(본문 가리기 후 연결 해제) → 4 좋아요 익명 집계
     *  → 5 작품의 평가 연결 해제 → 6 평가 회차(공식 피드백) → 7 작품·이미지·버전·작품의 댓글/좋아요/조치 이력
     *  → 8 진도 → 9 대기 기록 → 10 수강 건(수료·배지) → 11 퀘스트·아바타·업적 배지
     */
    private void deleteRelatedData(Long userId, List<String> identifiers) {
        notificationService.deleteAllForWithdrawal(userId);
        inquiryService.deleteAllOfLearnerForWithdrawal(userId);
        commentService.anonymizeCommentsForWithdrawal(userId, identifiers);
        commentService.anonymizeLikesForWithdrawal(userId);
        workService.detachAllOfAuthorForWithdrawal(userId);
        reviewService.deleteAllOfLearnerForWithdrawal(userId);
        workService.deleteAllOfAuthorForWithdrawal(userId);
        progressService.deleteAllOfLearnerForWithdrawal(userId);
        waitlistService.deleteAllOfUserForWithdrawal(userId);
        enrollmentService.deleteAllOfLearnerForWithdrawal(userId);
        gameService.deleteAllOfUserForWithdrawal(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
