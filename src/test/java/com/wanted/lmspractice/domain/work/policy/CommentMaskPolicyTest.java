package com.wanted.lmspractice.domain.work.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] 탈퇴 댓글 본문 식별정보 가리기(D-25) 규칙 테스트 — DB 없이 문자열만 확인
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리
 */
class CommentMaskPolicyTest {

    private final CommentMaskPolicy policy = new CommentMaskPolicy();
    private final List<String> me = List.of("김학습", "learner", "그림왕", "learner@example.com");

    @Test
    @DisplayName("이메일·휴대폰·지역번호·링크·외부 계정·본인 실명/아이디/닉네임을 가리고 나머지 글은 남긴다")
    void masksIdentifiers() {
        String body = "김학습입니다. 메일 hello.world@gmail.com, 폰 010-1234-5678, 집 02 123 4567, "
                + "인스타 @draw_kim, 블로그 https://blog.example.com/me 그리고 아이디 LEARNER 닉 그림왕. 색감이 정말 좋아요";

        String masked = policy.mask(body, me);

        assertThat(masked).doesNotContain("김학습", "hello.world@gmail.com", "010-1234-5678", "02 123 4567",
                "@draw_kim", "blog.example.com", "LEARNER", "그림왕");
        assertThat(masked).contains(CommentMaskPolicy.MASK).contains("색감이 정말 좋아요");
    }

    @Test
    @DisplayName("가릴 것이 없으면 본문을 그대로 둔다 (날짜·짧은 숫자는 전화번호로 보지 않는다)")
    void keepsPlainText() {
        String body = "2026.11.11 에 본 작품 중 최고예요! 3번째 그림이 좋아요";
        assertThat(policy.mask(body, me)).isEqualTo(body);
    }

    @Test
    @DisplayName("식별정보만 적은 댓글은 가린 뒤에도 알아볼 수 있으니 본문 전체를 대체한다")
    void replacesWholeBodyWhenMostlyIdentifiers() {
        assertThat(policy.mask("010-1234-5678 김학습", me)).isEqualTo(CommentMaskPolicy.FULLY_HIDDEN_BODY);
    }

    @Test
    @DisplayName("한 글자 같은 짧은 식별값은 흔한 낱말과 겹칠 수 있어 가리지 않는다")
    void ignoresTooShortIdentifier() {
        assertThat(policy.mask("좋은 그림이에요", List.of("림"))).isEqualTo("좋은 그림이에요");
    }
}
