package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
 * [역할] 관리자 취미 화면들 — A-S15 취미 탐색(+대분류 상태 편집·상태 필터·검색) · A-S02 대분류 관리(+대분류 추가) · A-S03 세부 취미(+세부 취미 추가) · A-S06 콘텐츠 검수
 * [담당] 팀원 2 · #08   [화면] A-S15, A-S02, A-S03, A-S06   [API] HB-03
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/hobbies(?state=&keyword=), /admin/categories(?create=true), /admin/sub-hobbies(?create=true&categoryId=),
 *             /admin/content-review, POST /admin/categories, /admin/sub-hobbies, /admin/hobbies/categories/{id}/state
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
     * 비공개를 포함한 전체 취미 조회 + 대분류 상태 필터·검색. 호출: GET /admin/hobbies?state=&keyword= (API HB-03)
     * state: OPERATING·PREPARING·PRIVATE 중 하나면 그 상태의 대분류만(그 밖의 값·빈 값 = 전체).
     * keyword: 대분류 이름에 들어 있으면 그 대분류 전체, 아니면 이름에 들어 있는 세부 취미만 남긴다(대소문자 무시).
     * 모델: categories(필터 결과), allCategories(요약 숫자용 전체), stateCounts(상태별 대분류 수), states, state(고른 상태 이름, 전체면 null), keyword
     */
    @GetMapping("/hobbies")
    public String list(@RequestParam(required = false) String state,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        List<CategoryDTO> all = courseCatalogService.getAdminCategories();
        ExposureState selected = parseState(state);
        String trimmed = keyword == null ? "" : keyword.trim();

        // 상태 필터 버튼 옆 개수 (상태 이름 → 대분류 수, 없는 상태는 0)
        Map<String, Long> stateCounts = new LinkedHashMap<>();
        for (ExposureState s : ExposureState.values()) {
            stateCounts.put(s.name(), all.stream().filter(category -> category.getState() == s).count());
        }

        model.addAttribute("allCategories", all);
        model.addAttribute("stateCounts", stateCounts);
        model.addAttribute("categories", filter(all, selected, trimmed));
        model.addAttribute("states", ExposureState.values());
        model.addAttribute("state", selected == null ? null : selected.name());
        model.addAttribute("keyword", trimmed);
        return "admin/hobbies";
    }

    /**
     * 대분류 노출 상태 편집 (A-S15 대분류 상태 칸 + [편집]). 바꾼 뒤 보던 필터·검색 그대로 목록으로 돌아간다(PRG).
     * 실패: 없는 상태 값 → 안내 문구, 없는 대분류 → HOBBY_NOT_FOUND(공통 오류 화면, 화면은 목록에서만 고르므로 조작된 요청)
     */
    @PostMapping("/hobbies/categories/{categoryId}/state")
    public String changeCategoryState(@PathVariable Long categoryId,
                                      @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String returnState,
                                      @RequestParam(required = false) String returnKeyword,
                                      RedirectAttributes redirectAttributes) {
        ExposureState newState = parseState(state);
        if (newState == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "대분류 상태를 운영 중·준비 중·비공개 중에서 골라 주세요.");
        } else {
            String name = hobbyService.changeCategoryState(categoryId, newState);
            redirectAttributes.addFlashAttribute("message",
                    "'" + name + "' 대분류를 " + newState.getLabel() + " 상태로 바꿨어요.");
        }
        // 보던 필터·검색 유지 — 값은 RedirectAttributes가 URL 인코딩해 붙인다
        ExposureState back = parseState(returnState);
        if (back != null) {
            redirectAttributes.addAttribute("state", back.name());
        }
        if (returnKeyword != null && !returnKeyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", returnKeyword.trim());
        }
        return "redirect:/admin/hobbies";
    }

    /** 상태 이름 → ExposureState. 빈 값·모르는 값은 null(= 전체) */
    private ExposureState parseState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        for (ExposureState s : ExposureState.values()) {
            if (s.name().equals(state)) {
                return s;
            }
        }
        return null;
    }

    /** 상태 필터 → 검색어 순서로 거른 대분류 목록 (검색어가 세부 취미에만 맞으면 그 세부 취미만 남긴 사본을 만든다) */
    private List<CategoryDTO> filter(List<CategoryDTO> all, ExposureState state, String keyword) {
        String key = keyword.toLowerCase();
        return all.stream()
                .filter(category -> state == null || category.getState() == state)
                .map(category -> {
                    if (key.isEmpty() || category.getName().toLowerCase().contains(key)) {
                        return category;
                    }
                    List<HobbyDTO> matched = category.getHobbies().stream()
                            .filter(hobby -> hobby.getName().toLowerCase().contains(key))
                            .toList();
                    return matched.isEmpty() ? null : new CategoryDTO(category.getId(), category.getCode(),
                            category.getName(), category.getState(), category.getImagePath(), matched);
                })
                .filter(Objects::nonNull)
                .toList();
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
