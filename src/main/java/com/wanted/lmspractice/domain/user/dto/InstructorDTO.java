package com.wanted.lmspractice.domain.user.dto;

import com.wanted.lmspractice.domain.user.entity.User;
import com.wanted.lmspractice.domain.user.entity.UserStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 강사 계정 한 명의 표시용 데이터 (관리자 강사 목록 A-S04, 강사 비밀번호 변경 화면 C-09의 계정 정보)
 * [담당] 팀원 1 · #05   [화면] A-S04, C-09   [API] AI-01 모델 instructors, AU-13
 * [연결]
 *  - 호출받음: InstructorAccountService(getInstructors·getInstructor) → AdminInstructorController·InstructorSetupController
 *             → templates/admin/instructors.html, templates/auth/instructor-password.html
 *  - 호출함:   User(엔티티 값 복사)
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * 실명·이메일이 들어 있으므로 관리자 화면과 본인 화면에만 쓴다(NFR-04). 비밀번호 값은 담지 않는다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class InstructorDTO {

    private Long id;
    private String loginId;
    private String realName;
    private String email;
    private UserStatus status;
    private boolean mustChangePassword;
    private List<Long> hobbyIds;      // 유효한 취미 권한 id (취미 활동 권한 표의 체크 여부)
    private List<String> hobbyNames;  // 같은 순서의 취미 이름 (계정 표의 "취미 권한" 칸)
    private int sectionCount;         // 담당 분반 수 — AdminInstructorController가 SectionService(C6)로 채운다

    /** User 엔티티 + 취미 권한 → 화면용 DTO. 담당 분반 수는 0으로 두고 컨트롤러가 채운다 */
    public static InstructorDTO from(User user, List<Long> hobbyIds, List<String> hobbyNames) {
        return new InstructorDTO(
                user.getId(),
                user.getLoginId(),
                user.getRealName(),
                user.getEmail(),
                user.getStatus(),
                user.requiresPasswordChange(),
                hobbyIds,
                hobbyNames,
                0);
    }

    /** 첫 로그인 전(임시 비밀번호 상태)인 활성 강사 — 표에 "첫 로그인 전" 칩을 보인다 */
    public boolean isPendingFirstLogin() {
        return status == UserStatus.ACTIVE && mustChangePassword;
    }

    /** 활성 계정인지 (비활성화 버튼 / 활성화 버튼 선택) */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** 해당 취미 권한이 있는지 (취미 활동 권한 표의 체크박스) */
    public boolean hasHobby(Long hobbyId) {
        return hobbyIds != null && hobbyIds.contains(hobbyId);
    }

    /** "색연필화 / 디지털 드로잉" 형태. 권한이 없으면 "-" */
    public String getHobbyNamesText() {
        return (hobbyNames == null || hobbyNames.isEmpty()) ? "-" : String.join(" / ", hobbyNames);
    }
}
