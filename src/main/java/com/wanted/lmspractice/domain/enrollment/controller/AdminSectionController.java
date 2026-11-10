package com.wanted.lmspractice.domain.enrollment.controller;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SectionFormDTO;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
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
 * [역할] 관리자 분반 관리 화면(A-S05): 강의 선택 → 분반 목록, 분반 생성, 정원 변경, 담당 강사 지정·변경
 *        화면 제목의 "수강 이동"은 구 기준 문구 — 수강생 분반 이동은 만들지 않고 담당 변경만 한다(D-59, 이슈 #10 구현 범위).
 * [담당] 팀원 2 · #10   [화면] A-S05   [기획] FR-052, D-52, D-59   [API] SE-01~04
 * [연결]
 *  - 호출받음: 브라우저(GET·POST /admin/sections/**) — ADMIN만(SecurityConfig /admin/**, USER·강사는 403)
 *  - 호출함:   SectionService(조회·생성·정원·담당), CourseCatalogService.getAdminCourses(강의 선택 + 대기 인원)
 *  - 화면:     templates/admin/sections.html
 *  - 패널 열기: 시안의 펼침 버튼은 링크로 바꿔 서버가 다시 그린다 — ?create=true(생성), ?edit={분반id}(정원), ?assign={분반id}(담당)
 * [기술] 🟢 @Controller, @Valid·BindingResult, RedirectAttributes(flash), @PathVariable·@RequestParam
 */
@Controller
@RequestMapping("/admin/sections")
@RequiredArgsConstructor
public class AdminSectionController {

    private static final String VIEW = "admin/sections";

    private final SectionService sectionService;
    private final CourseCatalogService courseCatalogService;

    /**
     * 분반 관리 화면 (SE-01). courseId가 없으면 강의 선택 목록만 보인다.
     * 모델: courses, selectedCourse, sections, candidates, sectionFormDTO, showCreateForm, editSectionId, assignSection
     *
     * @throws com.wanted.lmspractice.global.exception.BusinessException COURSE_NOT_FOUND
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long courseId,
                       @RequestParam(defaultValue = "false") boolean create,
                       @RequestParam(required = false) Long edit,
                       @RequestParam(required = false) Long assign,
                       Model model) {
        SectionFormDTO form = new SectionFormDTO();
        form.setCourseId(courseId);
        model.addAttribute("sectionFormDTO", form);
        addListModel(model, courseId, create, edit, assign);
        return VIEW;
    }

    /**
     * 분반 생성 (SE-02). 형식 오류·이름 중복이면 입력값을 유지한 채 생성 패널을 펼친 화면을 다시 보여 준다.
     * 성공: redirect:/admin/sections?courseId={courseId} + flash message
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("sectionFormDTO") SectionFormDTO sectionFormDTO,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        // 이름 중복은 형식 검증으로 알 수 없어서 BindingResult에 직접 넣는다 (AdminInstructorController와 같은 방식)
        if (!bindingResult.hasFieldErrors("name") && sectionFormDTO.getCourseId() != null
                && !sectionService.isNameAvailable(sectionFormDTO.getCourseId(), sectionFormDTO.getTrimmedName())) {
            bindingResult.rejectValue("name", "duplicate", "이 강의에 같은 이름의 분반이 있어요.");
        }
        if (bindingResult.hasErrors()) {
            addListModel(model, sectionFormDTO.getCourseId(), true, null, null);
            return VIEW;
        }

        Long sectionId = sectionService.create(sectionFormDTO.getCourseId(), sectionFormDTO);
        SectionDTO created = sectionService.getSection(sectionId);
        redirectAttributes.addFlashAttribute("message", created.getDisplayName() + "을 만들었어요.");
        return "redirect:/admin/sections?courseId=" + created.getCourseId();
    }

    /**
     * 정원 변경 (SE-03). 범위(1~30)·활성 수강 미만이면 같은 칸을 다시 펼치고 안내한다(400·409 → flash).
     * 성공: redirect:/admin/sections?courseId={courseId} + flash message(자동 확정 인원 포함)
     */
    @PostMapping("/{sectionId}/capacity")
    public String changeCapacity(@PathVariable Long sectionId,
                                 @RequestParam(required = false) Integer capacity,
                                 RedirectAttributes redirectAttributes) {
        SectionDTO section = sectionService.getSection(sectionId);
        int requested = capacity == null ? 0 : capacity;  // 빈 칸이면 범위 밖(0)으로 보고 같은 안내를 보인다
        Optional<ErrorCode> error = sectionService.checkCapacity(sectionId, requested);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", error.get().getMessage());
            return "redirect:/admin/sections?courseId=" + section.getCourseId() + "&edit=" + sectionId;
        }

        int confirmed = sectionService.changeCapacity(sectionId, requested);
        redirectAttributes.addFlashAttribute("message",
                section.getDisplayName() + " 정원을 " + requested + "명으로 바꿨어요." + confirmedText(confirmed));
        return "redirect:/admin/sections?courseId=" + section.getCourseId();
    }

    /**
     * 담당 강사 지정·변경 (SE-04). 수강생은 분반을 옮기지 않는다(D-59).
     * 성공: redirect:/admin/sections?courseId={courseId} + flash message (from=dashboard 면 관리자 대시보드 /admin 으로)
     * 실패: USER_NOT_FOUND(404)·INSTRUCTOR_NOT_GRANTED(403) — 화면은 후보만 보여 주므로 조작된 요청일 때만 오류 화면
     */
    @PostMapping("/{sectionId}/instructor")
    public String changeInstructor(@PathVariable Long sectionId,
                                   @RequestParam Long instructorId,
                                   @RequestParam(required = false) String from,
                                   RedirectAttributes redirectAttributes) {
        SectionDTO section = sectionService.getSection(sectionId);
        int confirmed = sectionService.changeInstructor(sectionId, instructorId);
        redirectAttributes.addFlashAttribute("message",
                section.getDisplayName() + " 담당 강사를 저장했어요." + confirmedText(confirmed));
        if ("dashboard".equals(from)) {
            return "redirect:/admin";  // 관리자 대시보드(A-S01)의 담당 공백 분반에서 바로 지정한 경우
        }
        return "redirect:/admin/sections?courseId=" + section.getCourseId();
    }

    /** 목록 화면 공통 모델. 생성 실패로 화면을 다시 그릴 때도 같은 모델이 필요하다 */
    private void addListModel(Model model, Long courseId, boolean create, Long editSectionId, Long assignSectionId) {
        List<CourseDTO> courses = courseCatalogService.getAdminCourses();
        model.addAttribute("courses", courses);
        if (courseId == null) {
            return;  // SE-01: 강의를 고르기 전에는 선택 목록만
        }
        List<SectionDTO> sections = sectionService.getSectionsForAdmin(courseId);  // 없는 강의면 COURSE_NOT_FOUND
        model.addAttribute("selectedCourse", courses.stream()
                .filter(course -> course.getId().equals(courseId)).findFirst().orElse(null));
        model.addAttribute("sections", sections);
        model.addAttribute("candidates", sectionService.getCandidates(courseId));
        model.addAttribute("showCreateForm", create);
        model.addAttribute("editSectionId", editSectionId);
        model.addAttribute("assignSection", sections.stream()
                .filter(section -> section.getId().equals(assignSectionId)).findFirst().orElse(null));
    }

    /** 자동 확정 인원 안내 (SE-03 💡 "자동 확정 인원 포함") */
    private static String confirmedText(int confirmed) {
        return confirmed > 0 ? " 대기자 " + confirmed + "명이 자동으로 수강 확정되었어요." : "";
    }
}
