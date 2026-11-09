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

    /**
     * 닉네임 변경 (U-S17). 정리·중복 확인은 호출한 쪽(UserService + NicknamePolicy)이 끝낸 값만 넘긴다.
     * 호출: UserService.changeNickname (#06)
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 비밀번호 변경 — 계정 설정(현재 비밀번호 확인 후)·이메일 인증을 거친 재설정(C-05)에서 쓴다.
     * 본인이 직접 새 비밀번호를 정한 것이므로 "임시 비밀번호 변경 필요" 표시도 함께 내린다(FR-012와 충돌하지 않음).
     * 호출: UserService.changePassword·resetPassword (#06)
     *
     * @param newPasswordHash 이미 BCrypt로 암호화된 값 (평문을 넘기지 않는다)
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = Boolean.FALSE;
    }

    /** 로그인할 수 있는 활성 계정인지 (#06 계정 복구 대상 판단 — 미인증·비활성 계정에는 복구 메일을 보내지 않는다) */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
