package com.wanted.lmspractice.global.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] VideoStorage 단위 테스트 — MP4 검사(확장자·실제 내용·크기), 저장 위치(관리자·강사 폴더), 재생 시간 읽기,
 *        목록(전체·본인), key 규칙(경로 조작·다른 영상 폴더 차단), 삭제
 * [전제] 스프링·DB 없이 임시 폴더만 사용한다. 영상은 ftyp + moov/mvhd 상자만 있는 가짜 MP4 바이트로 만든다.
 * 🟡 미학습 기술: JUnit @TempDir, Mockito mock (FileStorageTest와 같은 테스트 전용 사용)
 */
class VideoStorageTest {

    @TempDir
    Path dir;
    VideoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new VideoStorage(dir.toString());
    }

    /** timescale·duration 만 넣은 최소 MP4 (ftyp → moov → mvhd version 0) */
    private static byte[] mp4(int timescale, int duration) {
        ByteBuffer mvhdPayload = ByteBuffer.allocate(100);   // 실제 mvhd 내용 길이(version 0)
        mvhdPayload.putInt(0).putInt(0).putInt(0).putInt(timescale).putInt(duration);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(box("ftyp", "isom\0\0\2\0".getBytes(StandardCharsets.US_ASCII)));
        out.writeBytes(box("moov", box("mvhd", mvhdPayload.array())));
        return out.toByteArray();
    }

    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length).putInt(8 + payload.length)
                .put(type.getBytes(StandardCharsets.US_ASCII)).put(payload).array();
    }

    private MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "video/mp4", content);
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("관리자 영상은 videos/uploaded/에 '원래이름_8글자.mp4'로 저장하고 재생 시간을 읽는다")
    void storesAdminMp4() {
        UploadedVideo saved = storage.store(file("목공 입문(1차시).mp4", mp4(1000, 600_000)), null);

        assertThat(saved.getFileName()).matches("목공_입문_1차시_[0-9a-f]{8}\\.mp4");
        assertThat(saved.getVideoKey()).isEqualTo("videos/uploaded/" + saved.getFileName());
        assertThat(saved.getOwnerInstructorId()).isNull();
        assertThat(saved.getOwnerLabel()).isEqualTo("관리자");
        assertThat(saved.getDurationSec()).isEqualTo(600.0);
        assertThat(saved.getDurationLabel()).isEqualTo("10:00");
        assertThat(Files.exists(dir.resolve(saved.getVideoKey()))).isTrue();
    }

    @Test
    @DisplayName("강사 영상은 videos/uploaded/instructor-{id}/에 저장되고, 목록은 본인 것만·관리자는 전체를 본다")
    void instructorFolder() {
        UploadedVideo mine = storage.store(file("a.mp4", mp4(1, 30)), 6L);
        storage.store(file("b.mp4", mp4(1, 30)), 7L);
        storage.store(file("c.mp4", mp4(1, 30)), null);

        assertThat(mine.getVideoKey()).startsWith("videos/uploaded/instructor-6/");
        assertThat(mine.getOwnerLabel()).isEqualTo("강사 #6");
        assertThat(storage.ownerOf(mine.getVideoKey())).isEqualTo(6L);
        assertThat(storage.listOf(6L)).extracting(UploadedVideo::getVideoKey).containsExactly(mine.getVideoKey());
        assertThat(storage.listOf(null)).hasSize(1);    // 관리자 파일만
        assertThat(storage.listAll()).hasSize(3);       // 관리자 + 강사 6 + 강사 7
        assertThat(storage.find(mine.getVideoKey()).getDurationSec()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("같은 이름을 두 번 올려도 덮어쓰지 않는다")
    void sameNameTwice() {
        UploadedVideo a = storage.store(file("a.mp4", mp4(1, 30)), null);
        UploadedVideo b = storage.store(file("a.mp4", mp4(1, 30)), null);

        assertThat(a.getVideoKey()).isNotEqualTo(b.getVideoKey());
        assertThat(storage.listAll()).hasSize(2);
    }

    @Test
    @DisplayName("확장자만 .mp4로 바꾼 파일, .mp4가 아닌 파일, 빈 파일은 INVALID_VIDEO_FILE")
    void rejectsNonMp4() {
        assertThatThrownBy(() -> storage.store(file("fake.mp4", "hello world, not a video".getBytes()), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VIDEO_FILE));
        assertThatThrownBy(() -> storage.store(file("real.mov", mp4(1, 30)), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VIDEO_FILE));
        assertThatThrownBy(() -> storage.store(file("empty.mp4", new byte[0]), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VIDEO_FILE));
        assertThatThrownBy(() -> storage.store(null, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VIDEO_FILE));
        assertThat(storage.listAll()).isEmpty();
    }

    @Test
    @DisplayName("500MB를 넘으면 VIDEO_TOO_LARGE (파일을 읽기 전에 거른다)")
    void rejectsTooLarge() throws IOException {
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(VideoStorage.MAX_VIDEO_BYTES + 1);
        when(big.getOriginalFilename()).thenReturn("big.mp4");
        when(big.getInputStream()).thenReturn(new ByteArrayInputStream(mp4(1, 30)));

        assertThatThrownBy(() -> storage.store(big, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VIDEO_TOO_LARGE));
        assertThat(storage.listAll()).isEmpty();
    }

    @Test
    @DisplayName("재생 시간을 읽을 수 없는 MP4도 저장은 되고, 시간은 null('-')")
    void unknownDuration() {
        byte[] onlyFtyp = box("ftyp", "isom\0\0\2\0".getBytes(StandardCharsets.US_ASCII));
        UploadedVideo saved = storage.store(file("x.mp4", onlyFtyp), null);

        assertThat(saved.getDurationSec()).isNull();
        assertThat(saved.getDurationLabel()).isEqualTo("-");
    }

    @Test
    @DisplayName("key 규칙 밖(경로 조작·다른 영상 폴더·없는 파일)은 미리보기·삭제·소유자 확인 모두 NOT_FOUND")
    void keyRuleBlocksOutside() throws IOException {
        Path seedVideo = dir.resolve("videos/drawing/색연필화/secret.mp4");
        Files.createDirectories(seedVideo.getParent());
        Files.write(seedVideo, mp4(1, 30));

        for (String key : List.of("videos/drawing/색연필화/secret.mp4", "videos/uploaded/../drawing/색연필화/secret.mp4",
                "videos/uploaded/..\\drawing\\secret.mp4", "videos/uploaded/instructor-x/a.mp4", "videos/uploaded/secret.txt",
                "videos/uploaded/없는파일.mp4", "../application.yaml")) {
            assertThatThrownBy(() -> storage.load(key))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
            assertThatThrownBy(() -> storage.delete(key))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
        }
        assertThat(storage.isUploadedKey("videos/drawing/색연필화/secret.mp4")).isFalse();
        assertThat(Files.exists(seedVideo)).isTrue();   // 다른 폴더 영상은 그대로

        UploadedVideo saved = storage.store(file("a.mp4", mp4(1, 30)), 6L);
        assertThat(storage.load(saved.getVideoKey())).exists();
        storage.delete(saved.getVideoKey());
        assertThat(storage.listAll()).isEmpty();
    }
}
