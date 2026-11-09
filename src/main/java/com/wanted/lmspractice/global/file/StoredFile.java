package com.wanted.lmspractice.global.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] FileStorage가 저장을 끝낸 파일 1개의 결과 (업로드 폴더 기준 key, 원래 이름, 실제 MIME, 바이트 수)
 * [담당] 팀원 4 · #16
 * [연결]
 *  - 만든 곳:  FileStorage.store / storeAll
 *  - 쓰는 곳:  WorkService (work_files 칼럼 original_key·original_name·mime_type·*_size_bytes 에 그대로 넣는다)
 */
@Getter
@AllArgsConstructor
public class StoredFile {

    private final String key;
    private final String originalName;
    private final String mimeType;
    private final long sizeBytes;
}
