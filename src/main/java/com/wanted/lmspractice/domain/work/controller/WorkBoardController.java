package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.work.dto.LikeResultDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDetailDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.service.CommentService;
import com.wanted.lmspractice.domain.work.service.WorkBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * [역할] 작품 게시판 목록(U-S09·T-S10), 작품 상세(U-S10), 좋아요 토글(JSON)
 * [담당] 팀원 4 · #18   [API] WB-01·WB-02·WB-03
 * [연결]
 *  - 호출받음: 브라우저 GET /works, GET /works/{workId}, POST /works/{workId}/like (모두 로그인 필요 — SecurityConfig)
 *  - 호출함:   WorkBoardService(목록·상세), CommentService.toggleLike
 *  - 화면:     work/board (학습자·관리자), work/board-instructor (강사 — 같은 주소, 권한별 표시), work/detail
 * [동작] 시안의 탭·필터·정렬·이미지 넘김·답글/수정 입력창은 모두 GET 쿼리(?kind=, ?img=, ?replyTo=, ?editComment=)로 서버가 다시 그린다.
 *        좋아요만 화면을 새로 그리지 않도록 JSON(@ResponseBody) + static/js/work-like.js 로 처리한다.
 */
@Controller
@RequestMapping("/works")
@RequiredArgsConstructor
public class WorkBoardController {

    private final WorkBoardService workBoardService;
    private final CommentService commentService;

    /** WB-01 작품 게시판 — 강사는 T-S10 템플릿, 그 밖에는 U-S09 템플릿 */
    @GetMapping
    public String board(@AuthenticationPrincipal AuthDetails details,
                        @ModelAttribute("cond") WorkSearchDTO cond,
                        Model model) {
        model.addAttribute("works", workBoardService.search(cond, details.getUserId(), details.getRole()));
        model.addAttribute("hobbies", workBoardService.getHobbyOptions(details.getRole()));
        model.addAttribute("levels", CourseLevel.values());
        if (details.getRole() == Role.INSTRUCTOR) {
            model.addAttribute("instructorName", workBoardService.getPublicName(details.getUserId()));
            model.addAttribute("grantedHobbyNames", workBoardService.getGrantedHobbyNames(details.getUserId()));
            return "work/board-instructor";
        }
        return "work/board";
    }

    /**
     * WB-02 작품 상세
     * @param img          보고 있는 이미지 번호(1부터) — 이전·다음 링크
     * @param replyTo      답글을 쓰려는 댓글 id (답글 입력창을 연다)
     * @param editComment  수정하려는 내 댓글 id (수정 입력창을 연다)
     */
    @GetMapping("/{workId}")
    public String detail(@AuthenticationPrincipal AuthDetails details,
                         @PathVariable Long workId,
                         @RequestParam(defaultValue = "1") int img,
                         @RequestParam(required = false) Long replyTo,
                         @RequestParam(required = false) Long editComment,
                         Model model) {
        WorkDetailDTO detail = workBoardService.getDetail(workId, details.getUserId(), details.getRole());
        int imageCount = detail.getWork().getImageIds().size();
        int imageNo = Math.min(Math.max(img, 1), Math.max(imageCount, 1));   // 범위를 벗어나면 가장 가까운 장

        model.addAttribute("work", detail.getWork());
        model.addAttribute("comments", detail.getComments());
        model.addAttribute("canComment", detail.isCanComment());
        model.addAttribute("canReply", detail.isCanReply());
        model.addAttribute("isOwner", detail.isOwner());
        model.addAttribute("imageNo", imageNo);
        model.addAttribute("imageCount", imageCount);
        model.addAttribute("currentImageId", imageCount == 0 ? null : detail.getWork().getImageIds().get(imageNo - 1));
        model.addAttribute("replyTo", replyTo);
        model.addAttribute("editComment", editComment);
        return "work/detail";
    }

    /** WB-03 좋아요 토글 — { "liked": true, "likeCount": 12 } (실패 시 GlobalExceptionHandler 가 JSON 오류로 응답) */
    @PostMapping("/{workId}/like")
    @ResponseBody
    public LikeResultDTO like(@AuthenticationPrincipal AuthDetails details, @PathVariable Long workId) {
        return commentService.toggleLike(details.getUserId(), details.getRole(), workId);
    }
}
