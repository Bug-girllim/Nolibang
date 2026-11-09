package com.wanted.lmspractice.domain.work.dto;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 이미지 출력에 필요한 값 묶음 — 권한 확인이 끝난 파일의 실제 경로와 응답 헤더 값
 * [담당] 팀원 4 · #16
 * [연결] 만든 곳: WorkService.openFile / 쓰는 곳: WorkFileController (HttpServletResponse 출력)
 */
@Getter
@AllArgsConstructor
public class WorkFileDTO {

    private final Path path;
    private final String mimeType;
    private final long sizeBytes;
}
