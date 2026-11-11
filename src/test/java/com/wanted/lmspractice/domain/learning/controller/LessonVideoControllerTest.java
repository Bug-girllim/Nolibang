package com.wanted.lmspractice.domain.learning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.global.file.FileStorage;
import com.wanted.lmspractice.global.file.VideoStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] 학습자 영상 출력의 저장 위치 선택 검증 (#176)
 *   S3 모드(FileStorage 가 임시 주소를 만들어 줌)에서도 강사·관리자 업로드 영상(videos/uploaded/…)은
 *   서버 폴더에 있으므로(VideoStorage) 임시 주소로 보내지 않고 서버가 직접 내려보내야 한다.
 * [방식] 권한 확인(ProgressService)·S3(FileStorage)는 가짜로 두고, VideoStorage 는 임시 폴더로 실제 객체를 쓴다.
 */
class LessonVideoControllerTest {

    private static final String SEED_KEY = "videos/drawing/색연필화/색연필화_입문_1차시_10분.mp4";
    private static final String UPLOADED_KEY = "videos/uploaded/instructor-6/lesson_1a2b3c4d.mp4";
    private static final String SIGNED_URL = "https://bucket.example.com/signed";

    @TempDir
    Path uploadDir;

    private final ProgressService progressService = mock(ProgressService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final AuthDetails learner = mock(AuthDetails.class);
    private LessonVideoController controller;

    @BeforeEach
    void setUp() {
        controller = new LessonVideoController(progressService, fileStorage, new VideoStorage(uploadDir.toString()));
        ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());
        when(learner.getUserId()).thenReturn(2L);
        when(fileStorage.presignedUrlOrNull(anyString(), any(Duration.class))).thenReturn(SIGNED_URL);   // S3 모드
    }

    @Test
    @DisplayName("S3 모드: 시드·버킷 영상은 권한 확인 뒤 짧게 유효한 임시 주소로 보낸다")
    void bucketVideoRedirectsToSignedUrl() throws Exception {
        when(progressService.authorizeVideo(2L, 1L, 3L)).thenReturn(SEED_KEY);

        ResponseEntity<Resource> response = controller.video(learner, 3L, 1L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString(SIGNED_URL);
    }

    @Test
    @DisplayName("S3 모드여도 업로드 영상(videos/uploaded/…)은 임시 주소 대신 서버 폴더의 파일을 내려보낸다")
    void uploadedVideoIsServedFromServerEvenInS3Mode() throws Exception {
        Path file = uploadDir.resolve(UPLOADED_KEY);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p'});
        when(progressService.authorizeVideo(2L, 1L, 3L)).thenReturn(UPLOADED_KEY);

        ResponseEntity<Resource> response = controller.video(learner, 3L, 1L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFile().toPath()).isEqualTo(file.toAbsolutePath().normalize());
        verify(fileStorage, never()).presignedUrlOrNull(anyString(), any(Duration.class));
    }
}
