package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.TimelineDTO;
import com.wanted.lmspractice.domain.work.dto.WorkFormDTO;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.policy.WorkLevelPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkFileRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] #19 일반 작품 등록·수정·공개 설정·회고·성장 타임라인 통합 테스트 (이슈 #19 테스트 항목)
 * [담당] 팀원 4 · #19   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. DB 변경은 테스트 트랜잭션이 롤백한다.
 *       업로드 파일은 build/test-uploads 에만 쓴다(시드 uploads/ 보호).
 *       시드: learner(색연필화(1) 입문 배지만 보유, 작품 1·2·3(최종)·5·6(일반)), learner2(작품 4), 취미 4 목공 = 준비 중, 8 = 비공개
 * 🟡 미학습 기술: @SpringBootTest, @TestPropertySource (작업분배 §5 허용 범위 + 업로드 폴더 분리)
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "file.upload-dir=build/test-uploads")
class GeneralWorkServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final Long COLORED_PENCIL = 1L;   // learner 입문 배지 보유
    private static final Long DESSERT = 3L;          // learner 배지 없음
    private static final Long WOODWORK = 4L;         // 준비 중
    private static final Long PHOTO = 8L;            // 비공개
    private static final Long LEARNER_GENERAL_WORK = 6L;
    private static final Long LEARNER2_REVIEW_ROUND = 4L;

    @Autowired
    private WorkService workService;
    @Autowired
    private GrowthService growthService;
    @Autowired
    private WorkLevelPolicy workLevelPolicy;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkRepository workRepository;
    @Autowired
    private WorkFileRepository workFileRepository;

    private Long learner() {
        return userRepository.findByLoginId("learner").orElseThrow().getId();
    }

    private List<MultipartFile> oneImage() {
        return List.of(new MockMultipartFile("images", "a.png", null, PNG));
    }

    private WorkFormDTO form(String title, Long hobbyId, CourseLevel level) {
        WorkFormDTO form = new WorkFormDTO();
        form.setTitle(title);
        form.setHobbyId(hobbyId);
        form.setLevel(level);
        form.setDescription("설명");
        return form;
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("정상 등록: 일반 작품·버전 1·이미지가 저장되고 기본은 비공개, 수강 상태는 그대로")
    void saveGeneralWork() {
        List<String> before = enrollmentService.getMyEnrollments(learner()).stream()
                .map(e -> e.getEnrollmentId() + ":" + e.getStatus()).toList();

        WorkFormDTO form = form("  새 연습  ", COLORED_PENCIL, CourseLevel.BASIC);
        form.setReflection("선이 덜 흔들렸다");
        Long workId = workService.saveGeneralWork(learner(), form, oneImage());

        Work work = workRepository.findById(workId).orElseThrow();
        assertThat(work.getKind()).isEqualTo(WorkKind.GENERAL);
        assertThat(work.getEnrollmentId()).isNull();
        assertThat(work.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(work.getPublishedAt()).isNull();
        assertThat(work.getReflection()).isEqualTo("선이 덜 흔들렸다");
        assertThat(workFileRepository.findByWorkVersionIdOrderBySortOrder(work.getDisplayVersionId())).hasSize(1);
        assertThat(workService.getGeneralWorkForm(learner(), workId).getTitle()).isEqualTo("새 연습");

        List<String> after = enrollmentService.getMyEnrollments(learner()).stream()
                .map(e -> e.getEnrollmentId() + ":" + e.getStatus()).toList();
        assertThat(after).isEqualTo(before);   // 완료 조건: 수료 상태에 영향 없음
    }

    @Test
    @DisplayName("공개로 등록하면 공개 시각이 기록된다(ck_works_published)")
    void savePublic() {
        WorkFormDTO form = form("공개 작품", COLORED_PENCIL, CourseLevel.BASIC);
        form.setVisibility(Visibility.PUBLIC);
        Long workId = workService.saveGeneralWork(learner(), form, oneImage());
        workRepository.flush();
        assertThat(workRepository.findById(workId).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("수준 선택지: 배지 없는 취미는 입문만, 입문 배지 취미도 입문까지")
    void allowedLevels() {
        assertThat(workLevelPolicy.allowedLevels(learner(), DESSERT)).containsExactly(CourseLevel.BASIC);
        assertThat(workLevelPolicy.allowedLevels(learner(), COLORED_PENCIL)).containsExactly(CourseLevel.BASIC);
        assertThat(workLevelPolicy.allowedLevels(learner(), null)).containsExactly(CourseLevel.BASIC);
    }

    @Test
    @DisplayName("예외: 중급 배지 없이 중급 선택 → LEVEL_TAG_NOT_ALLOWED")
    void levelNotAllowed() {
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(),
                form("중급 작품", COLORED_PENCIL, CourseLevel.INTERMEDIATE), oneImage()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.LEVEL_TAG_NOT_ALLOWED));
    }

    @Test
    @DisplayName("예외: 준비 중·비공개 취미는 학습자에게 보이지 않아 HOBBY_NOT_FOUND (새 노출 정책)")
    void hobbyNotOperating() {
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(),
                form("목공", WOODWORK, CourseLevel.BASIC), oneImage()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(),
                form("사진", PHOTO, CourseLevel.BASIC), oneImage()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_FOUND));
    }

    @Test
    @DisplayName("예외: 제목 누락 → INVALID_INPUT, 사진 없음(빈 파일 칸) → INVALID_IMAGE_COUNT")
    void titleAndImages() {
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(),
                form("  ", COLORED_PENCIL, CourseLevel.BASIC), oneImage()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        List<MultipartFile> emptyPart = List.of(new MockMultipartFile("images", "", null, new byte[0]));
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(),
                form("사진 없음", COLORED_PENCIL, CourseLevel.BASIC), emptyPart))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_IMAGE_COUNT));
    }

    @Test
    @DisplayName("예외: 남의 평가 회차를 참고 피드백으로 연결 → ACCESS_DENIED")
    void foreignReference() {
        WorkFormDTO form = form("참고", COLORED_PENCIL, CourseLevel.BASIC);
        form.setReferenceReviewId(LEARNER2_REVIEW_ROUND);
        assertThatThrownBy(() -> workService.saveGeneralWork(learner(), form, oneImage()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("수정: 이미 연결한 참고 피드백은 유지 가능, 사진을 고르지 않으면 기존 사진 유지, 타임라인 순서 불변")
    void editKeepsOrder() {
        List<Long> before = growthService.getTimeline(learner(), null).getItems().stream()
                .map(TimelineDTO.Item::getWorkId).toList();

        WorkFormDTO form = workService.getGeneralWorkForm(learner(), LEARNER_GENERAL_WORK);   // 시드: 참고 피드백 2 연결
        form.setTitle("색연필 정물 연습 (고침)");
        workService.updateGeneralWork(learner(), LEARNER_GENERAL_WORK, form, List.of());
        workRepository.flush();

        List<Long> after = growthService.getTimeline(learner(), null).getItems().stream()
                .map(TimelineDTO.Item::getWorkId).toList();
        assertThat(after).isEqualTo(before);
        Work work = workRepository.findById(LEARNER_GENERAL_WORK).orElseThrow();
        assertThat(work.getReferenceReviewId()).isEqualTo(2L);
        assertThat(workFileRepository.findByWorkVersionIdOrderBySortOrder(work.getDisplayVersionId())).hasSize(1);
    }

    @Test
    @DisplayName("수정 화면: 최종 과제·남의 작품은 WORK_NOT_FOUND")
    void editOnlyOwnGeneral() {
        assertThatThrownBy(() -> workService.getGeneralWorkForm(learner(), 1L))   // 최종 과제
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThatThrownBy(() -> workService.getGeneralWorkForm(learner(), 4L))   // learner2 작품
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }

    @Test
    @DisplayName("타임라인: 등록일 최신순, 취미 필터 시 첫/최근 작품, 전체 보기에서는 없음")
    void timeline() {
        TimelineDTO all = growthService.getTimeline(learner(), null);
        assertThat(all.getItems()).extracting(TimelineDTO.Item::getWorkId).containsExactly(3L, 6L, 2L, 5L, 1L);
        assertThat(all.getFirst()).isNull();
        assertThat(all.getHobbies()).extracting("id").containsExactly(1L, 3L);

        TimelineDTO pencil = growthService.getTimeline(learner(), COLORED_PENCIL);
        assertThat(pencil.getItems()).extracting(TimelineDTO.Item::getWorkId).containsExactly(6L, 2L, 1L);
        assertThat(pencil.getFirst().getWorkId()).isEqualTo(1L);
        assertThat(pencil.getLatest().getWorkId()).isEqualTo(6L);
        assertThat(pencil.getFirst().getThumbnailFileId()).isNotNull();
        assertThat(pencil.getSelectedHobbyName()).isEqualTo("색연필화");
    }

    @Test
    @DisplayName("회고: 저장·빈 값이면 삭제, 남의 작품은 WORK_NOT_FOUND, 500자 초과는 INVALID_INPUT")
    void reflection() {
        growthService.saveReflection(learner(), 5L, "  아이싱이 고르게 됐다  ");
        assertThat(workRepository.findById(5L).orElseThrow().getReflection()).isEqualTo("아이싱이 고르게 됐다");
        growthService.saveReflection(learner(), 5L, "   ");
        assertThat(workRepository.findById(5L).orElseThrow().getReflection()).isNull();

        assertThatThrownBy(() -> growthService.saveReflection(learner(), 4L, "남의 작품"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
        assertThatThrownBy(() -> growthService.saveReflection(learner(), 5L, "가".repeat(Work.MAX_REFLECTION_LENGTH + 1)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("공개 설정: 비공개 → 공개면 공개 시각 기록, 남의 작품은 WORK_NOT_FOUND")
    void visibility() {
        workService.changeVisibility(learner(), LEARNER_GENERAL_WORK, Visibility.PUBLIC);
        workRepository.flush();
        Work work = workRepository.findById(LEARNER_GENERAL_WORK).orElseThrow();
        assertThat(work.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(work.getPublishedAt()).isNotNull();

        assertThatThrownBy(() -> workService.changeVisibility(learner(), 4L, Visibility.PUBLIC))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }
}
