package com.wanted.lmspractice.domain.hobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.file.FileStorage;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [역할] 대표 사진 교체의 파일 정리 순서를 실제 커밋·롤백으로 검증한다 (#176)
 *   - 커밋되면: 이전 사진 파일을 커밋 뒤에 지운다
 *   - 롤백되면: 방금 저장한 새 파일을 지우고, 이전 사진 파일은 그대로 둔다(DB가 계속 가리키므로)
 * [주의] HobbyImageTest 는 테스트 전체가 롤백되는 트랜잭션이라 커밋 뒤 동작을 볼 수 없어 따로 둔다.
 *        실제로 커밋하므로 대분류 1의 image_key 를 테스트 전 값으로 되돌리고, 만든 파일을 지운다.
 */
@SpringBootTest
class HobbyImageCommitTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final long CATEGORY_ID = 1L;

    @Autowired
    private HobbyService hobbyService;
    @Autowired
    private HobbyCatalogCache catalogCache;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;

    private final List<String> storedKeys = new ArrayList<>();
    private String originalKey;

    @BeforeEach
    void rememberOriginal() {
        originalKey = categoryKey();
    }

    @AfterEach
    void restore() {
        jdbcTemplate.update("UPDATE categories SET image_key = ? WHERE id = ?", originalKey, CATEGORY_ID);
        catalogCache.invalidate();
        storedKeys.stream().filter(key -> !key.equals(originalKey)).forEach(fileStorage::deleteQuietly);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("image", "pic.png", "image/png", PNG);
    }

    private String categoryKey() {
        return jdbcTemplate.queryForObject("SELECT image_key FROM categories WHERE id = ?", String.class, CATEGORY_ID);
    }

    /** 사진을 한 번 올리고 커밋한 뒤 그 key */
    private String uploadAndCommit() {
        hobbyService.applyCategoryImage(CATEGORY_ID, png(), false);   // 서비스의 @Transactional 이 커밋한다
        String key = categoryKey();
        storedKeys.add(key);
        return key;
    }

    @Test
    @DisplayName("교체가 커밋되면 이전 사진 파일은 커밋 뒤에 지워지고 새 파일이 남는다")
    void commitDeletesOldFileAfterCommit() {
        String first = uploadAndCommit();
        String second = uploadAndCommit();

        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> fileStorage.load(first)).isInstanceOf(BusinessException.class);
        assertThat(fileStorage.load(second)).exists();
    }

    @Test
    @DisplayName("교체가 롤백되면 새로 저장한 파일은 지워지고, DB가 가리키는 이전 사진 파일은 그대로 남는다")
    void rollbackKeepsOldFileAndDeletesNewFile() {
        String first = uploadAndCommit();
        List<String> keyInRollback = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            hobbyService.applyCategoryImage(CATEGORY_ID, png(), false);   // 바깥 트랜잭션에 참여
            entityManager.flush();                                         // 변경 감지 UPDATE 를 내보낸 뒤 같은 트랜잭션에서 읽는다
            keyInRollback.add(jdbcTemplate.queryForObject(
                    "SELECT image_key FROM categories WHERE id = ?", String.class, CATEGORY_ID));
            status.setRollbackOnly();                                      // 커밋 실패와 같은 결과
        });
        String second = keyInRollback.get(0);
        storedKeys.add(second);

        assertThat(categoryKey()).isEqualTo(first);
        assertThat(fileStorage.load(first)).exists();
        assertThatThrownBy(() -> fileStorage.load(second)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("사진 제거가 커밋되면 이전 사진 파일이 지워진다")
    void removeDeletesFileAfterCommit() {
        String first = uploadAndCommit();

        hobbyService.applyCategoryImage(CATEGORY_ID, null, true);

        assertThat(categoryKey()).isNull();
        assertThatThrownBy(() -> fileStorage.load(first)).isInstanceOf(BusinessException.class);
    }
}
