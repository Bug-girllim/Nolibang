package com.wanted.lmspractice.domain.course.dto;

import com.wanted.lmspractice.global.file.UploadedVideo;
import com.wanted.lmspractice.global.file.VideoStorage;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 영상 업로드 화면의 "차시 선택" 한 줄 — 어떤 차시에 지금 어떤 영상이 연결돼 있는지
 * [연결]
 *  - 만듦:   LessonVideoService.getLessonOptions
 *  - 사용:   templates/video/manage.html (차시 선택 목록, 차시별 현재 영상 표)
 */
@Getter
@AllArgsConstructor
public class LessonVideoOptionDTO {

    private Long lessonId;
    private Long hobbyId;
    private String hobbyName;
    /** 입문·중급·고급 */
    private String levelLabel;
    private Integer lessonNo;
    /** 지금 연결된 영상 경로 (lessons.video_key) */
    private String videoKey;
    /** 지금 DB에 적힌 영상 길이(초) */
    private Double durationSec;

    /** 예: 색연필화 · 입문 · 1차시 */
    public String getLabel() {
        return hobbyName + " · " + levelLabel + " · " + lessonNo + "차시";
    }

    /** 경로에서 파일명만 (예: 색연필화_입문_1차시_10분.mp4) */
    public String getVideoFileName() {
        return videoKey == null ? "-" : videoKey.substring(videoKey.lastIndexOf('/') + 1);
    }

    /** 업로드 화면에서 올린 영상이 연결돼 있는지 (아니면 개발자가 폴더에 넣은 기본 영상) */
    public boolean isUploadedVideo() {
        return videoKey != null && videoKey.startsWith(VideoStorage.KEY_PREFIX);
    }

    public String getDurationLabel() {
        return UploadedVideo.durationLabel(durationSec);
    }
}
