package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.WithdrawnEmailHold;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.user.repository.WithdrawnEmailHoldRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 탈퇴 이메일 재가입 제한 — 이메일 원문 대신 확인값만 30×24시간 보관하고, 가입 때 그 값으로 제한 여부를 판단한다
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리   [기획] U-15 3번, 02-workflow 56행(30일 지나지 않은 이메일 가입 불가), D-07
 * [연결]
 *  - 호출받음: WithdrawalService.withdraw(확인값 저장), UserService.signup·isEmailAvailableForSignup(제한 확인),
 *             WithdrawnEmailHoldScheduler(만료 정리·이전 방식 데이터 이전)
 *  - 호출함:   WithdrawnEmailHoldRepository, UserRepository(이전 방식으로 원문이 남은 탈퇴 계정 찾기)
 * [기술] 🟡 미학습 기술: javax.crypto.Mac(HMAC-SHA256) — JDK 표준. 이메일은 경우의 수가 적어 단순 SHA-256 이면
 *        흔한 이메일 목록을 해시해 맞춰 볼 수 있다. 서버만 아는 키를 섞은 HMAC 이라 DB 가 유출돼도 원문을 되찾기 어렵다.
 *        키는 app.withdrawal.email-hash-key(환경변수 WITHDRAWAL_EMAIL_HASH_KEY). 키를 바꾸면 기존 확인값이 맞지 않아
 *        제한 기간 중인 이메일도 가입이 풀리므로, 운영 중에는 바꾸지 않는다(배포_운영_가이드 환경변수 표).
 */
@Service
public class WithdrawnEmailHoldService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WithdrawnEmailHoldRepository holdRepository;
    private final UserRepository userRepository;
    private final SecretKeySpec hashKey;

    public WithdrawnEmailHoldService(WithdrawnEmailHoldRepository holdRepository, UserRepository userRepository,
                                     @Value("${app.withdrawal.email-hash-key}") String hashKey) {
        this.holdRepository = holdRepository;
        this.userRepository = userRepository;
        this.hashKey = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * 탈퇴한 이메일의 확인값을 저장한다 — 이메일 원문은 받기만 하고 저장하지 않는다.
     * 같은 확인값이 남아 있으면(만료 정리 전 재가입 → 재탈퇴) 기간을 새로 센다.
     * 호출: WithdrawalService.withdraw (탈퇴 트랜잭션 안)
     */
    @Transactional
    public void hold(String email, LocalDateTime withdrawnAt) {
        String emailHash = hash(email);
        holdRepository.findById(emailHash).ifPresentOrElse(
                hold -> hold.restart(withdrawnAt),
                () -> holdRepository.save(WithdrawnEmailHold.create(emailHash, withdrawnAt)));
    }

    /** 이 이메일이 탈퇴 후 재가입 제한 기간 안인지. 호출: UserService.signup, isEmailAvailableForSignup */
    @Transactional(readOnly = true)
    public boolean isHeld(String email, LocalDateTime now) {
        return holdRepository.existsByEmailHashAndExpiresAtAfter(hash(email), now);
    }

    /**
     * 기간이 끝난 확인값을 지운다(U-15 "확인값만 30일 보관"). 판정은 expires_at 으로 하므로 정리 전에도 재가입은 열린다.
     * 호출: WithdrawnEmailHoldScheduler
     * @return 지운 행 수
     */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        return holdRepository.deleteExpired(now);
    }

    /**
     * 이전 방식(탈퇴 후에도 이메일 원문을 users.email 에 30일 보관)으로 탈퇴한 계정을 새 방식으로 옮긴다.
     * 아직 기간 안이면 확인값을 만들고, 어느 경우든 원문 이메일을 지운다.
     * 호출: WithdrawnEmailHoldScheduler (서버 시작 직후 한 번 + 주기 실행)
     * @return 옮긴 계정 수
     */
    @Transactional
    public int migrateLegacyWithdrawnEmails(LocalDateTime now) {
        List<User> legacy = userRepository.findWithdrawnWithRawEmail(User.ANONYMIZED_EMAIL_DOMAIN);
        for (User user : legacy) {
            if (user.getWithdrawnAt().plusDays(User.REJOIN_RESTRICTION_DAYS).isAfter(now)) {
                hold(user.getEmail(), user.getWithdrawnAt());
            }
            user.removeRawEmail();
        }
        return legacy.size();
    }

    /** 확인값 = HMAC-SHA256(서버 키, 정규화 이메일) 16진수 64자. 가입 때와 같은 정규화(trim + 소문자)를 쓴다. 호출: 이 클래스, 테스트 */
    public String hash(String email) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);   // Mac 은 스레드 안전하지 않아 매번 만든다
            mac.init(hashKey);
            byte[] digest = mac.doFinal(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 을 쓸 수 없습니다", e);   // JDK 표준 알고리즘이라 일어나지 않는다
        }
    }
}
