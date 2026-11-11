package com.wanted.lmspractice.domain.user.repository;

import com.wanted.lmspractice.domain.user.entity.WithdrawnEmailHold;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] withdrawn_email_holds 표 접근 (재가입 제한 확인값)
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리
 * [연결] 호출받음: WithdrawnEmailHoldService
 * [기술] 🟢 JpaRepository 쿼리 메서드, 🟢 @Query(nativeQuery = true) + @Modifying
 */
@Repository
public interface WithdrawnEmailHoldRepository extends JpaRepository<WithdrawnEmailHold, String> {

    /** 아직 제한 기간 안인 확인값이 있는지 — 만료 시각이 지난 행은 정리 전이라도 제한하지 않는다 */
    boolean existsByEmailHashAndExpiresAtAfter(String emailHash, LocalDateTime now);

    /** 기간이 끝난 확인값 삭제. 호출: WithdrawnEmailHoldService.purgeExpired */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM withdrawn_email_holds WHERE expires_at <= :now", nativeQuery = true)
    int deleteExpired(@Param("now") LocalDateTime now);
}
