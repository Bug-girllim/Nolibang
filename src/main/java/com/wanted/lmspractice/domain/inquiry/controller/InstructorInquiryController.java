package com.wanted.lmspractice.domain.inquiry.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.inquiry.dto.InquiryPageDTO;
import com.wanted.lmspractice.domain.inquiry.dto.InquiryWriteDTO;
import com.wanted.lmspractice.domain.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 강사 문의 답변(T-S06) [P1] — 1:1 문의 탭(공개 작품 댓글 탭은 #18 게시판 /works로 연결)
 * 담당: 팀원 4 · #21   [API] II-01~03
 * [연결] 호출함: InquiryService / 화면: instructor/inquiries (목록+스레드 한 화면, II-02도 같은 템플릿)
 * [❓] API 명세는 II-02 뷰를 instructor/inquiry-thread로 적었으나 시안(T-S06)이 목록+스레드 한 화면이라 inquiries.html 하나를 쓴다.
 * 모델: threads, thread(선택한 스레드), messages, waitingCount(답변 대기 수), unansweredOnly
 * 강사는 학습자 문의에 답변만 한다(일방 발송 없음, 답변 입력은 스레드를 고른 상태에서만).
 */
@Controller
@RequestMapping("/instructor/inquiries")
@RequiredArgsConstructor
public class InstructorInquiryController {

    private final InquiryService inquiryService;

    /** 강사 문의 목록 (II-01) — answered=false면 답변 대기만 */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails auth,
                       @RequestParam(required = false) Boolean answered,
                       Model model) {
        return show(model, inquiryService.getInstructorPage(auth.getUserId(), null, Boolean.FALSE.equals(answered)),
                Boolean.FALSE.equals(answered));
    }

    /** 강사 문의 스레드 (II-02) */
    @GetMapping("/{threadId}")
    public String thread(@AuthenticationPrincipal AuthDetails auth,
                         @PathVariable Long threadId,
                         @RequestParam(required = false) Boolean answered,
                         Model model) {
        return show(model, inquiryService.getInstructorPage(auth.getUserId(), threadId, Boolean.FALSE.equals(answered)),
                Boolean.FALSE.equals(answered));
    }

    /** 답변 작성 (II-03) */
    @PostMapping("/{threadId}/answer")
    public String answer(@AuthenticationPrincipal AuthDetails auth,
                         @PathVariable Long threadId,
                         @Valid @ModelAttribute("form") InquiryWriteDTO form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/instructor/inquiries/" + threadId;
        }
        inquiryService.answer(auth.getUserId(), threadId, form.getContent());
        redirectAttributes.addFlashAttribute("message", "답변을 등록했어요.");
        return "redirect:/instructor/inquiries/" + threadId;
    }

    private String show(Model model, InquiryPageDTO page, boolean unansweredOnly) {
        model.addAttribute("threads", page.getThreads());
        model.addAttribute("thread", page.getSelected());
        model.addAttribute("messages", page.getMessages());
        model.addAttribute("waitingCount", page.getWaitingCount());
        model.addAttribute("unansweredOnly", unansweredOnly);
        model.addAttribute("maxContentLength", InquiryService.MAX_CONTENT_LENGTH);
        return "instructor/inquiries";
    }
}
