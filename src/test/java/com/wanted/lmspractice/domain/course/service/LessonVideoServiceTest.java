package com.wanted.lmspractice.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.repository.CourseRepository;
import com.wanted.lmspractice.domain.course.repository.LessonRepository;
import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.service.SectionService;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import com.wanted.lmspractice.global.file.UploadedVideo;
import com.wanted.lmspractice.global.file.VideoStorage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] LessonVideoService 단위 테스트 — 강사 범위(자기 영상·권한 취미), 연결 시 경로·길이 변경, 연결된 영상 삭제 거부
 * [전제] 스프링·DB 없이 Mockito로 저장소·서비스를 대신한다(공용 DB를 건드리지 않는다).
 * 🟡 미학습 기술: Mockito mock·verify (테스트 전용)
 */
class LessonVideoServiceTest {

    CourseRepository courseRepository;
    LessonRepository lessonRepository;
    HobbyService hobbyService;
    InstructorAccountService instructorAccountService;
    VideoStorage videoStorage;
    ProgressService progressService;
    SectionService sectionService;
    LessonVideoService service;

    Lesson lesson;
    Course course;

    static final String ADMIN_KEY = "videos/uploaded/a_12345678.mp4";
    static final String T6_KEY = "videos/uploaded/instructor-6/b_12345678.mp4";

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        lessonRepository = mock(LessonRepository.class);
        hobbyService = mock(HobbyService.class);
        instructorAccountService = mock(InstructorAccountService.class);
        videoStorage = mock(VideoStorage.class);
        progressService = mock(ProgressService.class);
        sectionService = mock(SectionService.class);
        service = new LessonVideoService(courseRepository, lessonRepository, hobbyService, instructorAccountService, videoStorage,
                progressService, sectionService);

        lesson = mock(Lesson.class);
        when(lesson.getCourseId()).thenReturn(1L);
        when(lesson.getLessonNo()).thenReturn(1);
        when(lessonRepository.findById(3L)).thenReturn(Optional.of(lesson));

        course = mock(Course.class);
        when(course.getId()).thenReturn(1L);
        when(course.getHobbyId()).thenReturn(10L);
        when(course.getLevel()).thenReturn(CourseLevel.values()[0]);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        HobbyDTO hobby = mock(HobbyDTO.class);
        when(hobby.getName()).thenReturn("색연필화");
        when(hobbyService.getHobbiesIgnoringVisibility(any())).thenReturn(Map.of(10L, hobby));

