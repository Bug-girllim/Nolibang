package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 전체 취미(U-S01)·대분류별 취미(U-S01-A)·세부 취미 상세(U-S02) 화면 — 조회 전용, 비회원도 열람(D-08)
 * [담당] 팀원 2 · #08   [화면] U-S01·U-S01-A·U-S02   [기획] FR-010·013, 05 §3   [API] HB-01·HB-02
 * [연결]
 *  - 호출받음: 브라우저(GET /hobbies, /hobbies?categoryId=, /hobbies/{hobbyId}) — SecurityConfig가 /hobbies/**를 공개로 연다
 *  - 호출함:   HobbyService(노출 규칙이 적용된 목록), CourseCatalogService(수준별 강의·신청 상태가 붙은 취미 상세)
 *  - 이동:     취미 상세의 [강의 상세] → /courses/{id}, [수준별 강의 목록] → /courses?hobbyId= (CourseController)
 *  - 화면:     templates/hobby/list.html · category.html · detail.html, 사이드 메뉴는 hobby/side.html 조각
 * [기술] 🟢 @Controller, @GetMapping, @PathVariable·@RequestParam, @ModelAttribute, @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/hobbies")
@RequiredArgsConstructor
public class HobbyController {

    private final HobbyService hobbyService;
    private final CourseCatalogService courseCatalogService;

    /**
     * 모든 화면의 사이드 메뉴("취미 탐색")에 쓰는 대분류·세부 취미 목록. 이 컨트롤러의 모든 요청 앞에서 한 번 만들어 모델에 넣는다.
     * 보는 사람에게 보이는 것만 담긴다(비공개는 관리자만).
     */
    @ModelAttribute("menuCategories")
    public List<CategoryDTO> menuCategories(@AuthenticationPrincipal AuthDetails details) {
        return hobbyService.getCategories(roleOf(details));
    }

    /**
     * 전체 취미(categoryId 없음) 또는 한 대분류의 세부 취미(categoryId 있음).
     * 호출: GET /hobbies, GET /hobbies?categoryId= (API HB-01 — categoryId는 대분류별 보기 U-S01-A를 위해 #08에서 추가)
     * 모델: categories(List&lt;CategoryDTO&gt;), category(CategoryDTO, 대분류별 보기일 때)
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model) {
        List<CategoryDTO> menuCategories = menuCategoriesOf(model);  // @ModelAttribute로 이미 만든 목록을 그대로 쓴다
        model.addAttribute("categories", menuCategories);
        if (categoryId == null) {
            model.addAttribute("sideActive", "all");
            return "hobby/list";
        }
        // [성능 개선] getCategory는 목록을 다시 조회하므로, 같은 역할로 만든 menuCategories에서 고른다(결과·HOBBY_NOT_FOUND 규칙 동일)
        model.addAttribute("category", hobbyService.pickCategory(menuCategories, categoryId));
        model.addAttribute("sideActive", "category");
        model.addAttribute("sideCategoryId", categoryId);
        return "hobby/category";
    }

    /**
     * 세부 취미 상세 — 소개와 수준(입문·중급·고급)별 강의 카드. 비공개 취미를 관리자가 아닌 사람이 열면 HOBBY_NOT_FOUND.
     * 호출: GET /hobbies/{hobbyId} (API HB-02)
     * 모델: hobby(HobbyDetailDTO)
     */
    @GetMapping("/{hobbyId}")
    public String detail(@PathVariable Long hobbyId,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model) {
        HobbyDetailDTO hobby = courseCatalogService.getHobbyDetail(hobbyId, userIdOf(details), roleOf(details));
        model.addAttribute("hobby", hobby);
        model.addAttribute("sideActive", "hobby");
        model.addAttribute("sideCategoryId", hobby.getCategoryId());
        model.addAttribute("sideHobbyId", hobby.getId());
        return "hobby/detail";
    }

    /** 이 요청 앞에서 menuCategories(@ModelAttribute)가 모델에 넣어 둔 사이드 메뉴 목록 */
    @SuppressWarnings("unchecked")
    private List<CategoryDTO> menuCategoriesOf(Model model) {
        return (List<CategoryDTO>) model.getAttribute("menuCategories");
    }

    /** 비회원이면 null */
    private Role roleOf(AuthDetails details) {
        return details == null ? null : details.getRole();
    }

    /** 비회원이면 null */
    private Long userIdOf(AuthDetails details) {
        return details == null ? null : details.getUserId();
    }
}
