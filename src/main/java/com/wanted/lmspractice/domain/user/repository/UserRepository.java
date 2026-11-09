package com.wanted.lmspractice.domain.user.repository;

import com.wanted.lmspractice.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * [역할] users 표 조회·저장 (Spring Data JPA 쿼리 메서드)
 * [담당] 팀원 1 · #02
 * [연결]
 *  - 호출받음: AuthService(로그인 조회), UserService(#04 가입 시 중복 확인), InstructorAccountService(#05 강사 생성·목록)
 *  - 호출함:   없음
 * [기술] 🟢 JpaRepository 쿼리 메서드, 🟢 @Query(nativeQuery = true)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인 아이디로 회원 조회 (AuthService.loadUserByUsername에서 사용) */
    Optional<User> findByLoginId(String loginId);

    /** 아이디 중복 확인 (#04 가입, US-03) */
    boolean existsByLoginId(String loginId);

    /** 이메일 중복 확인 (#04 가입) */
    boolean existsByEmail(String email);

    /** 이메일로 회원 조회 (#04 인증 메일 재발송, #06 계정 복구) */
    Optional<User> findByEmail(String email);

    /** 닉네임 중복 확인 (#04 가입, #06 닉네임 변경) */
    boolean existsByNickname(String nickname);

    /**
     * 강사 목록·검색 (#05 AI-01). keyword가 null이면 전체, 있으면 아이디·실명·이메일 중 하나에 포함된 강사.
     * 대소문자 구분 없이 찾는다(표 collation utf8mb4_0900_ai_ci).
     */
    @Query(value = "SELECT * FROM users WHERE role = 'INSTRUCTOR' "
            + "AND (:keyword IS NULL OR login_id LIKE CONCAT('%', :keyword, '%') "
            + "OR real_name LIKE CONCAT('%', :keyword, '%') OR email LIKE CONCAT('%', :keyword, '%')) "
            + "ORDER BY id",
            nativeQuery = true)
    List<User> searchInstructors(@Param("keyword") String keyword);
}
