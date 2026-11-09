package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 관리자 취미 조회(A-S15) — 비공개 포함 전체 대분류·세부 취미와 강의·차시 수를 보여 주는 조회 전용 화면
 * [담당] 팀원 2 · #08   [화면] A-S15   [기획] D-59(등록·수정은 시드 데이터로 대체)   [API] HB-03
 * [연결]
 *  - 호출받음: 브라우저(GET /admin/hobbies) — SecurityConfig가 /admin/**를 ADMIN만 허용한다
 *  - 호출함:   CourseCatalogService.getAdminCategories
 *  - 화면:     templates/admin/hobbies.html
 * [기술] 🟢 @Controller, @GetMapping
 */
@Controller
@RequestMapping("/admin/hobbies")
@RequiredArgsConstructor
public class AdminHobbyController {

    private final CourseCatalogService courseCatalogService;

    /**
     * 비공개를 포함한 전체 취미 조회. 호출: GET /admin/hobbies (API HB-03)
     * 모델: categories(List&lt;CategoryDTO&gt; — 하위 세부 취미에 공개/전체 강의 수·차시 수가 들어 있다)
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", courseCatalogService.getAdminCategories());
        return "admin/hobbies";
    }
}
