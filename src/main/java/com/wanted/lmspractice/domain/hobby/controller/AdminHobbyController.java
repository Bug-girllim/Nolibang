package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 관리자 취미 화면들 — A-S15 취미 탐색 · A-S02 대분류 관리(+대분류 추가) · A-S03 세부 취미(+세부 취미 추가) · A-S06 콘텐츠 검수
 * [담당] 팀원 2 · #08   [화면] A-S15, A-S02, A-S03, A-S06   [API] HB-03
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/hobbies, /admin/categories(?create=true), /admin/sub-hobbies(?create=true&categoryId=),
 *             /admin/content-review, POST /admin/categories, /admin/sub-hobbies
 *             — SecurityConfig가 /admin/**를 ADMIN만 허용한다. 메뉴는 common/layout.html의 adminMenu 조각
 *  - 호출함:   CourseCatalogService.getAdminCategories(대분류·세부 취미·강의/차시 수), getAdminCourses(전체 강의),
 *             HobbyService(이름 중복 확인·대분류·세부 취미 추가)
 *  - 화면:     templates/admin/hobbies.html(A-S15), templates/admin/hobby-manage.html(A-S02·A-S03·A-S06 — view 값으로 구분)
 * [동작] 추가는 분반 관리(AdminSectionController)와 같은 방식: [추가] 버튼 = ?create=true 로 입력 패널을 펼치고,
 *        형식 오류·이름 중복이면 입력값을 유지한 채 패널을 다시 보여 주며, 성공하면 PRG(redirect + flash message).
 *        수정·삭제는 하지 않는다(D-59: 기존 데이터는 시드로 관리).
 * [기술] 🟢 @Controller, @GetMapping/@PostMapping, @Valid + BindingResult, RedirectAttributes flash
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHobbyController {

    private static final String MANAGE_VIEW = "admin/hobby-manage";

    private final CourseCatalogService courseCatalogService;
    private final HobbyService hobbyService;

    /**
     * 비공개를 포함한 전체 취미 조회. 호출: GET /admin/hobbies (API HB-03)
     * 모델: categories(List&lt;CategoryDTO&gt; — 하위 세부 취미에 공개/전체 강의 수·차시 수가 들어 있다)
     */
    @GetMapping("/hobbies")
    public String list(Model model) {
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        return "admin/hobbies";
    }

    /**
     * A-S02 대분류 관리 — 대분류별 상태·세부 취미 수·공개 강의 수. create=true면 대분류 추가 패널을 펼친다.
     * 모델: view, categories, states, showCreateForm, categoryFormDTO
     */
    @GetMapping("/categories")
    public String categories(@RequestParam(defaultValue = "false") boolean create, Model model) {
        model.addAttribute("categoryFormDTO", new CategoryFormDTO());
        addCategoriesModel(model, create);
        return MANAGE_VIEW;
    }

    /**
     * 대분류 추가 (A-S02). 형식 오류·이름 중복이면 입력값을 유지한 채 추가 패널을 다시 보여 준다.
     * 성공: redirect:/admin/categories + flash message
     */
    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("categoryFormDTO") CategoryFormDTO categoryFormDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        // 이름 중복은 형식 검증으로 알 수 없어서 BindingResult에 직접 넣는다 (AdminSectionController와 같은 방식)
        if (!bindingResult.hasFieldErrors("name")
                && !hobbyService.isCategoryNameAvailable(categoryFormDTO.getTrimmedName())) {
            bindingResult.rejectValue("name", "duplicate", "같은 이름의 대분류가 이미 있어요.");
        }
        if (bindingResult.hasErrors()) {
            addCategoriesModel(model, true);
            return MANAGE_VIEW;
        }

        hobbyService.createCategory(categoryFormDTO);
        redirectAttributes.addFlashAttribute("message",
                "'" + categoryFormDTO.getTrimmedName() + "' 대분류를 추가했어요. 이제 세부 취미를 추가할 수 있어요.");
        return "redirect:/admin/categories";
    }

    /**
     * A-S03 세부 취미 — 전체 세부 취미를 대분류와 함께 한 표로. create=true면 세부 취미 추가 패널을 펼친다
     * (categoryId가 있으면 그 대분류를 미리 골라 둔다 — 대분류 관리 화면의 [세부 취미 추가]에서 올 때).
     * 모델: view, categories, states, showCreateForm, hobbyFormDTO
     */
    @GetMapping("/sub-hobbies")
    public String subHobbies(@RequestParam(defaultValue = "false") boolean create,
                             @RequestParam(required = false) Long categoryId,
                             Model model) {
        HobbyFormDTO form = new HobbyFormDTO();
        form.setCategoryId(categoryId);
        model.addAttribute("hobbyFormDTO", form);
        addHobbiesModel(model, create);
        return MANAGE_VIEW;
    }

    /**
     * 세부 취미 추가 (A-S03). 형식 오류·없는 대분류·같은 대분류 안 이름 중복이면 입력값을 유지한 채 추가 패널을 다시 보여 준다.
     * 성공: redirect:/admin/sub-hobbies + flash message
     */
    @PostMapping("/sub-hobbies")
    public String createHobby(@Valid @ModelAttribute("hobbyFormDTO") HobbyFormDTO hobbyFormDTO,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("categoryId") && !hobbyService.categoryExists(hobbyFormDTO.getCategoryId())) {
            bindingResult.rejectValue("categoryId", "notFound", "대분류를 다시 선택해 주세요.");
        }
        if (!bindingResult.hasFieldErrors("name") && !bindingResult.hasFieldErrors("categoryId")
                && !hobbyService.isHobbyNameAvailable(hobbyFormDTO.getCategoryId(), hobbyFormDTO.getTrimmedName())) {
            bindingResult.rejectValue("name", "duplicate", "이 대분류에 같은 이름의 세부 취미가 이미 있어요.");
        }
        if (bindingResult.hasErrors()) {
            addHobbiesModel(model, true);
            return MANAGE_VIEW;
        }

        hobbyService.createHobby(hobbyFormDTO);
        redirectAttributes.addFlashAttribute("message",
                "'" + hobbyFormDTO.getTrimmedName() + "' 세부 취미를 추가했어요.");
        return "redirect:/admin/sub-hobbies";
    }

    /**
     * A-S06 콘텐츠 검수 — 전체 강의 목록. 강의는 시드 데이터로 등록돼 검수 대기 항목이 없다(D-59).
     * 모델: view, courses(List&lt;CourseDTO&gt;), hobbyNames(세부 취미 id → 이름)
     */
    @GetMapping("/content-review")
    public String contentReview(Model model) {
        List<CategoryDTO> categories = courseCatalogService.getAdminCategories();
        Map<Long, String> hobbyNames = categories.stream()
                .flatMap(category -> category.getHobbies().stream())
                .collect(Collectors.toMap(HobbyDTO::getId, HobbyDTO::getName, (a, b) -> a));
        model.addAttribute("view", "review");
        model.addAttribute("courses", courseCatalogService.getAdminCourses());
        model.addAttribute("hobbyNames", hobbyNames);
        return MANAGE_VIEW;
    }

    /** A-S02 화면 공통 모델 (GET 과 검증 실패 시 다시 그릴 때 같이 쓴다) */
    private void addCategoriesModel(Model model, boolean showCreateForm) {
        model.addAttribute("view", "categories");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        model.addAttribute("states", ExposureState.values());
        model.addAttribute("showCreateForm", showCreateForm);
    }

    /** A-S03 화면 공통 모델 (GET 과 검증 실패 시 다시 그릴 때 같이 쓴다) */
    private void addHobbiesModel(Model model, boolean showCreateForm) {
        model.addAttribute("view", "hobbies");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        model.addAttribute("states", ExposureState.values());
        model.addAttribute("showCreateForm", showCreateForm);
    }
}
