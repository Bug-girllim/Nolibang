package com.wanted.lmspractice.domain.user.entity;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 회원 (학습자·강사·관리자 공통)
 * 담당: 팀원 1 · #02
 * 칼럼은 resources/sql/schema.sql의 `users` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "real_name", nullable = false, length = 100)
    private String realName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;  // DB 기본값과 같은 초기값

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_EMAIL;  // DB 기본값과 같은 초기값

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.FALSE;  // DB 기본값과 같은 초기값

    @Column(name = "first_login_at")
    private LocalDateTime firstLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 학습자 가입용 정적 팩토리 — 이메일 인증 전(PENDING_EMAIL) 상태로 만든다 (FR-001).
     * 호출: UserService.signup (#04)
     * role·status·mustChangePassword는 필드 초기값(USER · PENDING_EMAIL · false)을 그대로 쓴다.
     *
     * @param passwordHash 이미 BCrypt로 암호화된 값 (평문을 넘기지 않는다)
     * @param nickname     NicknamePolicy가 정리·생성한 값 (users.nickname은 USER 역할이면 NOT NULL — DB CHECK)
     */
    public static User createLearner(String loginId, String passwordHash, String realName,
                                     String email, String nickname) {
        User user = new User();
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        user.realName = realName;
        user.email = email;
        user.nickname = nickname;
        return user;
    }

    /**
     * 강사 계정 생성용 정적 팩토리 — 관리자가 임시 비밀번호로 즉시 활성 계정을 만든다 (FR-012, D-51: 메일 발송 없음).
     * 호출: InstructorAccountService.create (#05)
     * 첫 로그인에서 비밀번호를 바꾸도록 mustChangePassword = true. 닉네임은 받지 않는다(DB CHECK: USER 역할만 필수).
     *
     * @param tempPasswordHash 임시 비밀번호를 BCrypt로 암호화한 값 (평문을 넘기지 않는다)
     */
    public static User createInstructor(String loginId, String tempPasswordHash, String realName, String email) {
        User user = new User();
        user.loginId = loginId;
        user.passwordHash = tempPasswordHash;
        user.realName = realName;
        user.email = email;
        user.role = Role.INSTRUCTOR;
        user.status = UserStatus.ACTIVE;   // 이메일 인증 없이 바로 로그인 가능(D-51)
        user.mustChangePassword = Boolean.TRUE;
        return user;
    }

    /**
     * 임시 비밀번호 재발급 — 새 임시 비밀번호로 바꾸고 다시 첫 로그인 변경을 강제한다 (AI-03, 07 C-09 "이전 변경 무효").
     * 호출: InstructorAccountService.reissue (#05)
     */
    public void reissueTemporaryPassword(String tempPasswordHash) {
        this.passwordHash = tempPasswordHash;
        this.mustChangePassword = Boolean.TRUE;
    }

    /**
     * 첫 로그인 비밀번호 변경 완료 — 새 비밀번호로 바꾸고 변경 강제를 해제한다 (AU-14).
     * 호출: InstructorAccountService.changeInitialPassword (#05)
     * 이미 바꾼 계정이 이 경로로 다시 바꾸는 것은 막는다(일반 비밀번호 변경은 #06 계정 설정).
     */
    public void completeInitialPasswordChange(String newPasswordHash) {
        if (!requiresPasswordChange()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = Boolean.FALSE;
    }

    /** 임시 비밀번호 상태인지 (PasswordChangeInterceptor·AuthSuccessHandler는 세션 값 LoginUserDTO로 판단) */
    public boolean requiresPasswordChange() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    /**
     * 활성/비활성 변경 (AI-04). 비활성 계정은 로그인 불가(AuthDetails.isEnabled).
     * 이메일 인증 전(PENDING_EMAIL) 계정은 인증 절차를 건너뛰게 되므로 바꿀 수 없다.
     * 호출: InstructorAccountService.changeStatus (#05)
     */
    public void changeActive(boolean active) {
        if (status == UserStatus.PENDING_EMAIL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.status = active ? UserStatus.ACTIVE : UserStatus.INACTIVE;
    }

    /** 강사 계정인지 (관리자 강사 관리 화면에서 다른 역할 id로 접근하는 것을 막는다) */
    public boolean isInstructor() {
        return role == Role.INSTRUCTOR;
    }

    /**
     * 이메일 인증 완료 처리 — PENDING_EMAIL → ACTIVE (AU-05).
     * 이미 인증됐거나 비활성인 계정은 인증 대상이 아니므로 INVALID_TOKEN으로 막는다(상태 전이 검사는 Entity 안에서).
     * 호출: EmailVerificationService.verify
     */
    public void verifyEmail(LocalDateTime now) {
        if (status != UserStatus.PENDING_EMAIL) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = now;
    }

    /** 이메일 인증을 마쳤는지 (재발송 가능 여부 판단용) */
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * 첫 로그인 시각 기록 (FR-003: 첫 로그인은 취미 탐색, 이후는 홈).
     * 호출: UserService.recordFirstLogin ← AuthSuccessHandler
     *
     * @return 이번이 첫 로그인이면 true (기록함), 이미 기록돼 있으면 false
     */
    public boolean recordFirstLogin(LocalDateTime now) {
        if (firstLoginAt != null) {
            return false;
        }
        this.firstLoginAt = now;
        return true;
    }
}
