package com.wanted.lmspractice.global.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 메인 홈(C-01), 개인정보 처리방침(C-10), 권한 오류 화면 진입점을 화면에 연결한다.
 * [담당] 팀원 1 · #02   [화면] C-01, C-10, E-403   [API] CM-01
 * [연결]
 *  - 호출받음: 브라우저(GET /, GET /privacy), SecurityConfig의 accessDeniedPage("/error/403")
 *  - 호출함:   없음 (#02에서는 모델 없이 화면만 반환)
 *  - 확장:     홈의 hobbies·continueCourses 모델은 #08·#11 완료 후 연결 (❓ API CM-01: 홈에 보여줄 항목 미확정)
 * [기술] 🟢 @Controller, @GetMapping
 */
@Controller
public class MainController {

    /** C-01 메인 홈. 비로그인도 접근 가능 (API CM-01) */
    @GetMapping("/")
    public String index() {
        return "main/home";  // 디자인 변환본 사용. (이전: main/index 임시 화면 — API 명세는 main/home, 이슈 #02 본문은 main/index)
    }

    /** C-10 개인정보 처리방침. ❓ URL이 API 명세에 없어 /privacy로 임시 지정 */
    @GetMapping("/privacy")
    public String privacy() {
        return "main/privacy";
    }

    /**
     * 권한 없음 화면 (GET·POST 등 모든 방식). Security 필터에서 난 AccessDeniedException은 컨트롤러 밖이라
     * GlobalExceptionHandler가 못 잡으므로 accessDeniedPage가 이 URL로 보낸다.
     */
    @RequestMapping("/error/403")   // 모든 HTTP 방식 — accessDeniedPage 는 원래 요청 방식(POST 등) 그대로 forward 하므로 GET 만 받으면 405 가 된다 (이슈 #47)
    public String forbidden(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("message", "접근 권한이 없습니다.");
        return "error/403";
    }
}
