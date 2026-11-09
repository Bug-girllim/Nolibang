package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.work.dto.WorkFormDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFormOptionsDTO;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.service.GrowthService;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 일반 작품 등록·수정(U-S11), 공개 설정, 한 줄 회고, 개인 성장 타임라인(U-S12) 요청을 받아 서비스에 넘긴다(PRG).
 * [담당] 팀원 4 · #19 (MW-08 숨김 사유 U-S16은 #20에서 이 컨트롤러에 추가)
 *       [화면] U-S11 — templates/work/form.html, U-S12 — templates/work/growth.html   [기획] FR-027·028   [API] MW-01~MW-07
 * [연결]
 *  - 호출받음: 브라우저 (SecurityConfig: /my/** = 학습자(USER)만), 진입 링크: 사이드 메뉴 "성장 기록", 타임라인의 [일반 작품 등록]·[수정]
 *  - 호출함:   WorkService(getFormOptions·saveGeneralWork·getGeneralWorkForm·updateGeneralWork·changeVisibility),
 *             GrowthService(getTimeline·saveReflection), GlobalExceptionHandler(404·403·500 공통 오류 화면)
 *  - 모델:     (form) workFormDTO, hobbies, allowedLevels, reference — API 명세 이름 그대로 / workId(수정이면 작품 id, 등록이면 null — 명세에 없어 추가)
 *             (growth) timeline(TimelineDTO)
 *  - 이동:     MW-02·05·06 → redirect:/my/works/growth, MW-04 → redirect:/works/{workId} (+ flash message)
 *             업무 오류 중 400(제목·이미지·취미·수준) → 입력하던 폼으로 redirect + flash errorMessage
 * [기술] 🟢 @Controller·@GetMapping·@PostMapping, 🟢 @Valid + BindingResult(같은 화면 다시 그림), 🟢 List&lt;MultipartFile&gt;,
 *        🟢 RedirectAttributes(flash), 🟢 @ExceptionHandler(이 컨트롤러 전용 — AssignmentController와 같은 방식), 🟢 @AuthenticationPrincipal
 */
@Controller
@RequestMapping("/my/works")
@RequiredArgsConstructor
public class MyWorkController {

    private static final String VIEW_FORM = "work/form";
    private static final String VIEW_GROWTH = "work/growth";
    private static final String REDIRECT_GROWTH = "redirect:/my/works/growth";
    private static final String MSG_CREATED = "일반 작품을 저장했어요. 수료 상태에는 영향이 없어요.";
    private static final String MSG_EDITED = "작품을 수정했어요.";
    private static final String MSG_VISIBILITY = "공개 설정을 바꿨어요.";
    private static final String MSG_REFLECTION = "회고를 저장했어요.";

    private final WorkService workService;
    private final GrowthService growthService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /**
     * 일반 작품 등록 화면 (API MW-01). 취미를 고르면 work-form.js 가 ?hobbyId=&title= 로 다시 불러
     * 그 취미의 수준 선택지·참고 피드백을 갱신한다(쿼리 값은 workFormDTO 에 그대로 바인딩된다).
     */
    @GetMapping("/new")
    public String createForm(@ModelAttribute("workFormDTO") WorkFormDTO form,
                             @AuthenticationPrincipal AuthDetails details,
                             Model model) {
        addFormModel(model, details.getUserId(), form, null);
        return VIEW_FORM;
    }

    /**
     * 일반 작품 등록 (API MW-02, multipart/form-data) — 성공하면 성장 타임라인으로.
     * 형식 오류(@Valid)는 같은 화면을 다시 그린다(고른 사진은 브라우저 보안상 다시 골라야 한다).
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("workFormDTO") WorkFormDTO form,
                         BindingResult bindingResult,
                         @RequestParam(value = "images", required = false) List<MultipartFile> images,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormModel(model, details.getUserId(), form, null);
            return VIEW_FORM;
        }
        workService.saveGeneralWork(details.getUserId(), form, images);
        redirectAttributes.addFlashAttribute("message", MSG_CREATED);
        return REDIRECT_GROWTH;
    }

    /**
     * 일반 작품 수정 화면 (API MW-03) — 기존 값으로 채운다. 취미를 바꿔 다시 불러온 경우(hobbyId·title 쿼리)는 그 값을 덮어쓴다.
     * 본인 일반 작품이 아니면 WORK_NOT_FOUND(404, 최종 과제는 AS-03에서 수정).
     */
    @GetMapping("/{workId}/edit")
    public String editForm(@PathVariable Long workId,
                           @RequestParam(required = false) Long hobbyId,
                           @RequestParam(required = false) String title,
                           @AuthenticationPrincipal AuthDetails details,
                           Model model) {
        WorkFormDTO form = workService.getGeneralWorkForm(details.getUserId(), workId);
        if (hobbyId != null) {
            form.setHobbyId(hobbyId);
        }
        if (title != null) {
            form.setTitle(title);
        }
        addFormModel(model, details.getUserId(), form, workId);
        return VIEW_FORM;
    }

    /** 일반 작품 수정 (API MW-04, multipart/form-data) — 사진을 고르지 않으면 기존 사진 유지. 성공하면 작품 상세(#18)로 */
    @PostMapping("/{workId}/edit")
    public String edit(@PathVariable Long workId,
                       @Valid @ModelAttribute("workFormDTO") WorkFormDTO form,
                       BindingResult bindingResult,
                       @RequestParam(value = "images", required = false) List<MultipartFile> images,
                       @AuthenticationPrincipal AuthDetails details,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormModel(model, details.getUserId(), form, workId);
            return VIEW_FORM;
        }
        workService.updateGeneralWork(details.getUserId(), workId, form, images);
        redirectAttributes.addFlashAttribute("message", MSG_EDITED);
        return "redirect:/works/" + workId;
    }

    /**
     * 공개 설정 변경 (API MW-05) — 일반 작품·최종 과제 모두 본인만.
     * 💡 명세의 returnUrl 은 받지 않고 성장 타임라인으로 돌아간다(이 버튼이 타임라인에만 있음 — 외부 주소로 보내는 위험도 없앰).
     */
    @PostMapping("/{workId}/visibility")
    public String changeVisibility(@PathVariable Long workId,
                                   @RequestParam Visibility visibility,
                                   @AuthenticationPrincipal AuthDetails details,
                                   RedirectAttributes redirectAttributes) {
        workService.changeVisibility(details.getUserId(), workId, visibility);
        redirectAttributes.addFlashAttribute("message", MSG_VISIBILITY);
        return REDIRECT_GROWTH;
    }

    /** 한 줄 회고 저장 (API MW-06) — 빈 값이면 삭제. 선택 입력이며 수료와 무관 */
    @PostMapping("/{workId}/reflection")
    public String saveReflection(@PathVariable Long workId,
                                 @RequestParam(required = false) String reflection,
                                 @AuthenticationPrincipal AuthDetails details,
                                 RedirectAttributes redirectAttributes) {
        growthService.saveReflection(details.getUserId(), workId, reflection);
        redirectAttributes.addFlashAttribute("message", MSG_REFLECTION);
        return REDIRECT_GROWTH;
    }

    /** 개인 성장 타임라인 (API MW-07) — hobbyId 가 없으면 전체 보기 */
    @GetMapping("/growth")
    public String growth(@RequestParam(required = false) Long hobbyId,
                         @AuthenticationPrincipal AuthDetails details,
                         Model model) {
        model.addAttribute("timeline", growthService.getTimeline(details.getUserId(), hobbyId));
        return VIEW_GROWTH;
    }

    /**
     * 이 컨트롤러의 업무 오류 처리 — POST 의 400(제목·이미지·취미·수준·회고 길이)은 오류 화면 대신 원래 화면으로 돌려보내 안내한다.
     * 403(ACCESS_DENIED — 남의 참고 피드백 연결)·404(WORK_NOT_FOUND 등)·500 은 공통 오류 화면(GlobalExceptionHandler).
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        boolean flashable = "POST".equals(request.getMethod())
                && e.getErrorCode().getStatus() == HttpStatus.BAD_REQUEST;
        if (!flashable) {
            return globalExceptionHandler.handleBusiness(e, request);
        }
        redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        return "redirect:" + backPath(request);
    }

    // 오류 때 돌아갈 화면: 수정 → 같은 수정 화면, 등록 → 등록 화면(고른 취미 유지), 그 밖(공개 설정·회고) → 타임라인
    private String backPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.endsWith("/edit")) {
            return path;
        }
        if ("/my/works".equals(path)) {
            String hobbyId = request.getParameter("hobbyId");
            return hobbyId != null && hobbyId.matches("\\d+") ? "/my/works/new?hobbyId=" + hobbyId : "/my/works/new";
        }
        return "/my/works/growth";
    }

    // 등록·수정 화면 공통 모델 (API MW-01 모델 이름 그대로)
    private void addFormModel(Model model, Long userId, WorkFormDTO form, Long workId) {
        WorkFormOptionsDTO options = workService.getFormOptions(userId, form.getHobbyId());
        model.addAttribute("workFormDTO", form);
        model.addAttribute("hobbies", options.getHobbies());
        model.addAttribute("allowedLevels", options.getAllowedLevels());
        model.addAttribute("reference", options.getReference());
        model.addAttribute("workId", workId);
    }
}
