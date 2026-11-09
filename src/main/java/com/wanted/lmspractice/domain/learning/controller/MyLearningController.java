package com.wanted.lmspractice.domain.learning.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.MyLearningDTO;
import com.wanted.lmspractice.domain.learning.service.MyLearningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 나의 학습 현황(U-S08) 화면 — 본인의 대기·수강·평가·수료 기록을 상태 탭으로 보여 준다
 * [담당] 팀원 2 · #11   [화면] U-S08   [기획] FR-029   [API] ML-01
 * [연결]
 *  - 호출받음: 브라우저(GET /my/learning?status=) — SecurityConfig가 /my/**를 USER 역할만 허용한다
 *  - 호출함:   MyLearningService.getMyLearning
 *  - 이동:     카드 버튼 → 이어서 학습 /lessons/{id}(#12), 최종 과제 /assignments(#13), 피드백 /assignments/{id}/result(#15),
 *             수강 취소 POST /enrollments/{id}/cancel·대기 취소 POST /enrollments/waitlist/{id}/cancel(#09)
 *  - 화면:     templates/learning/my-learning.html
 * [기술] 🟢 @Controller, @GetMapping, @RequestParam, @AuthenticationPrincipal
 * 본인 것만: 조회 대상은 로그인한 사용자의 id뿐이고, 요청에서 다른 사람의 id를 받지 않는다.
 */
@Controller
@RequestMapping("/my/learning")
@RequiredArgsConstructor
public class MyLearningController {

    private final MyLearningService myLearningService;

    /**
     * 나의 학습 현황. status가 없으면 전체(ALL), 잘못된 값이면 400(타입 변환 오류 — 전역 예외 처리).
     * 호출: GET /my/learning?status=ALL|LEARNING|WAITING|REVIEWING|REJECTED|COMPLETED|ENDED (API ML-01)
     * 모델: learning(MyLearningDTO — courses·요약 숫자), selectedFilter(LearningFilter), filters(탭 목록)
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "ALL") LearningFilter status,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model) {
        MyLearningDTO learning = myLearningService.getMyLearning(details.getUserId(), status);
        model.addAttribute("learning", learning);
        model.addAttribute("selectedFilter", status);
        model.addAttribute("filters", LearningFilter.values());
        return "learning/my-learning";
    }
}
