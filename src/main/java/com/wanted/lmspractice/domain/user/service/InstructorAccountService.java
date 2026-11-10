package com.wanted.lmspractice.domain.user.service;

import com.wanted.lmspractice.domain.auth.dto.PasswordChangeDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.dto.InstructorCreateDTO;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrant;
import com.wanted.lmspractice.domain.user.entity.InstructorHobbyGrantId;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.repository.InstructorHobbyGrantRepository;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * [역할] 관리자 강사 계정 생성·임시 비밀번호 재발급·활성/비활성·취미 권한 부여/회수, 강사 첫 로그인 비밀번호 변경
 * [담당] 팀원 1 · #05   [화면] A-S04, C-09   [기획] FR-012, D-51   [API] AI-01~06, AU-14
 * [연결]
 *  - 호출받음: AdminInstructorController(AI-01~06), InstructorSetupController(AU-13·14),
 *             SectionService(#10, hasHobbyGrant — 분반 담당 지정), 팀원 4 #18(hasHobbyGrant — 공개 댓글 자격)
 *  - 호출함:   UserRepository, InstructorHobbyGrantRepository, PasswordEncoder(BCrypt),
 *             HobbyService.getCategories(Role.ADMIN)(#08 — 취미 이름·존재 확인. 다른 도메인이라 Repository 대신 Service 사용)
 *  - 계약:     C3 hasHobbyGrant(Long, Long) — 시그니처 변경 금지
 *  - 주의:     SectionService(#10)가 이 서비스를 주입하므로, 여기서 SectionService를 주입하면 순환 참조가 된다.
 *             담당 분반 수는 AdminInstructorController가 채운다.
 * [기술] 🟢 @Transactional·JPA 변경 감지, 🟢 BCrypt, 🟢 BusinessException(ErrorCode)
 * 보안: 임시 비밀번호 평문은 반환값으로만 넘기고(화면 flash 1회 표시), 로그·DB에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InstructorAccountService {

    /** ❓ 임시값(CONVENTIONS §10 #5): 임시 비밀번호 12자, 영문 대·소문자·숫자·특수문자 각 1자 이상 */
    static final int TEMP_PASSWORD_LENGTH = 12;
    // 손으로 옮겨 적기 쉽게 헷갈리는 글자(I·l·1·O·0)를 뺀다
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIALS;

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final InstructorHobbyGrantRepository grantRepository;
    private final PasswordEncoder passwordEncoder;
    private final HobbyService hobbyService;

    /**
     * 강사 목록·검색 + 각 강사의 유효한 취미 권한 (AI-01).
     * 호출: AdminInstructorController.list (GET /admin/instructors)
     *
     * @param keyword 아이디·실명·이메일 검색어. 비어 있으면 전체
     */
    @Transactional(readOnly = true)
    public List<InstructorDTO> getInstructors(String keyword) {
        List<User> instructors = userRepository.searchInstructors(StringUtils.hasText(keyword) ? keyword.trim() : null);
        if (instructors.isEmpty()) {
            return List.of();
        }
        Map<Long, String> hobbyNames = allHobbies().stream()
                .collect(Collectors.toMap(HobbyDTO::getId, HobbyDTO::getName));
        // 강사마다 따로 조회하지 않도록 권한을 한 번에 읽어 강사별로 묶는다
        Map<Long, List<Long>> grantsByInstructor = grantRepository
                .findByInstructorIdInAndIsActiveTrue(instructors.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(InstructorHobbyGrant::getInstructorId,
                        Collectors.mapping(InstructorHobbyGrant::getHobbyId, Collectors.toList())));

        return instructors.stream()
                .map(user -> {
                    List<Long> hobbyIds = grantsByInstructor.getOrDefault(user.getId(), List.of()).stream()
                            .sorted().toList();
                    List<String> names = hobbyIds.stream().map(id -> hobbyNames.getOrDefault(id, "#" + id)).toList();
                    return InstructorDTO.from(user, hobbyIds, names);
                })
                .toList();
    }

    /**
     * 강사 한 명의 계정 정보 (C-09 화면의 아이디·이메일 표시).
     * 호출: InstructorSetupController.passwordForm (GET /auth/instructor/password)
     * @throws BusinessException USER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public InstructorDTO getInstructor(Long instructorId) {
        return InstructorDTO.from(findInstructor(instructorId), List.of(), List.of());
    }

    /**
     * 권한을 줄 수 있는 세부 취미 목록 — 운영·준비 중 취미 (A-S04 시안 "운영·준비 중 취미", 비공개 취미 제외).
     * 호출: AdminInstructorController (모델 hobbies — 생성 폼 체크박스, 취미 활동 권한 표의 열)
     */
    @Transactional(readOnly = true)
    public List<HobbyDTO> getGrantableHobbies() {
        return allHobbies().stream()
                .filter(hobby -> hobby.getState() != ExposureState.PRIVATE)
                .toList();
    }

    /**
     * 전체 세부 취미(비공개 포함)를 대분류·표시 순서대로.
     * HobbyService(#08)의 관리자용 조회를 그대로 쓴다 — ADMIN이면 노출 규칙으로 숨기지 않고 취미 자신의 상태를 돌려준다.
     */
    private List<HobbyDTO> allHobbies() {
        return hobbyService.getCategories(Role.ADMIN).stream()
                .flatMap(category -> category.getHobbies().stream())
                .toList();
    }

    /**
     * 강사 계정 생성 — 중복 검사 → 임시 비밀번호 생성 → BCrypt 저장(mustChangePassword = true, 즉시 활성) → 선택한 취미 권한 부여 (AI-02).
     * 메일은 보내지 않는다(D-51). 관리자가 화면에서 임시 비밀번호를 한 번 보고 강사에게 직접 전달한다.
     * 호출: AdminInstructorController.create (POST /admin/instructors)
     *
     * @return 임시 비밀번호 평문 — 컨트롤러가 flash로 한 번만 보여 준다(저장·로그 금지)
     * @throws BusinessException DUPLICATE_LOGIN_ID, DUPLICATE_EMAIL, HOBBY_NOT_FOUND
     */
    @Transactional
    public String create(InstructorCreateDTO dto) {
        // 학습자 가입(UserService.signup)과 같은 정규화: 아이디·실명 앞뒤 공백 제거, 이메일 소문자
        String loginId = dto.getLoginId().trim();
        String email = dto.getEmail().trim().toLowerCase();

        // 컨트롤러에서 한 번 확인했지만 다른 관리자가 동시에 만들 수 있어 다시 검사한다
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String tempPassword = generateTempPassword();
        User instructor = User.createInstructor(loginId, passwordEncoder.encode(tempPassword), dto.getName().trim(), email);
        // 권한 행이 users.id를 가리키므로(FK) id를 먼저 확정한다
        userRepository.saveAndFlush(instructor);

        if (dto.getHobbyIds() != null) {
            dto.getHobbyIds().stream().distinct().forEach(hobbyId -> grant(instructor.getId(), hobbyId));
        }
        return tempPassword;
    }

    /**
     * 임시 비밀번호 재발급 — 새 임시 비밀번호로 바꾸고 다시 첫 로그인 변경을 강제한다 (AI-03).
     * 호출: AdminInstructorController.reissue (POST /admin/instructors/{instructorId}/reissue)
     *
     * @return 새 임시 비밀번호 평문 (flash 1회 표시용)
     * @throws BusinessException USER_NOT_FOUND
     */
    @Transactional
    public String reissue(Long instructorId) {
        User instructor = findInstructor(instructorId);
        String tempPassword = generateTempPassword();
        instructor.reissueTemporaryPassword(passwordEncoder.encode(tempPassword));  // 변경 감지로 저장
        return tempPassword;
    }

    /**
     * 강사 활성/비활성 변경 (AI-04). 비활성 강사는 로그인할 수 없다(AuthDetails.isEnabled → AuthFailHandler "disabled").
     * ❓ 비활성화한 강사가 담당 중인 분반 처리는 #10(D-52: 담당 공백 분반은 신규 배정 제외)에서 다룬다.
     * 호출: AdminInstructorController.changeStatus (POST /admin/instructors/{instructorId}/status)
     * @throws BusinessException USER_NOT_FOUND
     */
    @Transactional
    public void changeStatus(Long instructorId, boolean active) {
        findInstructor(instructorId).changeActive(active);
    }

    /**
     * 취미 활동 권한 부여 (AI-05). 이미 있으면 그대로 둔다(💡 API "이미 있으면 무시").
     * 호출: AdminInstructorController.grantHobby (POST /admin/instructors/{instructorId}/hobbies)
     * @throws BusinessException USER_NOT_FOUND, HOBBY_NOT_FOUND(없거나 비공개 취미)
     */
    @Transactional
    public void grantHobby(Long instructorId, Long hobbyId) {
        findInstructor(instructorId);
        grant(instructorId, hobbyId);
    }

    /**
     * 취미 활동 권한 회수 (AI-06). 행은 지우지 않고 is_active = false로 남긴다. 권한이 없으면 그대로 둔다.
     * ❓ 해당 취미 분반을 담당 중일 때 회수 거부(💡 API AI-06)는 #10 SectionService 계약이 생긴 뒤 추가한다.
     * 호출: AdminInstructorController.revokeHobby (POST /admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke)
     * @throws BusinessException USER_NOT_FOUND, HOBBY_NOT_FOUND(없는 취미)
     */
    @Transactional
    public void revokeHobby(Long instructorId, Long hobbyId) {
        findInstructor(instructorId);
        validateExistingHobby(hobbyId);
        grantRepository.findById(new InstructorHobbyGrantId(instructorId, hobbyId))
                .filter(InstructorHobbyGrant::isGranted)
                .ifPresent(grant -> grant.revoke(LocalDateTime.now()));  // 변경 감지로 저장
    }

    /**
     * 계약 C3: 강사가 해당 세부 취미 활동 권한을 가졌는지 (회수된 권한은 false).
     * 호출: SectionService(#10 분반 담당 지정 — "권한 없는 취미 분반 지정 불가"), 팀원 4 #18(공개 댓글 자격)
     * 시그니처 변경 금지 (팀원 2·4와 합의).
     */
    @Transactional(readOnly = true)
    public boolean hasHobbyGrant(Long instructorId, Long hobbyId) {
        if (instructorId == null || hobbyId == null) {
            return false;
        }
        return grantRepository.existsByInstructorIdAndHobbyIdAndIsActiveTrue(instructorId, hobbyId);
    }

    /**
     * [성능 개선] 강사가 활동 권한을 가진(회수되지 않은) 세부 취미 id 전체 — 조회 1번.
     * 취미마다 hasHobbyGrant(계약 C3)를 부르면 취미 수만큼 조회하므로(N+1), 여러 취미를 한꺼번에 확인할 때 쓴다. 판단 기준(is_active)은 C3와 같다.
     * 호출: WorkBoardService(강사 게시판 — 담당 취미 필터·댓글 가능 표시·담당 취미 이름)
     */
    @Transactional(readOnly = true)
    public Set<Long> getActiveGrantHobbyIds(Long instructorId) {
        if (instructorId == null) {
            return Set.of();
        }
        return grantRepository.findByInstructorIdInAndIsActiveTrue(List.of(instructorId)).stream()
                .map(InstructorHobbyGrant::getHobbyId)
                .collect(Collectors.toSet());
    }

    /**
     * 새 비밀번호로 쓸 수 있는지 — 아이디와 같거나 지금의 임시 비밀번호와 같으면 불가 (AU-14).
     * 임시 비밀번호 평문은 어디에도 없으므로 저장된 BCrypt 해시와 비교한다.
     * 호출: InstructorSetupController.changePassword (BindingResult 오류 표시용), changeInitialPassword(재확인)
     */
    @Transactional(readOnly = true)
    public boolean isUsableNewPassword(Long instructorId, String newPassword) {
        return isUsableNewPassword(findInstructor(instructorId), newPassword);
    }

    /**
     * 강사 첫 로그인 비밀번호 변경 — 새 비밀번호 BCrypt 저장, 변경 강제 해제 (AU-14, FR-012).
     * 호출: InstructorSetupController.changePassword (POST /auth/instructor/password)
     * 세션의 로그인 정보(mustChangePassword) 갱신은 컨트롤러가 한다 — 서비스는 HTTP 세션을 다루지 않는다.
     * @throws BusinessException USER_NOT_FOUND, INVALID_NEW_PASSWORD(아이디·임시 비밀번호와 같음), INVALID_INPUT(이미 변경함)
     */
    @Transactional
    public void changeInitialPassword(Long instructorId, PasswordChangeDTO dto) {
        User instructor = findInstructor(instructorId);
        if (!isUsableNewPassword(instructor, dto.getNewPassword())) {
            throw new BusinessException(ErrorCode.INVALID_NEW_PASSWORD);
        }
        instructor.completeInitialPasswordChange(passwordEncoder.encode(dto.getNewPassword()));  // 변경 감지로 저장
    }

    /** 강사 계정만 찾는다 — 다른 역할의 id로 접근하면 존재를 숨기려고 USER_NOT_FOUND(404) */
    private User findInstructor(Long instructorId) {
        return userRepository.findById(instructorId)
                .filter(User::isInstructor)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** C-09 규칙: 아이디(대소문자 무시)와 다르고, 현재 임시 비밀번호와도 달라야 한다 */
    private boolean isUsableNewPassword(User instructor, String newPassword) {
        if (newPassword == null) {
            return false;
        }
        return !newPassword.equalsIgnoreCase(instructor.getLoginId())
                && !passwordEncoder.matches(newPassword, instructor.getPasswordHash());
    }

    /** 권한 한 건 부여. 회수했던 권한은 같은 행을 되살린다(기본키가 같아 새 행을 만들 수 없음) */
    private void grant(Long instructorId, Long hobbyId) {
        validateGrantableHobby(hobbyId);
        grantRepository.findById(new InstructorHobbyGrantId(instructorId, hobbyId))
                .ifPresentOrElse(
                        existing -> {
                            if (!existing.isGranted()) {
                                existing.regrant(LocalDateTime.now());
                            }
                        },
                        () -> grantRepository.save(InstructorHobbyGrant.create(instructorId, hobbyId, LocalDateTime.now())));
    }

    /** 존재하는 운영·준비 중 취미인지 — 다른 도메인 표라 HobbyService 계약으로만 확인한다 */
    private void validateGrantableHobby(Long hobbyId) {
        boolean grantable = getGrantableHobbies().stream().anyMatch(hobby -> hobby.getId().equals(hobbyId));
        if (!grantable) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_FOUND);
        }
    }

    /**
     * 존재하는 취미인지 (AI-06 회수용). 부여와 달리 비공개 취미도 허용한다 —
     * 운영 중에 준 권한이 취미가 비공개로 바뀐 뒤에도 남아 있을 수 있어서 회수는 막지 않는다.
     */
    private void validateExistingHobby(Long hobbyId) {
        boolean exists = allHobbies().stream().anyMatch(hobby -> hobby.getId().equals(hobbyId));
        if (!exists) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_FOUND);
        }
    }

    /**
     * 임시 비밀번호 생성 — 네 종류 글자를 하나씩 넣고 나머지는 전체 글자에서 무작위로 고른 뒤 섞는다.
     * 결과는 가입 비밀번호 규칙(영문·숫자·특수문자 포함)도 만족한다. 🟢 java.security.SecureRandom(예측 불가 난수)
     */
    private String generateTempPassword() {
        List<Character> chars = new ArrayList<>(TEMP_PASSWORD_LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));
        while (chars.size() < TEMP_PASSWORD_LENGTH) {
            chars.add(pick(ALL_CHARS));
        }
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        chars.forEach(sb::append);
        return sb.toString();
    }

    /**
     * 계약 추가 요청(#18, 팀원 4): 해당 세부 취미의 활동 권한을 가진 강사 id 목록 (회수된 권한 제외).
     * 호출: CommentService — 공개 댓글이 달리면 취미 담당 강사에게 PUBLIC_COMMENT 알림
     */
    @Transactional(readOnly = true)
    public List<Long> getGrantedInstructorIds(Long hobbyId) {
        return grantRepository.findByHobbyIdAndIsActiveTrue(hobbyId).stream()
                .map(InstructorHobbyGrant::getInstructorId).toList();
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
