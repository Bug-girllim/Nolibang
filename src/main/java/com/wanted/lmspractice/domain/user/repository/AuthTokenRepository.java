package com.wanted.lmspractice.domain.user.repository;

import com.wanted.lmspractice.domain.user.entity.AuthToken;
import com.wanted.lmspractice.domain.user.entity.TokenPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할] auth_tokens 표 조회·저장 (이메일 인증·비밀번호 재설정·아이디 찾기 토큰)
 * [담당] 팀원 1 · #04 (#06 재사용)
 * [연결]
 *  - 호출받음: EmailVerificationService(발급·검증·무효화)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드
 */
@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /** 메일 링크의 토큰 원문을 SHA-256으로 해시한 값으로 조회한다 (DB에는 해시만 있다 — NFR-02) */
    Optional<AuthToken> findByTokenHash(byte[] tokenHash);

    /** 아직 쓰지 않은 같은 목적의 토큰 전부 — 재발송 시 이전 링크를 무효화하는 데 쓴다 (AU-06) */
    List<AuthToken> findByUserIdAndPurposeAndConsumedAtIsNull(Long userId, TokenPurpose purpose);
}
