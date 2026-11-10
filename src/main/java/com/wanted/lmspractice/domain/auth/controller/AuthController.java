package com.wanted.lmspractice.domain.auth.controller;

import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 로그인 화면(C-02)을 보여 준다. 로그인·로그아웃 POST는 Spring Security가 처리한다.
 * [담당] 팀원 1 · #02 (#04: 실패 원인별 문구 — 원인 판단은 AuthFailHandler)   [화면] C-02   [API] AU-01
 * [연결]
 *  - 호출받음: 브라우저(GET /auth/login), SecurityConfig(로그인 필요 시 이동), AuthFailHandler(실패 시 ?error[=원인] 이동)
 *  - 호출함:   없음
 * [기술] 🟢 @Controller, @RequestParam
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    /**
     * 로그인 화면. error·expired·logout 쿼리는 값 없이 존재 여부로 판단하고, error만 값으로 원인을 나눈다.
     * 호출: GET /auth/login (API AU-01), 실패 이동은 AuthFailHandler
     * 모델: errorMessage·message, unverified(true면 인증 메일 재발송 링크를 보여 준다)
     */
    @GetMapping("/login")
    public String loginForm(@RequestParam Map<String, String> params, Model model) {
        // ?error 처럼 값 없는 파라미터는 값이 아니라 "키가 있는지"로 판단한다
        if (params.containsKey("error")) {
            // #04: 원인별 안내. 값이 없는 ?error(아이디·비밀번호 불일치)는 계정 존재 여부를 드러내지 않는 한 문구만 쓴다
            switch (params.get("error")) {
                case "unverified" -> {
                    model.addAttribute("errorMessage", ErrorCode.EMAIL_NOT_VERIFIED.getMessage());
                    model.addAttribute("unverified", true);
                }
                case "disabled" -> model.addAttribute("errorMessage", "비활성화된 계정입니다.");
                default -> model.addAttribute("errorMessage", "아이디 또는 비밀번호가 올바르지 않습니다.");
            }
        } else if (params.containsKey("expired")) {
            model.addAttribute("errorMessage", "세션이 만료되었거나 다른 기기에서 로그인되었습니다. 다시 로그인해 주세요.");
        } else if (params.containsKey("withdrawn")) {
            // 탈퇴하면 세션이 끝나 flash 속성을 쓸 수 없어서 쿼리 값으로 알린다
            model.addAttribute("message", "회원 탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.");
        } else if (params.containsKey("logout")) {
            model.addAttribute("message", "로그아웃되었습니다.");
        }
        return "auth/login";
    }
}
