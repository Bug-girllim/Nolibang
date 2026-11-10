package com.wanted.lmspractice.domain.work.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.work.dto.WorkFileDTO;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 작품 이미지 출력 — 권한 확인 후 HttpServletResponse 로 파일을 내보낸다 (업로드 폴더는 정적 리소스로 노출하지 않음)
 * [담당] 팀원 4 · #16   [API] FL-01 GET /files/works/{fileId}   [기획] NFR-01, Q-02
 * [연결]
 *  - 호출받음: 브라우저의 모든 작품 이미지 &lt;img src="/files/works/{id}"&gt; (로그인 필요 — SecurityConfig)
 *  - 호출함:   WorkService.openFile (권한 판단·파일 경로)
 * [실패] 403 WORK_ACCESS_DENIED / 404 NOT_FOUND — 예외는 GlobalExceptionHandler 가 처리한다(컨트롤러에 try-catch 없음)
 * [기술] 🟢 HttpServletResponse 직접 출력 (LessonVideoController 와 같은 방식)
 */
@Controller
@RequestMapping("/files/works")
@RequiredArgsConstructor
public class WorkFileController {

    private final WorkService workService;
    private final FileStorage fileStorage;

    @GetMapping("/{fileId}")
    public void image(@AuthenticationPrincipal AuthDetails details,
                      @PathVariable Long fileId,
                      HttpServletResponse response) throws IOException {
        WorkFileDTO file = workService.openFile(fileId, details.getUserId(), details.getRole());
        try (InputStream in = fileStorage.open(file.getKey())) {                 // 로컬 디스크든 클라우드든 같은 방식으로 읽는다
            response.setContentType(file.getMimeType());                       // 저장 때 매직 바이트로 확인한 MIME
            response.setContentLengthLong(file.getSizeBytes());
            response.setHeader("X-Content-Type-Options", "nosniff");           // 브라우저가 내용을 보고 형식을 다시 추측하지 못하게
            response.setHeader("Cache-Control", "private, no-cache");          // 공개→비공개·숨김 전환이 바로 반영되도록 매번 권한 확인
            in.transferTo(response.getOutputStream());
        }
    }
}
