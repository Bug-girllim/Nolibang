package com.wanted.lmspractice.domain.user.entity;

/**
 * 인증 토큰 용도 (auth_tokens.purpose)
 * 담당: 팀원 1 · #04
 * 값은 resources/sql/schema.sql의 CHECK 제약 값과 글자까지 같아야 한다(@Enumerated(STRING) 저장). 바꾸려면 DB와 함께 바꾸고 전원에게 공유.
 */
public enum TokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD,
    RECOVER_LOGIN_ID
}
