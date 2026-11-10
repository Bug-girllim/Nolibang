package com.wanted.lmspractice.global.file;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [역할] 관리자·강사가 올린 영상 1개의 정보 — uploads/videos/uploaded/ 폴더의 파일에서 읽는다(DB에 저장하지 않음)
 * [연결]
 *  - 만듦:   VideoStorage.store / list / find
 *  - 사용:   LessonVideoService → AdminVideoController·InstructorVideoController → templates/video/manage.html
 * [기술] 🟢 Lombok @Getter (Thymeleaf에서 ${v.fileName} 처럼 읽는다)
 */
@Getter
@RequiredArgsConstructor
public class UploadedVideo {

    /** 파일 위치 = 차시에 연결할 때 lessons.video_key 에 그대로 넣는 값 (예: videos/uploaded/instructor-6/목공_입문_1차시_a3f9c2d1.mp4) */
    private final String videoKey;

    /** 파일명만 (예: 목공_입문_1차시_a3f9c2d1.mp4) */
    private final String fileName;

    /** 올린 강사 id. 관리자가 올린 파일이면 null */
    private final Long ownerInstructorId;

    private final long sizeBytes;

    /** MP4 헤더(mvhd)에서 읽은 재생 시간(초). 읽지 못하면 null — 이 경우 차시에 연결할 수 없다 */
    private final Double durationSec;

    /** 파일 수정 시각 = 올린 시각 */
    private final LocalDateTime uploadedAt;

    /** 화면 표시용 올린 사람 (관리자 / 강사 #6) */
    public String getOwnerLabel() {
        return ownerInstructorId == null ? "관리자" : "강사 #" + ownerInstructorId;
    }

    /** 화면 표시용 크기 (예: 6.7 MB) */
    public String getSizeLabel() {
        return String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0);
    }

    /** 화면 표시용 재생 시간 (예: 10:00, 1:02:03). 읽지 못했으면 "-" */
    public String getDurationLabel() {
        return durationLabel(durationSec);
    }

    /** 초 → "m:ss" / "h:mm:ss". null 이면 "-" (차시 목록 표시에도 쓴다) */
    public static String durationLabel(Double seconds) {
        if (seconds == null) {
            return "-";
        }
        long total = Math.round(seconds);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
