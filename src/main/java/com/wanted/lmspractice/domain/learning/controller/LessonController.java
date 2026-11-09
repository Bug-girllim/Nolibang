package com.wanted.lmspractice.domain.learning.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.dto.ProgressReportDTO;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * [역할] 차시 영상 학습 화면(U-S06)과 재생 시간 보고(@ResponseBody) 연결
 * [담당] 팀원 3 · #12   [화면] U-S06   [기획] FR-020   [API] LE-01, LE-02
 * [연결]
 *  - 호출받음: 브라우저 GET /lessons/{lessonId}?enrollmentId=, static/js/lesson-progress.js의 POST /lessons/{lessonId}/progress
 *             (SecurityConfig: USER만)
 *  - 호출함:   ProgressService.getLessonView / report (본인 확인·기간 확인은 Service가 한다)
 *  - 뷰:       templates/learning/lesson.html
 * [기술] 🟢 @Controller, 🟢 @ResponseBody(작은 데이터 응답 — 재생 보고), 🟢 @Valid, 🟢 @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final ProgressService progressService;

    /** 모델: lesson(LessonViewDTO) — 지금 차시, 차시 목록·진도, 수강 기간 */
    @GetMapping("/{lessonId}")
    public String lesson(@AuthenticationPrincipal AuthDetails details,
                         @PathVariable Long lessonId,
                         @RequestParam Long enrollmentId,
                         Model model) {
        model.addAttribute("lesson", progressService.getLessonView(details.getUserId(), enrollmentId, lessonId));
        return "learning/lesson";
    }

    /**
     * 재생 시간 보고. 응답: 누적 비율·완료 여부(JSON).
     * 입력이 잘못되면 BindException → GlobalExceptionHandler가 400 JSON({code,message})으로 바꾼다(Accept: application/json).
     */
    @PostMapping("/{lessonId}/progress")
    @ResponseBody
    public LessonProgressDTO report(@AuthenticationPrincipal AuthDetails details,
                                    @PathVariable Long lessonId,
                                    @Valid ProgressReportDTO report) {
        return progressService.report(details.getUserId(), lessonId, report);
    }
}
