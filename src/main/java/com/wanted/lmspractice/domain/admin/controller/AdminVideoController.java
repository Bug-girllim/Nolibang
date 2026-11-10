package com.wanted.lmspractice.domain.admin.controller;

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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] 관리자 영상 업로드 — MP4 올리기(uploads/videos/uploaded/), 목록·미리보기, 차시 연결, 삭제. 관리자는 모든 영상·모든 차시를 다룬다.
 * [연결]
 *  - 호출받음: 브라우저(/admin/videos …) — SecurityConfig가 /admin/**를 ADMIN만 허용한다(설정 변경 없음)
 *  - 호출함:   LessonVideoService(instructorId = null → 관리자 범위)
 *  - 화면:     templates/video/manage.html (강사 화면과 공용), 관리자 사이드 메뉴 "영상 업로드"(A-VID)
 *  - 같은 기능: InstructorVideoController(/instructor/videos — 자기 영상·권한 취미만)
 * 영상은 key(= lessons.video_key, 예: videos/uploaded/x.mp4)로 가리킨다 — 경로에 '/'가 들어가서 주소 경로가 아니라 key 파라미터로 받는다.
 * [기술] 🟢 @Controller, MultipartFile, RedirectAttributes(flash), ResponseEntity<Resource>(Range 요청 자동 처리 → 미리보기 구간 이동)
 */
@Controller
@RequestMapping("/admin/videos")
@RequiredArgsConstructor
public class AdminVideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");
    private static final String BASE = "/admin/videos";

    private final LessonVideoService lessonVideoService;

    /** 업로드 화면. 모델: videos, lessons, linked(key → 차시 이름들), admin, basePath, maxSizeMb */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("admin", true);
        model.addAttribute("basePath", BASE);
        model.addAttribute("videos", lessonVideoService.getVideos(null));
        model.addAttribute("lessons", lessonVideoService.getLessonOptions(null));
        model.addAttribute("linked", lessonVideoService.getLinkedLessonLabels());
        model.addAttribute("maxSizeMb", VideoStorage.MAX_VIDEO_BYTES / 1024 / 1024);
        return "video/manage";
    }

    /** MP4 업로드. 성공·실패 모두 목록으로 돌아가 message / errorMessage 로 안내한다 */
    @PostMapping
    public String upload(@RequestParam(value = "file", required = false) MultipartFile file,
                         RedirectAttributes redirectAttributes) {
        try {
            UploadedVideo saved = lessonVideoService.upload(file, null);
            redirectAttributes.addFlashAttribute("message",
                    saved.getFileName() + " 을(를) 올렸어요. (" + saved.getSizeLabel() + " · " + saved.getDurationLabel() + ")");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }

    /** 미리보기 재생 — Range 요청에 206으로 응답해 구간 이동이 된다. 없는 파일·규칙 밖 경로는 404 */
    @GetMapping("/file")
    public ResponseEntity<Resource> preview(@RequestParam String key) {
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new FileSystemResource(lessonVideoService.loadForPreview(key, null)));
    }

    /** 차시 연결 — lessons.video_key·duration_sec 변경 */
    @PostMapping("/link")
    public String link(@RequestParam String key, @RequestParam(required = false) Long lessonId,
                       RedirectAttributes redirectAttributes) {
        if (lessonId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "연결할 차시를 골라 주세요.");
            return "redirect:" + BASE;
        }
        try {
            String lessonLabel = lessonVideoService.linkVideo(key, lessonId, null);
            redirectAttributes.addFlashAttribute("message", lessonLabel + "에 영상을 연결했어요.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }

    /** 삭제 — 차시에 연결된 영상은 지우지 않는다 */
    @PostMapping("/delete")
    public String delete(@RequestParam String key, RedirectAttributes redirectAttributes) {
        try {
            lessonVideoService.delete(key, null);
            redirectAttributes.addFlashAttribute("message", key.substring(key.lastIndexOf('/') + 1) + " 을(를) 삭제했어요.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        }
        return "redirect:" + BASE;
    }
}
