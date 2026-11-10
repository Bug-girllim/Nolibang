package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 관리자 취미 조회 화면들 — 모두 조회 전용(D-59: 등록·수정은 시드 데이터로 대체)
 *        A-S15 취미 탐색 · A-S02 대분류 관리 · A-S03 세부 취미 · A-S06 콘텐츠 검수
 * [담당] 팀원 2 · #08   [화면] A-S15, A-S02, A-S03, A-S06   [API] HB-03
 * [연결]
 *  - 호출받음: 브라우저 GET /admin/hobbies, /admin/categories, /admin/sub-hobbies, /admin/content-review
 *             — SecurityConfig가 /admin/**를 ADMIN만 허용한다. 메뉴는 common/layout.html의 adminMenu 조각
 *  - 호출함:   CourseCatalogService.getAdminCategories(대분류·세부 취미·강의/차시 수), getAdminCourses(전체 강의)
 *  - 화면:     templates/admin/hobbies.html(A-S15), templates/admin/hobby-manage.html(A-S02·A-S03·A-S06 — view 값으로 구분)
 * [기술] 🟢 @Controller, @GetMapping
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHobbyController {

    private static final String MANAGE_VIEW = "admin/hobby-manage";

    private final CourseCatalogService courseCatalogService;

    /**
     * 비공개를 포함한 전체 취미 조회. 호출: GET /admin/hobbies (API HB-03)
     * 모델: categories(List&lt;CategoryDTO&gt; — 하위 세부 취미에 공개/전체 강의 수·차시 수가 들어 있다)
     */
    @GetMapping("/hobbies")
    public String list(Model model) {
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        return "admin/hobbies";
    }

    /** A-S02 대분류 관리 — 대분류별 상태·세부 취미 수·공개 강의 수. 모델: view, categories */
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("view", "categories");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        return MANAGE_VIEW;
    }

    /** A-S03 세부 취미 — 전체 세부 취미를 대분류와 함께 한 표로. 모델: view, categories */
    @GetMapping("/sub-hobbies")
    public String subHobbies(Model model) {
        model.addAttribute("view", "hobbies");
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        return MANAGE_VIEW;
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
}
