package com.wanted.lmspractice.domain.user.entity;

/**
 * 계정 상태: 이메일 인증 전 / 활성 / 비활성 (users.status)
 * 담당: 팀원 1 · #02
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum UserStatus {
    PENDING_EMAIL,   // 가입 후 이메일 인증 전 — 로그인 불가
    ACTIVE,
    INACTIVE,       // 비활성 강사 등 — 로그인 불가
    WITHDRAWN       // 본인 탈퇴(학습자) — 로그인 불가, 개인정보는 익명화(User.withdraw)
}
