package com.wanted.lmspractice.domain.user.controller;

import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.user.dto.InstructorCreateDTO;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 관리자 강사 계정·권한 화면(A-S04): 목록·검색, 생성(임시 비밀번호 1회 표시), 재발급, 활성/비활성, 취미 권한 부여·회수
 * [담당] 팀원 1 · #05   [화면] A-S04 (탭: 전문 강사 계정 / 취미 활동 권한)   [기획] FR-012, D-51   [API] AI-01~06
 *       "취미 담당 강사 지정" 탭은 만들지 않는다(11-06 결정). 분반 담당 지정은 #10(팀원 2) A-S05.
 * [연결]
 *  - 호출받음: 브라우저(GET·POST /admin/instructors/**) — ADMIN만(SecurityConfig, USER·강사는 403)
 *  - 호출함:   InstructorAccountService(목록·생성·재발급·상태·권한), UserService(아이디·이메일 중복 확인 — 가입과 같은 메서드),
 *             SectionService.getSectionsByInstructor(계약 C6, 담당 분반 수 — #10 전까지는 빈 목록)
 *  - 화면:     templates/admin/instructors.html
 * [기술] 🟢 @Controller, @Valid·BindingResult, RedirectAttributes(flash 1회 표시), @PathVariable·@RequestParam
 */
@Controller
@RequestMapping("/admin/instructors")
@RequiredArgsConstructor
public class AdminInstructorController {

    private static final String TAB_ACCOUNT = "account";
    private static final String TAB_HOBBY = "hobby";

    private final InstructorAccountService instructorAccountService;
    private final UserService userService;
    private final SectionService sectionService;

    /**
     * 강사 계정·권한 화면. 호출: GET /admin/instructors (AI-01)
     * 모델: instructors, hobbies, instructorCreateDTO, tab, keyword, showCreateForm
     *       + 생성·재발급 직후에만 flash tempPassword·message (새로고침하면 사라진다)
     *
     * @param tab    account(기본) / hobby — 시안의 탭 버튼을 링크로 바꿔 서버가 다시 그린다
     * @param create true면 생성 폼을 펼친다 (시안의 "전문 강사 생성" 버튼)
     */
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = TAB_ACCOUNT) String tab,
                       @RequestParam(defaultValue = "false") boolean create,
                       Model model,
                       HttpServletResponse response) {
        if (model.containsAttribute("tempPassword")) {
            // 임시 비밀번호가 보이는 화면은 브라우저 캐시(뒤로 가기)에도 남기지 않는다 — "1회만 표시"
            response.setHeader("Cache-Control", "no-store");
        }
        model.addAttribute("instructorCreateDTO", new InstructorCreateDTO());
        model.addAttribute("showCreateForm", create);
        addListModel(model, keyword, tab);
        return "admin/instructors";
    }

    /**
     * 강사 계정 생성. 호출: POST /admin/instructors (AI-02)
     * 형식 오류·중복이면 입력값을 유지한 채 생성 폼을 펼친 화면을 다시 보여 준다.
     * 성공하면 임시 비밀번호를 flash로 한 번만 보여 준다(로그·DB에 평문 저장 안 함).
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("instructorCreateDTO") InstructorCreateDTO instructorCreateDTO,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        // 중복은 형식 검증으로 알 수 없어서 BindingResult에 직접 넣는다 (SignupController와 같은 방식)
        if (!bindingResult.hasFieldErrors("loginId") && !userService.isLoginIdAvailable(instructorCreateDTO.getLoginId())) {
            bindingResult.rejectValue("loginId", "duplicate", ErrorCode.DUPLICATE_LOGIN_ID.getMessage());
        }
        if (!bindingResult.hasFieldErrors("email") && !userService.isEmailAvailable(instructorCreateDTO.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", ErrorCode.DUPLICATE_EMAIL.getMessage());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("showCreateForm", true);
            addListModel(model, null, TAB_ACCOUNT);
            return "admin/instructors";
        }

        String tempPassword = instructorAccountService.create(instructorCreateDTO);
        redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
        redirectAttributes.addFlashAttribute("message",
                instructorCreateDTO.getLoginId().trim() + " 강사 계정을 만들었어요.");
        return "redirect:/admin/instructors";
    }

    /** 임시 비밀번호 재발급 — 다시 첫 로그인 변경 강제. 호출: POST /admin/instructors/{instructorId}/reissue (AI-03) */
    @PostMapping("/{instructorId}/reissue")
    public String reissue(@PathVariable Long instructorId, RedirectAttributes redirectAttributes) {
        String tempPassword = instructorAccountService.reissue(instructorId);
        redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
        redirectAttributes.addFlashAttribute("message", "임시 비밀번호를 재발급했어요.");
        return "redirect:/admin/instructors";
    }

    /** 활성/비활성 변경. 호출: POST /admin/instructors/{instructorId}/status, Form active (AI-04) */
    @PostMapping("/{instructorId}/status")
    public String changeStatus(@PathVariable Long instructorId,
                               @RequestParam boolean active,
                               RedirectAttributes redirectAttributes) {
        instructorAccountService.changeStatus(instructorId, active);
        redirectAttributes.addFlashAttribute("message", active ? "강사 계정을 활성화했어요." : "강사 계정을 비활성화했어요.");
        return "redirect:/admin/instructors";
    }

    /** 취미 활동 권한 부여. 호출: POST /admin/instructors/{instructorId}/hobbies, Form hobbyId (AI-05) */
    @PostMapping("/{instructorId}/hobbies")
    public String grantHobby(@PathVariable Long instructorId,
                             @RequestParam Long hobbyId,
                             RedirectAttributes redirectAttributes) {
        instructorAccountService.grantHobby(instructorId, hobbyId);
        redirectAttributes.addFlashAttribute("message", "취미 활동 권한을 저장했어요.");
        return "redirect:/admin/instructors?tab=" + TAB_HOBBY;
    }

    /** 취미 활동 권한 회수. 호출: POST /admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke (AI-06) */
    @PostMapping("/{instructorId}/hobbies/{hobbyId}/revoke")
    public String revokeHobby(@PathVariable Long instructorId,
                              @PathVariable Long hobbyId,
                              RedirectAttributes redirectAttributes) {
        instructorAccountService.revokeHobby(instructorId, hobbyId);
        redirectAttributes.addFlashAttribute("message", "취미 활동 권한을 회수했어요.");
        return "redirect:/admin/instructors?tab=" + TAB_HOBBY;
    }

    /** 목록 화면 공통 모델. 생성 실패로 화면을 다시 그릴 때도 같은 모델이 필요하다 */
    private void addListModel(Model model, String keyword, String tab) {
        List<InstructorDTO> instructors = instructorAccountService.getInstructors(keyword);
        // 담당 분반 수는 SectionService(C6)에서 채운다. InstructorAccountService에서 부르면
        // SectionService(#10) → InstructorAccountService(C3)와 순환 참조가 되므로 화면 조립 단계인 여기서 채운다.
        instructors.forEach(instructor ->
                instructor.setSectionCount(sectionService.getSectionsByInstructor(instructor.getId()).size()));

        model.addAttribute("instructors", instructors);
        model.addAttribute("hobbies", instructorAccountService.getGrantableHobbies());
        model.addAttribute("tab", TAB_HOBBY.equals(tab) ? TAB_HOBBY : TAB_ACCOUNT);
        model.addAttribute("keyword", keyword);
    }
}
