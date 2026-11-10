package com.wanted.lmspractice.domain.instructor.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.course.service.LessonVideoService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.file.UploadedVideo;
import com.wanted.lmspractice.global.file.VideoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 강사 영상 업로드 — 자기 영상만 올리고(uploads/videos/uploaded/instructor-{id}/) 보고 지우며,
 *        관리자가 활동 권한을 준 취미의 차시에만 연결한다.
 * [연결]
 *  - 호출받음: 브라우저(/instructor/videos …) — SecurityConfig가 /instructor/**를 INSTRUCTOR만 허용한다(설정 변경 없음)
 *  - 호출함:   LessonVideoService(instructorId = 로그인 강사 → 강사 범위, 권한은 InstructorAccountService.hasHobbyGrant)
 *  - 화면:     templates/video/manage.html (관리자 화면과 공용), 강사 LMS 사이드 메뉴 "영상 업로드"(T-VID)
 *  - 같은 기능: AdminVideoController(/admin/videos — 모든 영상·모든 차시)
 * [기술] 🟢 @Controller, @AuthenticationPrincipal, MultipartFile, RedirectAttributes(flash), ResponseEntity<Resource>(Range)
 */
@Controller
@RequestMapping("/instructor/videos")
@RequiredArgsConstructor
public class InstructorVideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");
    private static final String BASE = "/instructor/videos";

    private final LessonVideoService lessonVideoService;

    /** 업로드 화면. 모델: videos(내 영상), lessons(권한 취미의 차시), linked, admin=false, basePath, maxSizeMb */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthDetails details, Model model) {
        Long me = details.getUserId();
        model.addAttribute("admin", false);
        model.addAttribute("basePath", BASE);
        model.addAttribute("videos", lessonVideoService.getVideos(me));
        model.addAttribute("lessons", lessonVideoService.getLessonOptions(me));
        model.addAttribute("linked", lessonVideoService.getLinkedLessonLabels());
        model.addAttribute("maxSizeMb", VideoStorage.MAX_VIDEO_BYTES / 1024 / 1024);
        return "video/manage";
    }

    @PostMapping
    public String upload(@AuthenticationPrincipal AuthDetails details,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         RedirectAttributes redirectAttributes) {
        try {
            UploadedVideo saved = lessonVideoService.upload(file, details.getUserId());
            redirectAttributes.addFlashAttribute("message",
                    saved.getFileName() + " 을(를) 올렸어요. (" + saved.getSizeLabel() + " · " + saved.getDurationLabel() + ")");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }

    /** 미리보기 — 자기 영상만(남의 영상은 404) */
    @GetMapping("/file")
    public ResponseEntity<Resource> preview(@AuthenticationPrincipal AuthDetails details, @RequestParam String key) {
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new FileSystemResource(lessonVideoService.loadForPreview(key, details.getUserId())));
    }

    /** 차시 연결 — 권한 받은 취미의 차시만 */
    @PostMapping("/link")
    public String link(@AuthenticationPrincipal AuthDetails details, @RequestParam String key,
                       @RequestParam(required = false) Long lessonId, RedirectAttributes redirectAttributes) {
        if (lessonId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "연결할 차시를 골라 주세요.");
            return "redirect:" + BASE;
        }
        try {
            String lessonLabel = lessonVideoService.linkVideo(key, lessonId, details.getUserId());
            redirectAttributes.addFlashAttribute("message", lessonLabel + "에 영상을 연결했어요.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }

    /** 삭제 — 자기 영상만, 차시에 연결된 영상은 지우지 않는다 */
    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal AuthDetails details, @RequestParam String key,
                         RedirectAttributes redirectAttributes) {
        try {
            lessonVideoService.delete(key, details.getUserId());
            redirectAttributes.addFlashAttribute("message", key.substring(key.lastIndexOf('/') + 1) + " 을(를) 삭제했어요.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }
}