        when(videoStorage.ownerOf(ADMIN_KEY)).thenReturn(null);
        when(videoStorage.ownerOf(T6_KEY)).thenReturn(6L);
        when(videoStorage.find(ADMIN_KEY)).thenReturn(video(ADMIN_KEY, null, 600.0));
        when(videoStorage.find(T6_KEY)).thenReturn(video(T6_KEY, 6L, 30.0));
    }

    private UploadedVideo video(String key, Long owner, Double duration) {
        return new UploadedVideo(key, key.substring(key.lastIndexOf('/') + 1), owner, 100, duration, LocalDateTime.now());
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("관리자 연결: 경로와 실제 영상 길이(소수 3자리)를 함께 바꾸고 차시 이름을 돌려준다")
    void adminLinks() {
        String label = service.linkVideo(ADMIN_KEY, 3L, null);

        verify(lesson).changeVideo(ADMIN_KEY, new BigDecimal("600.000"));
        verify(progressService).applyLessonDuration(3L, new BigDecimal("600.000"));   // 기존 학습자 진도를 새 길이에 맞춘다
        assertThat(label).startsWith("색연필화 · ").endsWith(" · 1차시");
    }

    /** 강사가 담당하는 분반들 — 분반마다 강의 id 만 쓴다 */
    private void assignSections(Long instructorId, Long... courseIds) {
        List<SectionDTO> sections = java.util.Arrays.stream(courseIds).map(courseId -> {
            SectionDTO section = mock(SectionDTO.class);
            when(section.getCourseId()).thenReturn(courseId);
            return section;
        }).toList();
        when(sectionService.getSectionsByInstructor(instructorId)).thenReturn(sections);
    }

    @Test
    @DisplayName("강사 연결: 담당 강의 + 권한 받은 취미면 연결, 취미 권한이 없으면 VIDEO_LINK_DENIED")
    void instructorNeedsGrant() {
        assignSections(6L, 1L);
        when(instructorAccountService.hasHobbyGrant(6L, 10L)).thenReturn(true);
        service.linkVideo(T6_KEY, 3L, 6L);
        verify(lesson).changeVideo(T6_KEY, new BigDecimal("30.000"));

        when(instructorAccountService.hasHobbyGrant(6L, 10L)).thenReturn(false);
        assertThatThrownBy(() -> service.linkVideo(T6_KEY, 3L, 6L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VIDEO_LINK_DENIED));
    }

    @Test
    @DisplayName("강사 연결: 취미 권한이 있어도 내가 담당하지 않는 강의(같은 취미의 다른 수준)의 차시는 VIDEO_LINK_DENIED, 목록에도 없다")
    void instructorOnlyAssignedCourses() {
        assignSections(6L, 2L);                                   // 같은 취미(10)의 강의 2만 담당 — 차시 3은 강의 1 소속
        when(instructorAccountService.hasHobbyGrant(6L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.linkVideo(T6_KEY, 3L, 6L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VIDEO_LINK_DENIED));
        verify(lesson, never()).changeVideo(anyString(), any());

        Course other = mock(Course.class);
        when(other.getId()).thenReturn(2L);
        when(other.getHobbyId()).thenReturn(10L);
        when(other.getLevel()).thenReturn(CourseLevel.values()[1]);
        when(courseRepository.findAllByOrderByHobbyIdAscLevelAsc()).thenReturn(List.of(course, other));
        Lesson otherLesson = mock(Lesson.class);
        when(otherLesson.getId()).thenReturn(4L);
        when(otherLesson.getLessonNo()).thenReturn(1);
        when(lesson.getId()).thenReturn(3L);
        when(lessonRepository.findByCourseIdOrderByLessonNoAsc(1L)).thenReturn(List.of(lesson));
        when(lessonRepository.findByCourseIdOrderByLessonNoAsc(2L)).thenReturn(List.of(otherLesson));

        assertThat(service.getLessonOptions(6L)).extracting(o -> o.getLessonId()).containsExactly(4L);
        assertThat(service.getLessonOptions(null)).extracting(o -> o.getLessonId()).containsExactly(3L, 4L);   // 관리자는 전체
    }

    @Test
    @DisplayName("강사는 남의 영상(관리자·다른 강사)을 연결·미리보기·삭제할 수 없다 — NOT_FOUND로 존재를 숨긴다")
    void instructorOnlyOwnVideos() {
        when(instructorAccountService.hasHobbyGrant(7L, 10L)).thenReturn(true);
        for (String key : new String[]{ADMIN_KEY, T6_KEY}) {
            assertThatThrownBy(() -> service.linkVideo(key, 3L, 7L))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
            assertThatThrownBy(() -> service.loadForPreview(key, 7L))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
            assertThatThrownBy(() -> service.delete(key, 7L))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
        }
        verify(lesson, never()).changeVideo(anyString(), any());
        verify(videoStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("길이를 읽지 못한 영상은 VIDEO_DURATION_UNKNOWN, 없는 차시는 LESSON_NOT_FOUND")
    void linkFailures() {
        when(videoStorage.find(ADMIN_KEY)).thenReturn(video(ADMIN_KEY, null, null));
        assertThatThrownBy(() -> service.linkVideo(ADMIN_KEY, 3L, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VIDEO_DURATION_UNKNOWN));

        when(videoStorage.find(ADMIN_KEY)).thenReturn(video(ADMIN_KEY, null, 600.0));
        when(lessonRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.linkVideo(ADMIN_KEY, 99L, null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.LESSON_NOT_FOUND));
        verify(lesson, never()).changeVideo(anyString(), any());
    }

    @Test
    @DisplayName("차시에 연결된 영상은 VIDEO_IN_USE로 삭제를 거부하고, 연결이 없으면 지운다")
    void deleteGuard() {
        when(lessonRepository.existsByVideoKey(T6_KEY)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(T6_KEY, 6L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VIDEO_IN_USE));
        verify(videoStorage, never()).delete(anyString());

        when(lessonRepository.existsByVideoKey(T6_KEY)).thenReturn(false);
        service.delete(T6_KEY, 6L);
        verify(videoStorage).delete(T6_KEY);
    }
}
