package com.wanted.lmspractice.domain.learning.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 구간 요청(Range) 지원: ResponseEntity<Resource>로 돌려주면 스프링이 Range 헤더를 보고 206 Partial Content로 필요한 부분만 보낸다
 *   (Accept-Ranges: bytes 자동, 범위가 잘못되면 416). 그래서 시크바 이동·이어보기가 된다.
 * 영상 파일 위치: lessons.video_key 를 file.upload-dir(./uploads) 아래 상대 경로로 본다.
 *   예) video_key = videos/drawing/색연필화/색연필화_입문_1차시_10분.mp4 → ./uploads/videos/drawing/색연필화/… . 파일이 없으면 404.
 * [기술] 🟢 ResponseEntity<Resource>(Range 자동 처리), 🟢 @Value
 */
@Controller
@RequestMapping("/videos")
@RequiredArgsConstructor
public class LessonVideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final ProgressService progressService;
    private final FileStorage fileStorage;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @GetMapping("/{lessonId}")
    public ResponseEntity<Resource> video(@AuthenticationPrincipal AuthDetails details,
                                          @PathVariable Long lessonId,
                                          @RequestParam Long enrollmentId) {
        String videoKey = progressService.authorizeVideo(details.getUserId(), enrollmentId, lessonId);   // 권한 확인이 먼저

        // 클라우드 저장소면 짧게 유효한 주소로 보낸다(권한 확인은 위에서 끝남). 로컬이면 아래에서 서버가 직접 내려보낸다
        String signedUrl = fileStorage.presignedUrlOrNull(videoKey, Duration.ofHours(2));
        if (signedUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(signedUrl))
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .build();
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = base.resolve(videoKey).normalize();
        // 경로 조작 방지: 업로드 폴더 밖이거나 파일이 없으면 존재를 알리지 않고 404
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 본문을 Resource로 주면 Range 요청일 때 스프링이 206 + Content-Range로 잘라 보낸다
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .cacheControl(CacheControl.noStore().cachePrivate())   // 수강 기간이 끝나면 다시 확인해야 한다
                .body(new FileSystemResource(file));
    }
}
