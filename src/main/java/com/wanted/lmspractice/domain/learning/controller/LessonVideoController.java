package com.wanted.lmspractice.domain.learning.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * [역할] 영상 파일 출력 — 수강 30일 이내 또는 수료자만 (D-42). 권한을 확인한 뒤에만 파일을 내보낸다.
 * [담당] 팀원 3 · #12   [기획] D-42   [API] LE-03
 * [연결]
 *  - 호출받음: lesson.html의 <video src="/videos/{lessonId}?enrollmentId=…"> (SecurityConfig: USER만)
 *  - 호출함:   ProgressService.authorizeVideo (본인 확인 + VideoAccessPolicy), 파일은 file.upload-dir 아래에서 읽는다
 * ❓ 구간 요청(Range) 미지원: 전체 파일을 그대로 응답한다. 시크바 이동 지원 여부는 #01 결정 대기(이슈 주의 사항).
 * ❓ 영상 파일 위치: 시드의 video_key(videos/course-N/lesson-M.mp4)를 file.upload-dir 아래 경로로 본다. 파일이 없으면 404.
 * [기술] 🟢 HttpServletResponse로 권한 검사 후 파일 출력, 🟢 @Value
 */
@Controller
@RequestMapping("/videos")
@RequiredArgsConstructor
public class LessonVideoController {

    private final ProgressService progressService;
    private final FileStorage fileStorage;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @GetMapping("/{lessonId}")
    public void video(@AuthenticationPrincipal AuthDetails details,
                      @PathVariable Long lessonId,
                      @RequestParam Long enrollmentId,
                      HttpServletResponse response) throws IOException {
        String videoKey = progressService.authorizeVideo(details.getUserId(), enrollmentId, lessonId);   // 권한 확인이 먼저

        // 클라우드 저장소면 짧게 유효한 주소로 보낸다(권한 확인은 위에서 끝남). 로컬이면 아래에서 서버가 직접 내려보낸다
        String signedUrl = fileStorage.presignedUrlOrNull(videoKey, java.time.Duration.ofHours(2));
        if (signedUrl != null) {
            response.setHeader("Cache-Control", "private, no-store");
            response.sendRedirect(signedUrl);
            return;
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = base.resolve(videoKey).normalize();
        // 경로 조작 방지: 업로드 폴더 밖이거나 파일이 없으면 존재를 알리지 않고 404
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        response.setContentType("video/mp4");
        response.setContentLengthLong(Files.size(file));
        response.setHeader("Cache-Control", "private, no-store");   // 수강 기간이 끝나면 다시 확인해야 한다
        Files.copy(file, response.getOutputStream());
    }
}
