package com.wanted.lmspractice.domain.auth.service;

import com.wanted.lmspractice.domain.user.entity.AuthToken;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import com.wanted.lmspractice.domain.user.repository.AuthTokenRepository;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 이메일 인증 토큰 발급(해시 저장)·검증·1회 사용 처리, 인증 메일 발송·재발송, 계정 복구 메일·아이디 찾기(#06)
 * [담당] 팀원 1 · #04, #06(sendRecovery·recoverLoginId·checkToken 추가)
 *       [화면] C-04, C-05   [기획] FR-001, FR-004   [API] AU-04·05·06, US-02   [NFR] NFR-02(토큰 원문 미저장)
 * [연결]
 *  - 호출받음: UserService.signup(sendVerification), EmailVerifyController(verify·resend),
 *             AccountRecoveryController(sendRecovery·recoverLoginId·checkToken), UserService.resetPassword(consumeToken)
 *  - 호출함:   AuthTokenRepository, UserRepository(같은 user 도메인·같은 담당자라 직접 사용 — AuthService와 동일),
 *             JavaMailSender 🟡
 *  - 트랜잭션: 모두 호출한 쪽 트랜잭션에 참여한다. 메일 발송이 실패하면 예외를 던져 가입·재발송 전체를 롤백한다
 *             (❓ 메일 실패 시 가입 롤백 여부는 API §16-1 미정 → 임시로 롤백)
 * [기술] 🟢 @Service·@Transactional·Repository
 *        🟡 미학습 기술: spring-boot-starter-mail(JavaMailSender) — 이메일 인증에 필수이고 대체 수단 없음(작업분배 §5, 팀 허용)
 *        🟡 미학습 기술: SecureRandom·MessageDigest(SHA-256) — JDK 표준. 토큰 원문은 예측 불가능해야 하고,
 *           DB(auth_tokens.token_hash BINARY(32))에는 해시만 저장해야 해서(NFR-02) 배운 기술로 대신할 수 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    /** 토큰 원문 길이(바이트). 32바이트 난수 → URL 안전 Base64 43자 */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    // SMTP 설정(spring.mail.host)이 없으면 JavaMailSender Bean이 없으므로 ObjectProvider로 받는다
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.base-url}")
    private String baseUrl;
    @Value("${app.mail.from}")
    private String mailFrom;
    /** ❓ 인증 토큰 만료 시간(시간). application.yaml 임시값 24 — API §16-1 확정 필요 */
    @Value("${app.token.verify-email-hours}")
    private long verifyEmailHours;

    // ───────────────────────── #04 이메일 인증 ─────────────────────────

    /**
     * 가입한 회원에게 인증 메일을 보낸다 — 새 토큰 발급(이전 토큰은 무효화) 후 링크 메일 발송.
     * 호출: UserService.signup (US-02), resend (AU-06)
     *
     * @throws BusinessException INTERNAL_ERROR — 메일 발송 실패(호출한 트랜잭션이 롤백된다)
     */
    @Transactional
    public void sendVerification(User user) {
        String rawToken = issueToken(user.getId(), TokenPurpose.VERIFY_EMAIL, Duration.ofHours(verifyEmailHours));
        String link = baseUrl + "/auth/verify?token=" + rawToken;
        sendMail(user, "[놀이방] 이메일 인증을 완료해 주세요", "이메일 인증을 완료해 주세요", link);
    }

    /**
     * 메일 링크의 토큰을 검증하고 이메일 인증을 완료한다 (AU-05).
     * 호출: EmailVerifyController.verify (GET /auth/verify?token=)
     *
     * @throws BusinessException INVALID_TOKEN(없음·이미 사용·목적 다름·계정이 인증 대상 아님), TOKEN_EXPIRED
     */
    @Transactional
    public void verify(String rawToken) {
        Long userId = consumeToken(rawToken, TokenPurpose.VERIFY_EMAIL);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        user.verifyEmail(utcNow());  // PENDING_EMAIL → ACTIVE (변경 감지로 저장)
    }

    /**
     * 인증 메일 재발송 (AU-06). 미인증 계정이면 이전 링크를 무효화하고 새로 보낸다.
     * 이메일이 없거나 이미 인증된 계정이어도 아무 일 없이 끝난다 — 호출한 화면이 같은 안내를 보여 계정 존재 여부를 숨긴다.
     * 호출: EmailVerifyController.resend (POST /auth/verify/resend)
     */
    @Transactional
    public void resend(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> user.getStatus() == UserStatus.PENDING_EMAIL)
                .ifPresent(this::sendVerification);
    }

    // ───────────────────────── #06 계정 복구 ─────────────────────────

    /**
     * 계정 복구 메일 발송 (C-05, FR-004) — 아이디 찾기(RECOVER_LOGIN_ID)·비밀번호 재설정(RESET_PASSWORD).
     * 가입된 활성 계정의 이메일이면 목적별 토큰을 발급해 링크를 보내고, 아니면(미가입·미인증·비활성) 아무것도 하지 않는다.
     * 결과를 돌려주지 않는 이유: 화면이 항상 같은 안내를 보여 이메일 가입 여부를 드러내지 않게 하려는 것(AU-06과 같은 방식).
     * 호출: AccountRecoveryController.send (POST /auth/recovery/send)
     *
     * @throws BusinessException INVALID_INPUT(복구용이 아닌 목적), INTERNAL_ERROR(메일 발송 실패)
     */
    @Transactional
    public void sendRecovery(String email, TokenPurpose purpose) {
        if (purpose != TokenPurpose.RECOVER_LOGIN_ID && purpose != TokenPurpose.RESET_PASSWORD) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);  // 복구 화면에서 가입 인증 토큰을 만들 수 없게 막는다
        }
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(User::isActive)  // 이메일 인증을 마친 활성 계정만 — 소유 이메일 인증 없이 복구 불가(#06 완료 조건)
                .ifPresent(user -> sendRecoveryMail(user, purpose));
    }

    /**
     * 아이디 찾기 결과 — RECOVER_LOGIN_ID 토큰을 1회 사용 처리하고 로그인 아이디를 돌려준다.
     * 메일을 받은 사람만 링크를 열 수 있으므로 이 시점에 이메일 소유가 확인된다.
     * 호출: AccountRecoveryController.showLoginId (GET /auth/recovery/login-id?token=)
     *
     * @throws BusinessException INVALID_TOKEN(없음·재사용·목적 다름), TOKEN_EXPIRED
     */
    @Transactional
    public String recoverLoginId(String rawToken) {
        Long userId = consumeToken(rawToken, TokenPurpose.RECOVER_LOGIN_ID);
        return userRepository.findById(userId)
                .map(User::getLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    /**
     * 토큰이 아직 쓸 수 있는지 확인만 한다(1회 사용 처리하지 않음).
     * 비밀번호 재설정 링크를 열었을 때 폼을 보여 줄지 판단하려고 쓴다 — 실제 사용 처리는 새 비밀번호 저장(POST) 때 한다.
     * 메일 앱의 링크 미리보기가 GET을 먼저 보내도 토큰이 소모되지 않게 하려는 순서다.
     * 호출: AccountRecoveryController.passwordForm (GET /auth/recovery/password?token=)
     *
     * @throws BusinessException INVALID_TOKEN, TOKEN_EXPIRED
     */
    @Transactional(readOnly = true)
    public void checkToken(String rawToken, TokenPurpose purpose) {
        findUsableToken(rawToken, purpose, utcNow());
    }

    /** 목적에 맞는 링크로 복구 메일을 보낸다. 토큰 유효 시간은 가입 인증과 같은 값(❓ 임시 24시간, CONVENTIONS §10 #1) */
    private void sendRecoveryMail(User user, TokenPurpose purpose) {
        String rawToken = issueToken(user.getId(), purpose, Duration.ofHours(verifyEmailHours));
        if (purpose == TokenPurpose.RECOVER_LOGIN_ID) {
            sendMail(user, "[놀이방] 아이디 찾기 안내", "아이디를 확인해 주세요",
                    baseUrl + "/auth/recovery/login-id?token=" + rawToken);
        } else {
            sendMail(user, "[놀이방] 비밀번호 재설정 안내", "새 비밀번호를 설정해 주세요",
                    baseUrl + "/auth/recovery/password?token=" + rawToken);
        }
    }

    // ───────────────────────── 공용 토큰 (#06 재사용) ─────────────────────────

    /**
     * 토큰을 새로 발급하고 원문을 돌려준다. 원문은 메일에만 담고 저장하지 않는다(DB엔 SHA-256 해시만).
     * 같은 회원·같은 목적의 아직 안 쓴 토큰은 먼저 무효화해서 가장 최근 링크 하나만 유효하게 한다.
     * 호출: sendVerification, #06 비밀번호 재설정·아이디 찾기
     */
    @Transactional
    public String issueToken(Long userId, TokenPurpose purpose, Duration ttl) {
        LocalDateTime now = utcNow();
        authTokenRepository.findByUserIdAndPurposeAndConsumedAtIsNull(userId, purpose)
                .forEach(old -> old.consume(now));

        byte[] random = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        authTokenRepository.save(AuthToken.create(userId, purpose, sha256(rawToken), now.plus(ttl)));
        return rawToken;
    }

    /**
     * 토큰 원문을 검증하고 1회 사용 처리한 뒤 회원 id를 돌려준다.
     * 호출: verify, #06 비밀번호 재설정·아이디 찾기
     *
     * @throws BusinessException INVALID_TOKEN(없음·이미 사용·목적 다름), TOKEN_EXPIRED(만료)
     */
    @Transactional
    public Long consumeToken(String rawToken, TokenPurpose purpose) {
        LocalDateTime now = utcNow();
        AuthToken token = findUsableToken(rawToken, purpose, now);
        token.consume(now);
        return token.getUserId();
    }

    /**
     * 쓸 수 있는 토큰을 찾는다 — 원문 해시로 조회 → 목적·사용 여부 → 만료 순으로 검사한다.
     * consumeToken·checkToken이 같은 검사를 쓰도록 한곳에 모았다.
     *
     * @throws BusinessException INVALID_TOKEN(없음·이미 사용·목적 다름), TOKEN_EXPIRED(만료)
     */
    private AuthToken findUsableToken(String rawToken, TokenPurpose purpose, LocalDateTime now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        AuthToken token = authTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        // 목적이 다른 토큰은 쓸 수 없다 — 가입 인증 토큰 ↔ 재설정·아이디 찾기 토큰 교차 사용 금지 (AU-05, DEV_REVIEW §2-12)
        if (token.getPurpose() != purpose || token.isConsumed()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        return token;
    }

    /** 화면에 보여 줄 가린 이메일 (예: ab***@gmail.com). 계정 정보를 다 드러내지 않기 위함 (AU-04 💡) */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        int visible = at <= 2 ? 1 : 2;
        return email.substring(0, visible) + "***" + email.substring(at);
    }

    // ───────────────────────── 내부 ─────────────────────────

    /**
     * 인증 메일 발송. SMTP가 설정돼 있지 않으면 메일을 보내지 않고 링크를 서버 로그에 남긴다 — 개발·시연 확인용.
     * ❓ SMTP 미준비 시 대체 확인 방법은 #01에서 정하기로 했으나 기록이 없어 이 방식을 임시로 택했다(PR에 명시).
     * 주의: 링크에는 토큰 원문이 들어 있으므로 이 로그는 SMTP 미설정 환경에서만 남는다. 운영에서는 반드시 SMTP를 설정한다.
     */
    private void sendMail(User user, String subject, String action, String link) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("[개발용] SMTP가 설정되지 않아 메일을 보내지 않았습니다. {} 링크: {}", subject, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText("안녕하세요, 놀이방입니다.\n\n"
                + "아래 링크를 눌러 " + action + ". 링크는 " + verifyEmailHours
                + "시간 동안 유효하고 한 번만 사용할 수 있어요.\n\n"
                + link + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 수신자 주소·토큰은 로그에 남기지 않는다
            log.error("메일 발송 실패 userId={}", user.getId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 토큰 원문 → SHA-256 해시(32바이트). auth_tokens.token_hash BINARY(32)와 길이가 같다 */
    private static byte[] sha256(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);  // JDK에 항상 있어 실제로는 발생하지 않음
        }
    }

    /**
     * 현재 시각. JVM 기본 시간대의 LocalDateTime.now()를 그대로 쓴다.
     * application.yaml의 hibernate.jdbc.time_zone=UTC 때문에 Hibernate가 LocalDateTime을 "JVM 기본 시간대 시각"으로 보고
     * DB에는 UTC로 바꿔 저장하고, 읽을 때 다시 되돌린다. 그래서 now(ZoneOffset.UTC)를 넣으면 한국(KST) 서버에서 9시간 어긋나
     * DB의 DEFAULT CURRENT_TIMESTAMP(UTC)와 비교하는 CHECK(expires_at > created_at)가 깨진다(테스트로 확인).
     */
    private static LocalDateTime utcNow() {
        return LocalDateTime.now();
    }
}
