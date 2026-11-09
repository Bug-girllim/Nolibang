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
 * 학습자 1:1 문의(U-S18) [P1]
 * 담당: 팀원 4 · #21   [API] IQ-01~04
 * [연결] 호출함: InquiryService / 화면: inquiry/list (목록과 스레드를 한 화면에 보여 주는 시안이라 IQ-03도 같은 템플릿을 쓴다)
 * [❓] API 명세는 IQ-03 뷰를 inquiry/thread로 적었으나 시안(U-S18)이 목록+스레드 한 화면이고 변환 템플릿도 list.html 하나뿐이라 새로 만들지 않았다.
 * 모델: threads(InquiryDTO 목록), thread(선택한 항목), messages(InquiryMessageDTO 목록)
 * [기술] 🟢 @Controller, 🟢 @Valid + BindingResult, 🟢 redirect: + flash
 */
@Controller
@RequestMapping("/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    /** 내 문의 목록 (IQ-01). enrollmentId는 아직 스레드가 없는 수강을 골랐을 때 */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails auth,
                       @RequestParam(required = false) Long enrollmentId,
                       Model model) {
        return show(model, inquiryService.getLearnerPage(auth.getUserId(), null, enrollmentId));
    }

    /** 문의 스레드 보기 (IQ-03) */
    @GetMapping("/{threadId}")
    public String thread(@AuthenticationPrincipal AuthDetails auth, @PathVariable Long threadId, Model model) {
        return show(model, inquiryService.getLearnerPage(auth.getUserId(), threadId, null));
    }

    /** 문의 작성 — 스레드가 없으면 만들고 있으면 이어 쓴다 (IQ-02) */
    @PostMapping
    public String write(@AuthenticationPrincipal AuthDetails auth,
                        @Valid @ModelAttribute("form") InquiryWriteDTO form,
                        BindingResult bindingResult,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/inquiries" + (form.getEnrollmentId() == null ? "" : "?enrollmentId=" + form.getEnrollmentId());
        }
        Long threadId = inquiryService.writeMessage(auth.getUserId(), form.getEnrollmentId(), form.getTitle(), form.getContent());
        redirectAttributes.addFlashAttribute("message", "문의를 보냈어요.");
        return "redirect:/inquiries/" + threadId;
    }

    /** 스레드에 메시지 추가 (IQ-04) */
    @PostMapping("/{threadId}/messages")
    public String addMessage(@AuthenticationPrincipal AuthDetails auth,
                             @PathVariable Long threadId,
                             @Valid @ModelAttribute("form") InquiryWriteDTO form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/inquiries/" + threadId;
        }
        inquiryService.addMessage(auth.getUserId(), threadId, form.getContent());
        redirectAttributes.addFlashAttribute("message", "메시지를 보냈어요.");
        return "redirect:/inquiries/" + threadId;
    }

    private String show(Model model, InquiryPageDTO page) {
        model.addAttribute("threads", page.getThreads());
        model.addAttribute("thread", page.getSelected());
        model.addAttribute("messages", page.getMessages());
        model.addAttribute("maxContentLength", InquiryService.MAX_CONTENT_LENGTH);
        return "inquiry/list";
    }
}
