package com.wanted.lmspractice.domain.hobby.controller;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [역할] 관리자가 올린 대분류·세부 취미 대표 사진 출력 — GET /hobbies/image-files/{파일명}
 * [담당] 관리자 사진 관리   [화면] 취미 탐색·메인 카드, 세부 취미 상세, 관리자 편집 미리보기
 * [연결]
 *  - 호출받음: 브라우저 &lt;img src="/hobbies/image-files/…"&gt; (취미 탐색은 비회원도 보므로 공개 경로 /hobbies/** 아래에 둔다 — SecurityConfig 변경 없음)
 *  - 호출함:   FileStorage.open (로컬 디스크든 클라우드든 같은 방식)
 * [규칙] 파일명은 UUID + jpg/png/webp 형식만 받는다(그 밖은 404) — 경로 조작 차단. 저장 폴더는 hobby/ 고정이라 작품 이미지(works/)는 이 주소로 열 수 없다.
 * [기술] 🟢 HttpServletResponse 직접 출력 (WorkFileController 와 같은 방식)
 */
@Controller
@RequestMapping("/hobbies/image-files")
@RequiredArgsConstructor
public class HobbyImageController {

    private static final Pattern NAME = Pattern.compile("^[0-9a-fA-F-]{36}[.](jpg|jpeg|png|webp)$");
    private static final Map<String, String> MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final FileStorage fileStorage;

    @GetMapping("/{name}")
    public void image(@PathVariable String name, HttpServletResponse response) throws IOException {
        if (!NAME.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try (InputStream in = fileStorage.open("hobby/" + name)) {
            response.setContentType(MIME.get(extension));
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "public, max-age=3600");   // 사진을 바꾸면 새 파일명(UUID)이라 주소가 달라진다
            in.transferTo(response.getOutputStream());
        }
    }
}
