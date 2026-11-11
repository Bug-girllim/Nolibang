package com.wanted.lmspractice.global.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] FileStorage 단위 테스트 — 형식(확장자·실제 내용)·크기 검증, UUID 저장, 중간 실패 시 정리, 경로 조작 차단
 * [담당] 팀원 4 · #16   [전제] 스프링·DB 없이 임시 폴더만 사용한다.
 * 🟡 미학습 기술: JUnit @TempDir, Mockito mock (파일·크기 시나리오를 만들기 위한 테스트 전용 사용)
 */
class FileStorageTest {

    @TempDir
    Path dir;
    FileStorage storage;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @BeforeEach
    void setUp() {
        storage = new FileStorage(dir.toString());
    }

    private MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("images", name, null, content);
    }

    private long storedCount() throws IOException {
        Path works = dir.resolve("works");
        if (!Files.exists(works)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(works)) {
            return s.count();
        }
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("JPG·PNG·WebP는 UUID 파일명으로 저장되고 MIME은 실제 내용 기준이다")
    void storesThreeFormats() {
        List<StoredFile> stored = storage.storeAll(List.of(file("a.jpg", JPG), file("b.PNG", PNG), file("c.webp", WEBP)));

        assertThat(stored).extracting(StoredFile::getMimeType).containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(stored).allSatisfy(f -> {
            assertThat(f.getKey()).matches("works/[0-9a-f-]{36}\\.(jpg|png|webp)");
            assertThat(storage.load(f.getKey())).exists();
        });
        assertThat(stored.get(1).getOriginalName()).isEqualTo("b.PNG");
    }

    @Test
    @DisplayName("텍스트 파일·허용하지 않는 확장자는 거부한다")
    void rejectsTextAndOtherExtensions() throws IOException {
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store(file("note.txt", text)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThatThrownBy(() -> storage.store(file("pic.gif", PNG)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThatThrownBy(() -> storage.store(file("noext", PNG)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("확장자만 이미지로 바꾼 위장 파일과, 확장자와 내용이 다른 파일은 거부한다")
    void rejectsDisguisedFiles() throws IOException {
        byte[] text = "not an image at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store(file("fake.png", text)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThatThrownBy(() -> storage.store(file("png-as.jpg", PNG)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("빈 파일은 거부, 정확히 50MB는 저장되고 50MB를 넘으면 FILE_TOO_LARGE")
    void sizeLimit() {
        assertThatThrownBy(() -> storage.store(file("empty.png", new byte[0])))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));

        byte[] exactly = new byte[(int) FileStorage.MAX_FILE_BYTES];
        System.arraycopy(PNG, 0, exactly, 0, PNG.length);
        assertThat(storage.store(file("max.png", exactly)).getSizeBytes()).isEqualTo(FileStorage.MAX_FILE_BYTES);

        MultipartFile tooLarge = mock(MultipartFile.class);   // 51MB 배열을 만들지 않고 크기만 흉내
        when(tooLarge.isEmpty()).thenReturn(false);
        when(tooLarge.getSize()).thenReturn(FileStorage.MAX_FILE_BYTES + 1);
        when(tooLarge.getOriginalFilename()).thenReturn("big.png");
        assertThatThrownBy(() -> storage.store(tooLarge))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("3번째 파일에서 실패하면 앞 2개 파일이 디스크에 남지 않는다")
    void thirdFileFailureLeavesNothing() throws IOException {
        // 확장자 검사는 통과하지만 저장 중 내용 검사에서 실패하는 파일
        MultipartFile fake = file("third.png", "not an image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storage.storeAll(List.of(file("1.png", PNG), file("2.jpg", JPG), fake)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("읽는 도중 입출력 오류가 나도 앞서 저장한 파일을 지우고 FILE_SAVE_FAILED")
    void ioFailureCleansUp() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getOriginalFilename()).thenReturn("broken.png");
        when(broken.getInputStream()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> storage.storeAll(List.of(file("1.png", PNG), broken)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FILE_SAVE_FAILED));
        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("업로드 폴더 밖을 가리키는 key와 없는 파일은 NOT_FOUND, 삭제는 없는 파일도 조용히 넘어간다")
    void loadAndDelete() throws IOException {
        Path outside = dir.getParent().resolve("outside-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "secret");
        String outsideKey = "../" + outside.getFileName();

        try {
            assertThatThrownBy(() -> storage.load(outsideKey))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
            assertThatThrownBy(() -> storage.load("works/none.png"))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));

            StoredFile stored = storage.store(file("a.png", PNG));
            storage.deleteQuietly(stored.getKey());
            storage.deleteQuietly(stored.getKey());          // 두 번째는 이미 없음 — 예외 없음
            storage.deleteQuietly(outsideKey);               // 폴더 밖은 지우지 않는다
            assertThat(storedCount()).isZero();
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("삭제 실패(#161): 대기 목록에 넣고, 재처리에 성공하면 목록에서 빠진다")
    void failedDeleteIsRetried() throws IOException {
        Path blocker = dir.resolve("works/locked.png");               // 비어 있지 않은 폴더 = 어느 OS에서도 삭제 실패
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("inner"), "x");

        storage.deleteQuietly("works/locked.png");
        assertThat(storage.getPendingDeleteKeys()).containsExactly("works/locked.png");

        assertThat(storage.retryPendingDeletes()).isEqualTo(1);       // 아직 실패 — 대기 유지
        Files.delete(blocker.resolve("inner"));                       // 실패 원인이 풀림
        assertThat(storage.retryPendingDeletes()).isZero();
        assertThat(blocker).doesNotExist();
        assertThat(storage.getPendingDeleteKeys()).isEmpty();
    }

    @Test
    @DisplayName("삭제 재처리(#161): 정해진 횟수만큼 실패하면 포기하고 목록에서 뺀다. 없는 파일·시드 파일은 대기 목록에 넣지 않는다")
    void retryGivesUpAfterMaxAttempts() throws IOException {
        Path blocker = dir.resolve("works/stuck.png");
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("inner"), "x");

        storage.deleteQuietly("works/stuck.png");                     // 1회째
        for (int i = 1; i < FileStorage.MAX_DELETE_ATTEMPTS; i++) {
            storage.retryPendingDeletes();
        }
        assertThat(storage.getPendingDeleteKeys()).isEmpty();
        assertThat(blocker).exists();                                 // 직접 정리 대상(ERROR 로그)

        storage.deleteQuietly("works/no-such-file.png");
        storage.deleteQuietly("seed/sample.png");
        assertThat(storage.getPendingDeleteKeys()).isEmpty();
    }

    @Test
    @DisplayName("삭제 재처리 대기 목록은 파일에 남아 서버를 재시작해도 이어서 지우고, 시도 횟수도 이어진다. 목록이 비면 파일도 지운다")
    void pendingDeletesSurviveRestart() throws IOException {
        Path blocker = dir.resolve("works/locked.png");
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("inner"), "x");
        Path pendingFile = dir.resolve(FileStorage.PENDING_DELETES_FILE);

        storage.deleteQuietly("works/locked.png");                    // 1회째 실패
        storage.retryPendingDeletes();                                // 2회째 실패
        assertThat(pendingFile).exists();

        FileStorage restarted = new FileStorage(dir.toString());      // 서버 재시작
        assertThat(restarted.getPendingDeleteKeys()).containsExactly("works/locked.png");
        for (int i = 2; i < FileStorage.MAX_DELETE_ATTEMPTS - 1; i++) {
            restarted.retryPendingDeletes();                          // 3 ~ (최대-1)회째 — 횟수가 처음부터 다시 세지지 않는다
        }
        assertThat(restarted.getPendingDeleteKeys()).containsExactly("works/locked.png");

        Files.delete(blocker.resolve("inner"));                       // 실패 원인이 풀림
        assertThat(restarted.retryPendingDeletes()).isZero();
        assertThat(blocker).doesNotExist();
        assertThat(pendingFile).doesNotExist();
        assertThat(new FileStorage(dir.toString()).getPendingDeleteKeys()).isEmpty();
    }

    @Test
    @DisplayName("대기 목록 파일의 깨진 줄은 건너뛰고 나머지만 되살린다")
    void brokenPendingLinesAreSkipped() throws IOException {
        Files.writeString(dir.resolve(FileStorage.PENDING_DELETES_FILE),
                "2\tworks/a.png\nabc\tworks/b.png\nno-tab\n3\t\n\n1\tworks/c.png\n");

        assertThat(new FileStorage(dir.toString()).getPendingDeleteKeys())
                .containsExactlyInAnyOrder("works/a.png", "works/c.png");
    }
}
