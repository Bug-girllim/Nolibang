package com.wanted.lmspractice.domain.instructor.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.instructor.service.InstructorHobbyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 강사 "내 담당 취미"(T-S08) 화면 연결 — 관리자가 지정한 담당 세부 취미 목록을 보여 준다. (조회 전용)
 * [담당] #T-S08   [화면] T-S08
 * [연결]
 *  - 호출받음: 브라우저 GET /instructor/hobbies (INSTRUCTOR만 — SecurityConfig의 /instructor/** 규칙)
 *  - 호출함:   InstructorHobbyService.getMyHobbyPage
 *  - 뷰:       templates/instructor/hobbies.html
 * 강의 등록·편집은 11-06 팀 결정으로 범위 밖이라 이 화면에는 없다.
 * [기술] 🟢 @Controller, @GetMapping, @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/instructor/hobbies")
@RequiredArgsConstructor
public class InstructorHobbyController {

    private final InstructorHobbyService instructorHobbyService;

    /** 사이드 "취미 탐색" 메뉴(hobby/side)가 쓰는 목록 — HobbyController와 같은 이름·같은 방식으로 모든 요청에 넣는다 */
    @ModelAttribute("menuCategories")
    public List<CategoryDTO> menuCategories() {
        return instructorHobbyService.getMenuCategories();
    }

    /** 모델: page(강사 이름, 담당 취미 카드 목록, 강의 수·준비 중 수 집계), menuCategories(사이드 메뉴) */
    @GetMapping
    public String hobbies(@AuthenticationPrincipal AuthDetails details, Model model) {
        model.addAttribute("page", instructorHobbyService.getMyHobbyPage(details.getUserId()));
        return "instructor/hobbies";
    }
}
