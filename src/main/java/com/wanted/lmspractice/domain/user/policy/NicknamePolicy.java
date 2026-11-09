package com.wanted.lmspractice.domain.user.policy;

import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 닉네임 정리·중복 확인, 미입력 시 "형용사+동물+1~999" 자동 생성 (중복이면 다시 만든다)
 * [담당] 팀원 1 · #04   [화면] C-03   [기획] FR-002   [API] US-02·US-04
 * [연결]
 *  - 호출받음: UserService.signup(정리·생성), UserService.isNicknameAvailable ← SignupController(check-nickname),
 *             #06 계정 설정의 닉네임 변경(U-S17)에서도 재사용
 *  - 호출함:   UserRepository.existsByNickname (같은 user 도메인 Repository라 직접 사용)
 * [기술] 🟢 @Component, 🟢 Repository 쿼리 메서드
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    /** 자동 생성 재시도 횟수. ❓ 임시값 10회(이슈 #04 제안값) */
    private static final int MAX_ATTEMPTS = 10;
    /** 뒤에 붙는 숫자 범위 1~999 (FR-002) */
    private static final int MAX_NUMBER = 999;

    private static final List<String> ADJECTIVES = List.of(
            "차분한", "수줍은", "느긋한", "용감한", "엉뚱한", "따뜻한", "씩씩한", "다정한", "호기심 많은", "부지런한");
    private static final List<String> ANIMALS = List.of(
            "수달", "다람쥐", "고양이", "여우", "펭귄", "토끼", "부엉이", "판다", "사슴", "고래");

    private final UserRepository userRepository;
    private final Random random = new Random();  // 예측 불가능성이 필요한 값이 아니라 일반 난수로 충분

    /** 입력 닉네임 앞뒤 공백을 지우고, 비어 있으면 null(= 자동 생성 대상)로 돌려준다 */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /** 사용 가능 여부(true = 아직 아무도 안 씀). US-04 의미와 같다 */
    public boolean isAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    /**
     * 중복 없는 닉네임을 자동 생성한다 (예: "차분한수달 128").
     * 호출: UserService.signup (닉네임 미입력 시)
     *
     * @throws BusinessException DUPLICATE_NICKNAME — MAX_ATTEMPTS번 모두 겹친 경우(사실상 드묾)
     */
    public String generate() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                    + ANIMALS.get(random.nextInt(ANIMALS.size()))
                    + " " + (random.nextInt(MAX_NUMBER) + 1);
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
    }
}
