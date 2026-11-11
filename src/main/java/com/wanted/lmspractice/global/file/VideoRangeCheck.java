package com.wanted.lmspractice.global.file;

import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * [역할] 영상 구간 요청(Range)이 파일 범위 밖이거나 형식이 틀리면, 본문 없는 416 응답을 만들어 준다.
 * [왜]  ResponseEntity<Resource> 를 그대로 돌려주면 스프링이 Range 를 처리해 206 으로 잘라 보내지만,
 *       범위가 잘못된 요청에는 상태만 416 으로 바꾸고 파일 전체(수 MB)를 본문으로 보냈다(체크리스트 ④ 검증에서 확인).
 *       416 은 본문이 필요 없는 응답이라, 같은 판단(HttpRange 해석)을 먼저 해서 본문 없이 끝낸다.
 * [연결]
 *  - 호출받음: LessonVideoController(학습자 영상), AdminVideoController·InstructorVideoController(업로드 영상 미리보기)
 *  - 정상 범위·Range 없음이면 null — 호출한 쪽이 지금처럼 Resource 를 돌려주고 스프링이 200/206 을 만든다
 * [기술] 🟡 HttpRange(스프링 표준 Range 해석) — 스프링이 내부에서 쓰는 것과 같은 규칙으로 판단하려고 쓴다
 */
public final class VideoRangeCheck {

    private VideoRangeCheck() {
    }

    /**
     * @param rangeHeader 요청의 Range 헤더 값(없으면 null)
     * @return 만족할 수 없는 범위면 416(Content-Range: bytes *&#47;길이, 본문 없음), 그 밖에는 null
     */
    public static ResponseEntity<Resource> unsatisfiableOrNull(String rangeHeader, Resource resource) throws IOException {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            HttpRange.toResourceRegions(ranges, resource);   // 시작 위치가 파일 길이 이상이거나 범위 합이 너무 크면 예외
            return null;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength())
                    .build();
        }
    }
}
