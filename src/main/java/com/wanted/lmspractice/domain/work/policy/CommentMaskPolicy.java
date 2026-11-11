package com.wanted.lmspractice.domain.work.policy;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * [역할] 탈퇴한 사용자의 댓글 본문에서 식별정보(이메일·전화번호·외부 링크·외부 계정·본인 실명/아이디/닉네임)를 가린다
 * [담당] 팀원 2 · 회원 탈퇴 개인정보 정리   [기획] D-25 확정, U-15 6번, 02-workflow 189행
 * [연결]
 *  - 호출받음: CommentService.anonymizeCommentsForWithdrawal (원 계정 연결을 끊기 "전에" 본문을 가린다)
 *  - 호출함:   없음 (본문 문자열만 다룬다)
 * [규칙]
 *  - 찾은 식별정보는 MASK 로 바꾼다. 다른 사람의 이름·답글은 건드리지 않는다(U-15 7번).
 *  - 가리고 남은 글자가 너무 적으면(재식별 우려 — 거의 식별정보만 적은 댓글) 본문 전체를 FULLY_HIDDEN_BODY 로 대체한다(D-25).
 *  - D-25: 자동 탐지만으로 모든 식별정보 제거를 보장하지 않는다 — 남은 것은 관리자 점검으로 보완한다.
 * [기술] 🟢 Policy 패턴, 🟡 미학습 기술: java.util.regex(정규식) — 이메일·전화번호처럼 모양으로만 찾을 수 있는 값을
 *        문자열 메서드(contains·replace)로는 찾을 수 없다. JDK 표준.
 */
@Component
public class CommentMaskPolicy {

    /** 가린 자리에 넣는 표시 */
    public static final String MASK = "***";

    /** 본문 전체를 대체할 때 쓰는 문구 (댓글 자리·답글은 유지된다) */
    public static final String FULLY_HIDDEN_BODY = "탈퇴한 사용자의 댓글은 개인정보 보호를 위해 비공개 처리되었습니다.";

    /** ❓ 임시값: 가리고 남은 글자(공백·기호·MASK 제외)가 이보다 적으면 전체 대체 — 팀 확인 필요 */
    static final int MIN_REMAINING_CHARS = 5;

    /** 실명·아이디·닉네임 중 이보다 짧은 값은 흔한 낱말과 겹칠 수 있어 가리지 않는다(예: 한 글자 이름) */
    static final int MIN_IDENTIFIER_LENGTH = 2;

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // 휴대폰·지역번호·+82 형식. 숫자 사이 -, ., 공백 허용. 앞뒤가 숫자면(더 긴 숫자의 일부) 제외
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?82[-.\\s]?)?0?\\d{1,2}[-.\\s)]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");
    // 외부 계정(인스타·트위터 등 @아이디). 이메일을 먼저 가린 뒤에 적용한다
    private static final Pattern HANDLE = Pattern.compile("(?<![\\w*])@[A-Za-z0-9_.]{2,}");
    private static final Pattern VISIBLE_CHAR = Pattern.compile("[\\p{L}\\p{N}]");

    /**
     * 댓글 본문의 식별정보를 가린다.
     * @param body        원래 본문
     * @param identifiers 탈퇴자 본인의 실명·아이디·닉네임 등 (null·짧은 값은 무시)
     * @return 가린 본문 (가릴 것이 없으면 원문 그대로)
     */
    public String mask(String body, Collection<String> identifiers) {
        String masked = EMAIL.matcher(body).replaceAll(MASK);
        masked = URL.matcher(masked).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(MASK);
        masked = HANDLE.matcher(masked).replaceAll(MASK);
        for (String identifier : identifiers) {
            masked = maskLiteral(masked, identifier);
        }
        if (!masked.equals(body) && countVisible(masked.replace(MASK, "")) < MIN_REMAINING_CHARS) {
            return FULLY_HIDDEN_BODY;
        }
        return masked;
    }

    // 대소문자 무시 글자 그대로 찾아 바꾼다 (정규식 특수문자는 quote 로 글자 취급)
    private String maskLiteral(String text, String identifier) {
        if (identifier == null || identifier.strip().length() < MIN_IDENTIFIER_LENGTH) {
            return text;
        }
        Pattern literal = Pattern.compile(Pattern.quote(identifier.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return literal.matcher(text).replaceAll(Matcher.quoteReplacement(MASK));
    }

    private int countVisible(String text) {
        Matcher matcher = VISIBLE_CHAR.matcher(text.toLowerCase(Locale.ROOT));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
