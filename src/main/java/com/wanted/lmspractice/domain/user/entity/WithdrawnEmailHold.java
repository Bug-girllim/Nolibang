package com.wanted.lmspractice.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 탈퇴한 이메일의 재가입 제한 확인값 — 이메일 원문 대신 되돌릴 수 없는 확인값(HMAC-SHA256 16진수)만 보관한다
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리   [기획] U-15 3번, D-07 "탈퇴 이메일 재가입 확인값 30일", 개인정보 처리방침 3항
 * [연결]
 *  - 호출받음: WithdrawnEmailHoldService(저장·조회·만료 정리)
 *  - 표:       withdrawn_email_holds (schema.sql "회원 탈퇴 개인정보 정리" 블록)
 * [설계] users 행을 가리키는 칼럼을 두지 않는다 — 확인값에서 원 계정으로 거슬러 갈 수 없게 하기 위해서다.
 *        expires_at = withdrawn_at + 30일(DB CHECK ck_email_holds_period). UTC 저장이라 30일 = 30×24시간이다.
 */
@Entity
@Table(name = "withdrawn_email_holds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
public class WithdrawnEmailHold {

    @Id
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 탈퇴 시각부터 User.REJOIN_RESTRICTION_DAYS 동안 같은 이메일 가입을 막는 확인값
     * @param emailHash 정규화한 이메일의 확인값 (WithdrawnEmailHoldService.hash)
     */
    public static WithdrawnEmailHold create(String emailHash, LocalDateTime withdrawnAt) {
        WithdrawnEmailHold hold = new WithdrawnEmailHold();
        hold.emailHash = emailHash;
        hold.withdrawnAt = withdrawnAt;
        hold.expiresAt = withdrawnAt.plusDays(User.REJOIN_RESTRICTION_DAYS);
        return hold;
    }

    /** 같은 이메일로 다시 탈퇴한 경우(만료 정리 전에 재가입 → 재탈퇴) 기간을 새 탈퇴 시각부터 다시 센다 */
    public void restart(LocalDateTime withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
        this.expiresAt = withdrawnAt.plusDays(User.REJOIN_RESTRICTION_DAYS);
    }
}
