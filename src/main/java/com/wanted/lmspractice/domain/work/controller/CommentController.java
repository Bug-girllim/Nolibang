package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.work.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 댓글·답글 작성, 본인 댓글 수정·삭제 요청을 받아 처리 후 작품 상세로 되돌려 보낸다 (PRG)
 * [담당] 팀원 4 · #18   [API] CM-C1·CM-C2·CM-C3
 * [연결]
 *  - 호출받음: templates/work/detail.html 의 댓글·답글·수정·삭제 폼 (POST, 로그인 필요)
 *  - 호출함:   CommentService.writeComment / editComment / deleteComment
 * [성공] 작품 상세로 돌아가 flash commentMessage(댓글 칸 위에 표시)로 결과를 알린다(작성·수정·삭제) — work/detail.html 이 표시한다.
 * [실패] 자격 없음·입력 오류는 예외로 던져 GlobalExceptionHandler 가 오류 화면으로 보낸다(컨트롤러에 try-catch 없음).
 * [기술] 🟢 RedirectAttributes(flash)
 */
@Controller
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** CM-C1 댓글·답글 작성 — parentId 가 있으면 답글(1단계) */
    @PostMapping
    public String write(@AuthenticationPrincipal AuthDetails details,
                        @RequestParam Long workId,
                        @RequestParam(required = false) Long parentId,
                        @RequestParam String content,
                        RedirectAttributes redirectAttributes) {
        Long commentId = commentService.writeComment(details.getUserId(), details.getRole(), workId, parentId, content);
        redirectAttributes.addFlashAttribute("commentMessage", parentId == null ? "댓글을 남겼어요." : "답글을 남겼어요.");
        return "redirect:/works/" + workId + "#comment-" + commentId;
    }

    /** CM-C2 댓글 수정 (본인만) */
    @PostMapping("/{commentId}/edit")
    public String edit(@AuthenticationPrincipal AuthDetails details,
                       @PathVariable Long commentId,
                       @RequestParam String content,
                       RedirectAttributes redirectAttributes) {
        Long workId = commentService.editComment(details.getUserId(), commentId, content);
        redirectAttributes.addFlashAttribute("commentMessage", "댓글을 수정했어요.");
        return "redirect:/works/" + workId + "#comment-" + commentId;
    }

    /** CM-C3 댓글 삭제 (본인만, 소프트 삭제 — 답글은 유지) */
    @PostMapping("/{commentId}/delete")
    public String delete(@AuthenticationPrincipal AuthDetails details, @PathVariable Long commentId,
                         RedirectAttributes redirectAttributes) {
        Long workId = commentService.deleteComment(details.getUserId(), commentId);
        redirectAttributes.addFlashAttribute("commentMessage", "댓글을 삭제했어요.");
        return "redirect:/works/" + workId + "#comments";
    }
}
