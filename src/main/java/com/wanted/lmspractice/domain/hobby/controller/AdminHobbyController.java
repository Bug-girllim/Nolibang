package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.course.dto.CourseFormDTO;
import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
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
 * [역할] 관리자 취미 화면들 — A-S15 취미 탐색(+대분류·세부 취미 상태 편집·상태 필터·검색) · A-S02 대분류 관리(+추가·편집·삭제) · A-S03 세부 취미(+추가·편집) · A-S06 콘텐츠 검수(+강의 편집)
 * [담당] 팀원 2 · #08   [화면] A-S15, A-S02, A-S03, A-S06   [API] HB-03
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/hobbies(?state=&keyword=), /admin/categories(?create=true|edit=), /admin/sub-hobbies(?create=true&categoryId=|edit=),
 *             /admin/content-review(?edit=), POST /admin/categories(/{id}, /{id}/delete), /admin/sub-hobbies(/{id}),
 *             /admin/content-review/courses/{courseId},
 *             /admin/hobbies/categories/{id}/state, /admin/hobbies/{hobbyId}/state
 *             — SecurityConfig가 /admin/**를 ADMIN만 허용한다. 메뉴는 common/layout.html의 adminMenu 조각
 *  - 호출함:   CourseCatalogService.getAdminCategories(대분류·세부 취미·강의/차시 수), getAdminCourses(전체 강의),
 *             HobbyService(이름 중복 확인·대분류·세부 취미 추가·편집·삭제), CourseService(강의 편집·공개 상태)
 *  - 화면:     templates/admin/hobbies.html(A-S15), templates/admin/hobby-manage.html(A-S02·A-S03·A-S06 — view 값으로 구분)
 * [동작] 추가·편집은 분반 관리(AdminSectionController)와 같은 방식: [추가] = ?create=true, [편집] = ?edit={id} 로 입력 패널을 펼치고,
 *        형식 오류·이름 중복이면 입력값을 유지한 채 패널을 다시 보여 주며, 성공하면 PRG(redirect + flash message).
 *        편집 패널은 현재 값으로 채운다. 삭제는 세부 취미가 없는 대분류만(잘못 만든 대분류 정리용). 세부 취미·강의 삭제는 수강 기록과 연결돼 하지 않는다.
 * [기술] 🟢 @Controller, @GetMapping/@PostMapping, @Valid + BindingResult, RedirectAttributes flash
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHobbyController {

    private static final String MANAGE_VIEW = "admin/hobby-manage";

    private final CourseCatalogService courseCatalogService;
    private final HobbyService hobbyService;
    private final CourseService courseService;

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
        return backToList(returnState, returnKeyword, redirectAttributes);
    }

    /**
     * 세부 취미 노출 상태 편집 (A-S15 세부 취미 줄의 상태 칸 + [편집]). 바꾼 뒤 보던 필터·검색 그대로 목록으로 돌아간다(PRG).
     * 대분류가 비공개·준비 중이면 운영 중으로 바꿔도 학습자에게 보이지 않아서 안내 문구에 그 사실을 덧붙인다.
     * 실패: 없는 상태 값 → 안내 문구, 없는 세부 취미 → HOBBY_NOT_FOUND(공통 오류 화면)
     */
    @PostMapping("/hobbies/{hobbyId}/state")
    public String changeHobbyState(@PathVariable Long hobbyId,
                                   @RequestParam(required = false) String state,
                                   @RequestParam(required = false) String categoryState,
                                   @RequestParam(required = false) String returnState,
                                   @RequestParam(required = false) String returnKeyword,
                                   RedirectAttributes redirectAttributes) {
        ExposureState newState = parseState(state);
        if (newState == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "세부 취미 상태를 운영 중·준비 중·비공개 중에서 골라 주세요.");
        } else {
            String name = hobbyService.changeHobbyState(hobbyId, newState);
            ExposureState parent = parseState(categoryState);
            String note = (newState != ExposureState.PRIVATE && parent != null && parent != ExposureState.OPERATING)
                    ? " 대분류가 " + parent.getLabel() + " 상태라 학습자에게는 아직 보이지 않아요."
                    : "";
            redirectAttributes.addFlashAttribute("message",
                    "'" + name + "' 세부 취미를 " + newState.getLabel() + " 상태로 바꿨어요." + note);
        }
        return backToList(returnState, returnKeyword, redirectAttributes);
    }

    /** 상태 편집 뒤 보던 필터·검색 그대로 취미 탐색 목록으로 — 값은 RedirectAttributes가 URL 인코딩해 붙인다 */
    private String backToList(String returnState, String returnKeyword, RedirectAttributes redirectAttributes) {
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

    // ===================== A-S02 대분류 관리 (추가·편집·삭제) =====================

    /**
     * A-S02 대분류 관리 — 대분류별 상태·세부 취미 수·공개 강의 수.
     * create=true면 대분류 추가 패널을, edit={id}면 그 대분류의 편집 패널(현재 값으로 채움)을 펼친다.
     * 모델: view, categories, states, showCreateForm, editId, categoryFormDTO
     */
    @GetMapping("/categories")
    public String categories(@RequestParam(defaultValue = "false") boolean create,
                             @RequestParam(required = false) Long edit,
                             Model model) {
        model.addAttribute("categoryFormDTO", edit != null ? hobbyService.getCategoryForm(edit) : new CategoryFormDTO());
        addCategoriesModel(model, create && edit == null, edit);
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
            addCategoriesModel(model, true, null);
            return MANAGE_VIEW;
        }

        hobbyService.createCategory(categoryFormDTO);
        redirectAttributes.addFlashAttribute("message",
                "'" + categoryFormDTO.getTrimmedName() + "' 대분류를 추가했어요. 이제 세부 취미를 추가할 수 있어요.");
        return "redirect:/admin/categories";
    }

    /**
     * 대분류 편집 (A-S02) — 이름·노출 상태. 형식 오류·이름 중복(자기 자신 제외)이면 입력값을 유지한 채 편집 패널을 다시 보여 준다.
     * 성공: redirect:/admin/categories + flash message.  실패: 없는 대분류 → HOBBY_NOT_FOUND(공통 오류 화면)
     */
    @PostMapping("/categories/{categoryId}")
    public String updateCategory(@PathVariable Long categoryId,
                                 @Valid @ModelAttribute("categoryFormDTO") CategoryFormDTO categoryFormDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("name")
                && !hobbyService.isCategoryNameAvailable(categoryFormDTO.getTrimmedName(), categoryId)) {
            bindingResult.rejectValue("name", "duplicate", "같은 이름의 대분류가 이미 있어요.");
        }
        if (bindingResult.hasErrors()) {
            addCategoriesModel(model, false, categoryId);
            return MANAGE_VIEW;
        }

        hobbyService.updateCategory(categoryId, categoryFormDTO);
        redirectAttributes.addFlashAttribute("message", "'" + categoryFormDTO.getTrimmedName() + "' 대분류를 저장했어요.");
        return "redirect:/admin/categories";
    }

    /**
     * 대분류 삭제 (A-S02) — 세부 취미가 하나도 없는 대분류만 지운다(잘못 만든 대분류 정리용).
     * 성공·실패 모두 redirect:/admin/categories + 안내 문구
     */
    @PostMapping("/categories/{categoryId}/delete")
    public String deleteCategory(@PathVariable Long categoryId, RedirectAttributes redirectAttributes) {
        try {
            String name = hobbyService.deleteCategory(categoryId);
            redirectAttributes.addFlashAttribute("message", "'" + name + "' 대분류를 삭제했어요.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode() == ErrorCode.INVALID_INPUT
                    ? "세부 취미가 있는 대분류는 삭제할 수 없어요. 세부 취미를 다른 대분류로 옮기거나 대분류를 비공개로 바꿔 주세요."
                    : "대분류를 찾을 수 없어요.");
        }
        return "redirect:/admin/categories";
    }

    // ===================== A-S03 세부 취미 (추가·편집) =====================

    /**
     * A-S03 세부 취미 — 전체 세부 취미를 대분류와 함께 한 표로. create=true면 세부 취미 추가 패널을 펼친다
     * (categoryId가 있으면 그 대분류를 미리 골라 둔다 — 대분류 관리 화면의 [세부 취미 추가]에서 올 때).
     * edit={id}면 그 세부 취미의 편집 패널(현재 값으로 채움)을 펼친다.
     * 모델: view, categories, states, showCreateForm, editId, hobbyFormDTO
     */
    @GetMapping("/sub-hobbies")
    public String subHobbies(@RequestParam(defaultValue = "false") boolean create,
                             @RequestParam(required = false) Long categoryId,
                             @RequestParam(required = false) Long edit,
                             Model model) {
        HobbyFormDTO form;
        if (edit != null) {
            form = hobbyService.getHobbyForm(edit);
        } else {
            form = new HobbyFormDTO();
            form.setCategoryId(categoryId);
        }
        model.addAttribute("hobbyFormDTO", form);
        addHobbiesModel(model, create && edit == null, edit);
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
        checkHobbyForm(hobbyFormDTO, bindingResult, null);
        if (bindingResult.hasErrors()) {
            addHobbiesModel(model, true, null);
            return MANAGE_VIEW;
        }

        hobbyService.createHobby(hobbyFormDTO);
        redirectAttributes.addFlashAttribute("message",
                "'" + hobbyFormDTO.getTrimmedName() + "' 세부 취미를 추가했어요.");
        return "redirect:/admin/sub-hobbies";
    }

    /**
     * 세부 취미 편집 (A-S03) — 대분류·이름·소개·노출 상태. 검증 실패면 입력값을 유지한 채 편집 패널을 다시 보여 준다.
     * 성공: redirect:/admin/sub-hobbies + flash message.  실패: 없는 세부 취미 → HOBBY_NOT_FOUND(공통 오류 화면)
     */
    @PostMapping("/sub-hobbies/{hobbyId}")
    public String updateHobby(@PathVariable Long hobbyId,
                              @Valid @ModelAttribute("hobbyFormDTO") HobbyFormDTO hobbyFormDTO,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        checkHobbyForm(hobbyFormDTO, bindingResult, hobbyId);
        if (bindingResult.hasErrors()) {
            addHobbiesModel(model, false, hobbyId);
            return MANAGE_VIEW;
        }

        hobbyService.updateHobby(hobbyId, hobbyFormDTO);
        redirectAttributes.addFlashAttribute("message", "'" + hobbyFormDTO.getTrimmedName() + "' 세부 취미를 저장했어요.");
        return "redirect:/admin/sub-hobbies";
    }

    /** 세부 취미 추가·편집 공통 검증 — 없는 대분류, 같은 대분류 안 이름 중복(편집이면 자기 자신 제외) */
    private void checkHobbyForm(HobbyFormDTO form, BindingResult bindingResult, Long exceptHobbyId) {
        if (!bindingResult.hasFieldErrors("categoryId") && !hobbyService.categoryExists(form.getCategoryId())) {
            bindingResult.rejectValue("categoryId", "notFound", "대분류를 다시 선택해 주세요.");
        }
        if (bindingResult.hasFieldErrors("name") || bindingResult.hasFieldErrors("categoryId")) {
            return;
        }
        boolean available = exceptHobbyId == null
                ? hobbyService.isHobbyNameAvailable(form.getCategoryId(), form.getTrimmedName())
                : hobbyService.isHobbyNameAvailable(form.getCategoryId(), form.getTrimmedName(), exceptHobbyId);
        if (!available) {
            bindingResult.rejectValue("name", "duplicate", "이 대분류에 같은 이름의 세부 취미가 이미 있어요.");
        }
    }

    // ===================== A-S06 콘텐츠 검수 (강의 편집) =====================

    /**
     * A-S06 콘텐츠 검수 — 전체 강의 목록과 공개 상태. edit={id}면 그 강의의 편집 패널(현재 값으로 채움)을 펼친다.
     * 모델: view, courses(List&lt;CourseDTO&gt;), hobbyNames(세부 취미 id → 이름), courseStatuses(강의 id → 공개 상태),
     *       openCourseTotal·closedCourseTotal(공개·비공개 강의 수), courseStatusValues, editId, courseFormDTO
     */
    @GetMapping("/content-review")
    public String contentReview(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("courseFormDTO", edit != null ? courseService.getCourseForm(edit) : new CourseFormDTO());
        addReviewModel(model, edit);
        return MANAGE_VIEW;
    }

    /**
     * 강의 편집 (A-S06) — 강의명·소개·준비물·과제 제목·과제 안내·공개 상태. 검증 실패면 입력값을 유지한 채 편집 패널을 다시 보여 준다.
     * 성공: redirect:/admin/content-review + flash message.  실패: 없는 강의 → COURSE_NOT_FOUND(공통 오류 화면)
     */
    @PostMapping("/content-review/courses/{courseId}")
    public String updateCourse(@PathVariable Long courseId,
                               @Valid @ModelAttribute("courseFormDTO") CourseFormDTO courseFormDTO,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReviewModel(model, courseId);
            return MANAGE_VIEW;
        }

        String title = courseService.updateCourse(courseId, courseFormDTO);
        redirectAttributes.addFlashAttribute("message", "'" + title + "' 강의를 저장했어요.");
        return "redirect:/admin/content-review";
    }

    // ===================== 공통 모델 =====================

    /** A-S02 화면 공통 모델 (GET 과 검증 실패 시 다시 그릴 때 같이 쓴다) */
    private void addCategoriesModel(Model model, boolean showCreateForm, Long editId) {
        model.addAttribute("view", "categories");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        model.addAttribute("states", ExposureState.values());
        model.addAttribute("showCreateForm", showCreateForm);
        model.addAttribute("editId", editId);
    }

    /** A-S03 화면 공통 모델 (GET 과 검증 실패 시 다시 그릴 때 같이 쓴다) */
    private void addHobbiesModel(Model model, boolean showCreateForm, Long editId) {
        model.addAttribute("view", "hobbies");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        model.addAttribute("states", ExposureState.values());
        model.addAttribute("showCreateForm", showCreateForm);
        model.addAttribute("editId", editId);
    }

    /** A-S06 화면 공통 모델 (GET 과 검증 실패 시 다시 그릴 때 같이 쓴다) */
    private void addReviewModel(Model model, Long editId) {
        List<CategoryDTO> categories = courseCatalogService.getAdminCategories();
        Map<Long, String> hobbyNames = categories.stream()
                .flatMap(category -> category.getHobbies().stream())
                .collect(Collectors.toMap(HobbyDTO::getId, HobbyDTO::getName, (a, b) -> a));
        model.addAttribute("view", "review");
        model.addAttribute("courses", courseCatalogService.getAdminCourses());
        model.addAttribute("hobbyNames", hobbyNames);
        Map<Long, CourseStatus> statuses = courseService.getCourseStatuses();
        model.addAttribute("courseStatuses", statuses);
        model.addAttribute("openCourseTotal", statuses.values().stream().filter(s -> s == CourseStatus.OPEN).count());
        model.addAttribute("closedCourseTotal", statuses.values().stream().filter(s -> s == CourseStatus.CLOSED).count());
        model.addAttribute("courseStatusValues", CourseStatus.values());
        model.addAttribute("editId", editId);
    }
}
