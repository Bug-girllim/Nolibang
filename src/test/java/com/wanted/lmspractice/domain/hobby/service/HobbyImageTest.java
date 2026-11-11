package com.wanted.lmspractice.domain.hobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService.ImageInfo;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.FileStorage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자 대표 사진 등록·교체·삭제 검증 — 대분류(categories.image_key)와 세부 취미(hobbies.thumbnail_key)
 * [검증] 올린 사진 주소가 목록·상세 DTO 에 나오는지 / 삭제하면 시안 기본 이미지로 돌아가는지 / 위장 파일은 막히는지 / 저장된 옛 파일이 지워지는지
 */
@SpringBootTest
@Transactional
class HobbyImageTest {

    /** 8x8 단색 PNG 의 앞부분 — 시그니처가 실제 PNG 라 FileStorage 의 매직 바이트 검사를 통과한다 */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private HobbyService hobbyService;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final List<String> storedKeys = new ArrayList<>();

    @AfterEach
    void cleanFiles() {
        storedKeys.forEach(fileStorage::deleteQuietly);   // DB 는 트랜잭션이 되돌리지만 디스크 파일은 직접 지운다
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("image", "pic.png", "image/png", PNG);
    }

    private String categoryKey(long id) {
        entityManager.flush();   // 변경 감지로 쌓인 UPDATE 를 DB 에 내보낸 뒤 JDBC 로 읽는다
        return jdbcTemplate.queryForObject("SELECT image_key FROM categories WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("대분류 사진을 올리면 카드 이미지가 올린 사진 주소로 바뀌고, 삭제하면 시안 기본 이미지로 돌아간다")
    void categoryImageUploadAndRemove() {
        ImageInfo before = hobbyService.getCategoryImage(1L);
        assertThat(before.uploaded()).isFalse();

        hobbyService.applyCategoryImage(1L, png(), false);
        String key = categoryKey(1L);
        storedKeys.add(key);
        assertThat(key).startsWith("hobby/");
        assertThat(hobbyService.getCategoryImage(1L).uploaded()).isTrue();
        CategoryDTO art = hobbyService.getCategories(Role.ADMIN).get(0);
        assertThat(art.getImagePath()).startsWith("/hobbies/image-files/").endsWith(key.substring(6));

        hobbyService.applyCategoryImage(1L, null, true);
        assertThat(categoryKey(1L)).isNull();
        assertThat(hobbyService.getCategories(Role.ADMIN).get(0).getImagePath()).isEqualTo(before.path());
        assertThatThrownBy(() -> fileStorage.load(key)).isInstanceOf(BusinessException.class);   // 옛 파일은 지워졌다
    }

    @Test
    @DisplayName("사진을 새로 올리면 이전 사진 파일은 지워진다 (교체)")
    void replaceDeletesOldFile() {
        hobbyService.applyCategoryImage(1L, png(), false);
        String first = categoryKey(1L);
        storedKeys.add(first);

        hobbyService.applyCategoryImage(1L, png(), false);
        String second = categoryKey(1L);
        storedKeys.add(second);

        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> fileStorage.load(first)).isInstanceOf(BusinessException.class);
        assertThat(fileStorage.load(second)).exists();
    }

    @Test
    @DisplayName("사진을 고르지 않고 삭제도 아니면 아무것도 바뀌지 않는다")
    void noChange() {
        hobbyService.applyCategoryImage(1L, null, false);
        hobbyService.applyCategoryImage(1L, new MockMultipartFile("image", new byte[0]), false);
        assertThat(categoryKey(1L)).isNull();
    }

    @Test
    @DisplayName("확장자만 이미지인 위장 파일은 INVALID_IMAGE_FILE 로 막히고 기존 사진은 그대로다")
    void rejectsFakeImage() {
        MockMultipartFile fake = new MockMultipartFile("image", "fake.png", "image/png", "not an image at all".getBytes());
        assertThatThrownBy(() -> hobbyService.applyCategoryImage(1L, fake, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
        assertThat(categoryKey(1L)).isNull();
    }

    @Test
    @DisplayName("세부 취미 사진도 올리면 상세 대표 이미지와 목록 카드 주소가 바뀌고, 삭제하면 시안 이미지로 돌아간다")
    void hobbyImageUploadAndRemove() {
        hobbyService.applyHobbyImage(1L, png(), false);
        entityManager.flush();
        String key = jdbcTemplate.queryForObject("SELECT thumbnail_key FROM hobbies WHERE id = 1", String.class);
        storedKeys.add(key);
        String expected = "/hobbies/image-files/" + key.substring(key.lastIndexOf('/') + 1);

        assertThat(hobbyService.getHobby(1L, Role.ADMIN).getHeroImage()).isEqualTo(expected);
        HobbyDTO card = hobbyService.getCategories(Role.ADMIN).get(0).getHobbies().stream()
                .filter(h -> h.getId() == 1L).findFirst().orElseThrow();
        assertThat(card.getImagePath()).isEqualTo(expected);

        hobbyService.applyHobbyImage(1L, null, true);
        assertThat(hobbyService.getHobby(1L, Role.ADMIN).getHeroImage()).doesNotStartWith("/hobbies/image-files/");
        assertThat(hobbyService.getHobbyImage(1L).uploaded()).isFalse();
    }
}
