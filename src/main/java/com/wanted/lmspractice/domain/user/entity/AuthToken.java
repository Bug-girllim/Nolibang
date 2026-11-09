package com.wanted.lmspractice.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 이메일 인증·비밀번호 재설정 토큰 (원문 미저장, 해시만)
 * 담당: 팀원 1 · #04
 * 칼럼은 resources/sql/schema.sql의 `auth_tokens` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "auth_tokens")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private TokenPurpose purpose;

    @JdbcTypeCode(SqlTypes.BINARY)  // DB가 BINARY 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "token_hash", nullable = false, length = 32)
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    /**
     * 토큰 발급 기록 생성. 원문은 저장하지 않고 SHA-256 해시(32바이트)만 받는다 (NFR-02).
     * 호출: EmailVerificationService.issueToken (#04, #06에서 목적만 바꿔 재사용)
     */
    public static AuthToken create(Long userId, TokenPurpose purpose, byte[] tokenHash, LocalDateTime expiresAt) {
        AuthToken token = new AuthToken();
        token.userId = userId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    /** 이미 사용했거나 무효화된 토큰인지 */
    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** 만료 시각이 지났는지 */
    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 1회 사용 처리(또는 재발송 시 기존 토큰 무효화). 변경 감지로 저장된다. */
    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }
}
