package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFileDTO;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.repository.WorkFileRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] WorkService 통합 테스트 — 최종 과제 저장(최초·재제출)·수정(같은 버전·이미지 교체)·조회·이미지 열람 권한·실패 시 파일 정리
 * [담당] 팀원 4 · #16   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. DB 변경은 테스트 트랜잭션이 롤백한다.
 *       업로드 파일은 실제 DB 롤백과 무관하게 디스크에 남으므로, 시드 uploads/ 가 아닌 build/test-uploads 에만 쓴다.
 *       시드: learner(2)의 수강 2(색연필화 응용, 작품 없음), learner2(3)의 수강 5, admin 계정.
 * 🟡 미학습 기술: @SpringBootTest, @TestPropertySource (작업분배 §5 허용 범위 + 업로드 폴더 분리)
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "file.upload-dir=build/test-uploads")
class WorkServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final Long ENROLLMENT = 2L;   // learner 의 수강, 시드에 작품 없음

    @Autowired
    private WorkService workService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkRepository workRepository;
    @Autowired
    private WorkVersionRepository workVersionRepository;
    @Autowired
    private WorkFileRepository workFileRepository;
    @Autowired
    private FileStorage fileStorage;

    /** 저장소에서 key 를 읽을 수 있는지 — 로컬 디스크·클라우드 어느 저장소에서도 같은 방식(FileStorage.open)으로 확인한다 */
    private boolean stored(String key) {
        try (InputStream in = fileStorage.open(key)) {
            return in != null;
        } catch (BusinessException e) {
            return false;   // NOT_FOUND — 없는 파일
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    private MultipartFile image(String name, byte[] content) {
        return new MockMultipartFile("images", name, null, content);
    }

    private Long save(String title, List<MultipartFile> images) {
        return workService.saveFinalWorkVersion(userId("learner"), ENROLLMENT, 1L, CourseLevel.INTERMEDIATE,
                title, "설명입니다", images);
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    private long filesOnDisk() throws IOException {
        Path works = Paths.get("build/test-uploads/works");
        if (!Files.exists(works)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(works)) {
            return s.count();
        }
    }

    private Long workIdOf(Long versionId) {
        return workVersionRepository.findById(versionId).orElseThrow().getWorkId();
    }

    @Test
    @DisplayName("최초 제출: 작품(FINAL)·버전 1·이미지가 저장되고 표시본이 새 버전이며 작성자가 조회할 수 있다")
    void firstSubmission() {
        Long versionId = save("나의 정물화", List.of(image("a.png", PNG), image("b.jpg", JPG)));

        WorkDTO dto = workService.getWork(workIdOf(versionId), userId("learner"), Role.USER);
        assertThat(dto.getWorkVersionId()).isEqualTo(versionId);
        assertThat(dto.getVersionNo()).isEqualTo(1);
        assertThat(dto.getTitle()).isEqualTo("나의 정물화");
        assertThat(dto.getImageIds()).hasSize(2);
        assertThat(dto.getHobbyId()).isEqualTo(1L);
        assertThat(dto.getLevel()).isEqualTo(CourseLevel.INTERMEDIATE);
        assertThat(dto.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId))
                .extracting(f -> f.getSortOrder()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("재제출: 같은 작품에 버전 2가 생기고 표시본이 새 버전으로 바뀐다(이전 버전은 보존)")
    void resubmission() {
        Long v1 = save("1차", List.of(image("a.png", PNG)));
        Long v2 = save("2차", List.of(image("b.png", PNG)));

        assertThat(workIdOf(v2)).isEqualTo(workIdOf(v1));
        assertThat(workVersionRepository.findById(v2).orElseThrow().getVersionNo()).isEqualTo(2);
        assertThat(workService.getWork(workIdOf(v2), userId("learner"), Role.USER).getTitle()).isEqualTo("2차");
        assertThat(workVersionRepository.findById(v1)).isPresent();
    }

    @Test
    @DisplayName("거부: 0장·6장은 INVALID_IMAGE_COUNT, 텍스트 파일은 INVALID_IMAGE_FILE, 빈 제목은 INVALID_INPUT, 남의 수강은 ENROLLMENT_NOT_FOUND")
    void rejections() throws IOException {
        long before = filesOnDisk();
        List<MultipartFile> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(image(i + ".png", PNG));
        }

        assertThatThrownBy(() -> save("제목", List.of()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_COUNT));
        assertThatThrownBy(() -> save("제목", six))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_COUNT));
        assertThatThrownBy(() -> save("제목", List.of(image("note.txt", "hi".getBytes(StandardCharsets.UTF_8)))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThatThrownBy(() -> save("  ", List.of(image("a.png", PNG))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> workService.saveFinalWorkVersion(userId("learner2"), ENROLLMENT, 1L, CourseLevel.BASIC,
                "제목", null, List.of(image("a.png", PNG))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
        assertThat(filesOnDisk()).isEqualTo(before);
        assertThat(workRepository.findByEnrollmentId(ENROLLMENT)).isEmpty();
    }

    @Test
    @DisplayName("3번째 파일에서 실패하면 앞 2개 파일이 디스크에 남지 않고 DB에도 저장되지 않는다")
    void thirdFileFailureLeavesNothing() throws IOException {
        long before = filesOnDisk();
        List<MultipartFile> images = List.of(image("1.png", PNG), image("2.jpg", JPG),
                image("3.png", "not an image".getBytes(StandardCharsets.UTF_8)));   // 확장자만 png

        assertThatThrownBy(() -> save("제목", images))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThat(filesOnDisk()).isEqualTo(before);
        assertThat(workRepository.findByEnrollmentId(ENROLLMENT)).isEmpty();
    }

    @Test
    @DisplayName("수정: 이미지를 안 보내면 같은 버전의 제목·설명만 바뀌고 이미지는 유지된다")
    void updateKeepsImages() {
        Long versionId = save("수정 전", List.of(image("a.png", PNG)));
        Long fileId = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId).get(0).getId();

        workService.updateFinalWorkVersion(userId("learner"), versionId, "수정 후", "새 설명", null);

        WorkVersion version = workVersionRepository.findById(versionId).orElseThrow();
        assertThat(version.getTitle()).isEqualTo("수정 후");
        assertThat(version.getDescription()).isEqualTo("새 설명");
        assertThat(version.getVersionNo()).isEqualTo(1);   // 새 버전을 만들지 않는다
        assertThat(workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId))
                .extracting(f -> f.getId()).containsExactly(fileId);
    }

    @Test
    @DisplayName("수정: 이미지를 보내면 전체 교체되고 이전 파일은 디스크에서 지워진다")
    void updateReplacesImages() {
        Long versionId = save("제목", List.of(image("a.png", PNG)));
        Long oldFileId = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId).get(0).getId();
        String oldKey = workService.openFile(oldFileId, userId("learner"), Role.USER).getKey();
        assertThat(stored(oldKey)).isTrue();

        workService.updateFinalWorkVersion(userId("learner"), versionId, "제목", null,
                List.of(image("x.jpg", JPG), image("y.png", PNG)));

        var files = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId);
        assertThat(files).hasSize(2);
        assertThat(files).extracting(f -> f.getMimeType()).containsExactly("image/jpeg", "image/png");
        assertThat(stored(oldKey)).isFalse();
        assertThat(stored(workService.openFile(files.get(0).getId(), userId("learner"), Role.USER).getKey())).isTrue();
    }

    @Test
    @DisplayName("수정 실패(위장 파일): 이전 이미지와 제목이 그대로 남는다 / 남의 작품 수정은 WORK_NOT_FOUND")
    void updateFailureKeepsOld() {
        Long versionId = save("원래 제목", List.of(image("a.png", PNG)));
        Long oldFileId = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId).get(0).getId();
        String oldKey = workService.openFile(oldFileId, userId("learner"), Role.USER).getKey();

        assertThatThrownBy(() -> workService.updateFinalWorkVersion(userId("learner"), versionId, "바뀐 제목", null,
                List.of(image("fake.png", "text".getBytes(StandardCharsets.UTF_8)))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
        assertThat(stored(oldKey)).isTrue();
        assertThat(workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId)).hasSize(1);

        assertThatThrownBy(() -> workService.updateFinalWorkVersion(userId("learner2"), versionId, "탈취", null, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }

    @Test
    @DisplayName("비공개 작품 이미지: 작성자·관리자는 열리고, 다른 학습자·강사(담당 아님)는 403, 비로그인은 403")
    void privateImageAccess() {
        Long versionId = save("비공개", List.of(image("a.png", PNG)));
        Long fileId = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId).get(0).getId();

        WorkFileDTO own = workService.openFile(fileId, userId("learner"), Role.USER);
        assertThat(own.getMimeType()).isEqualTo("image/png");
        assertThat(stored(own.getKey())).isTrue();
        assertThat(stored(workService.openFile(fileId, userId("admin"), Role.ADMIN).getKey())).isTrue();

        assertThatThrownBy(() -> workService.openFile(fileId, userId("learner2"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
        assertThatThrownBy(() -> workService.openFile(fileId, null, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("공개 작품 이미지는 다른 학습자도 열리고, 관리자 숨김이면 다시 403 (작성자는 계속 허용)")
    void publicAndHiddenImageAccess() {
        Long versionId = save("공개할 작품", List.of(image("a.png", PNG)));
        Long fileId = workFileRepository.findByWorkVersionIdOrderBySortOrder(versionId).get(0).getId();
        var work = workRepository.findById(workIdOf(versionId)).orElseThrow();
        ReflectionTestUtils.setField(work, "visibility", Visibility.PUBLIC);   // 공개 전환은 #18 소관 — 테스트에서만 직접 세팅

        assertThat(stored(workService.openFile(fileId, userId("learner2"), Role.USER).getKey())).isTrue();

        ReflectionTestUtils.setField(work, "adminHidden", true);
        assertThatThrownBy(() -> workService.openFile(fileId, userId("learner2"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_ACCESS_DENIED));
        assertThat(stored(workService.openFile(fileId, userId("learner"), Role.USER).getKey())).isTrue();
    }

    @Test
    @DisplayName("없는 이미지 id는 NOT_FOUND, 없는 작품 id는 WORK_NOT_FOUND")
    void notFound() {
        assertThatThrownBy(() -> workService.openFile(999999L, userId("learner"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> workService.getWork(999999L, userId("learner"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }
}
