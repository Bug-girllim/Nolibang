package com.wanted.lmspractice.domain.user.dto;

/**
 * [역할] 비밀번호 형식 규칙(길이·문자 종류)을 한곳에 모은 상수. Bean Validation 어노테이션 값으로 쓰므로 컴파일 시점 상수여야 한다.
 * [담당] 팀원 1 · #06 (#04 SignupDTO에 흩어져 있던 값을 옮김)   [기획] FR-001, CONVENTIONS §10 #4
 * [연결]
 *  - 호출받음: SignupDTO(#04), PasswordUpdateDTO(#06 계정 설정), PasswordResetDTO(#06 계정 복구), #05 강사 첫 로그인 변경에서도 재사용
 *  - 호출함:   없음
 * [기술] 🟢 상수 클래스 (규칙을 바꿀 때 이 파일 한 곳만 고친다)
 */
public final class PasswordRule {

    /** ❓ 임시값 8~16자 (CONVENTIONS §10 #4) */
    public static final int MIN = 8;
    public static final int MAX = 16;
    public static final String SIZE_MESSAGE = "비밀번호는 8~16자로 입력해 주세요.";

    /** 영문·숫자·특수문자를 모두 포함하고 공백이 없어야 한다 (C-03 화면 안내문 기준) */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$";
    public static final String REGEX_MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 포함하고 공백이 없어야 해요.";

    private PasswordRule() {
    }
}
