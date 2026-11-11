package com.wanted.lmspractice.domain.course.service;

import com.wanted.lmspractice.domain.course.dto.LessonVideoOptionDTO;
import com.wanted.lmspractice.domain.course.entity.Course;
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
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 관리자·강사 영상 업로드 — 올리기·목록·미리보기·삭제 + 차시 연결(lessons.video_key·duration_sec 변경)
 *   - 관리자(instructorId = null): 모든 영상·모든 차시
 *   - 강사(instructorId): 자기가 올린 영상만, 자기가 담당하는 강의(담당 분반이 있는 강의)의 차시에만 연결 — 취미 활동 권한(계약 C3)도 함께 확인.
 *     같은 취미의 다른 강사 강의 차시는 권한이 있어도 바꿀 수 없다(팀 결정 2026-11-11)
 *   - 새 테이블 없음: 영상 파일은 폴더(VideoStorage), 연결 정보는 기존 lessons 표의 video_key 칸
 * [연결]
 *  - 호출받음: AdminVideoController(/admin/videos), InstructorVideoController(/instructor/videos)
 *  - 호출함:   VideoStorage(파일), LessonRepository·CourseRepository(같은 도메인), HobbyService(취미 이름), InstructorAccountService.hasHobbyGrant(C3),
 *             SectionService.getSectionsByInstructor(계약 C6 — 강사의 담당 분반 → 담당 강의),
 *             ProgressService.applyLessonDuration(영상 연결로 길이가 바뀌면 기존 학습자 진도를 새 길이에 맞춤)
 * ❓ D-59(영상은 개발자가 폴더에 넣고 시드에 적는다)와 다른 기능 — 팀 공유 필요. 시드를 다시 적재하면 연결은 시드 값으로 돌아간다.
 * [기술] 🟢 @Service·@Transactional(변경 감지로 저장), 🟢 Stream
 */
@Service
@RequiredArgsConstructor
public class LessonVideoService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final HobbyService hobbyService;
    private final InstructorAccountService instructorAccountService;
    private final VideoStorage videoStorage;
    private final ProgressService progressService;
    private final SectionService sectionService;

    /** 영상 목록 — 관리자는 전부, 강사는 자기가 올린 것만 */
    public List<UploadedVideo> getVideos(Long instructorIdOrNull) {
        return instructorIdOrNull == null ? videoStorage.listAll() : videoStorage.listOf(instructorIdOrNull);
    }

    /**
     * 연결할 수 있는 차시 목록(취미·수준·차시 순서) — 관리자는 전체, 강사는 담당 강의(활동 권한이 있는 취미)의 차시만.
     */
    @Transactional(readOnly = true)
    public List<LessonVideoOptionDTO> getLessonOptions(Long instructorIdOrNull) {
        List<Course> courses = courseRepository.findAllByOrderByHobbyIdAscLevelAsc();
        if (instructorIdOrNull != null) {
            Set<Long> assigned = assignedCourseIds(instructorIdOrNull);
            Map<Long, Boolean> granted = new HashMap<>();   // 같은 취미를 여러 번 묻지 않도록
            courses = courses.stream()
                    .filter(c -> assigned.contains(c.getId()))
                    .filter(c -> granted.computeIfAbsent(c.getHobbyId(),
                            hobbyId -> instructorAccountService.hasHobbyGrant(instructorIdOrNull, hobbyId)))
                    .toList();
        }
        Set<Long> hobbyIds = courses.stream().map(Course::getHobbyId).collect(Collectors.toSet());
        Map<Long, HobbyDTO> hobbies = hobbyIds.isEmpty() ? Map.of() : hobbyService.getHobbiesIgnoringVisibility(hobbyIds);

        List<LessonVideoOptionDTO> options = new ArrayList<>();
        for (Course course : courses) {
            HobbyDTO hobby = hobbies.get(course.getHobbyId());
            String hobbyName = hobby == null ? "취미 #" + course.getHobbyId() : hobby.getName();
            for (Lesson lesson : lessonRepository.findByCourseIdOrderByLessonNoAsc(course.getId())) {
                options.add(new LessonVideoOptionDTO(lesson.getId(), course.getHobbyId(), hobbyName,
                        course.getLevel().getLabel(), lesson.getLessonNo(), lesson.getVideoKey(),
                        lesson.getDurationSec() == null ? null : lesson.getDurationSec().doubleValue()));
            }
        }
        return options;
    }

    /**
     * 업로드 영상 key → 그 영상을 쓰는 차시 이름들 (영상 목록의 "연결된 차시" 칸, 연결된 영상은 삭제 버튼을 막는다).
     * 강사 화면이어도 전체 차시 기준으로 본다 — 관리자가 강사 영상을 다른 취미에 연결했을 수도 있어서.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getLinkedLessonLabels() {
        Map<String, List<String>> linked = new LinkedHashMap<>();
        for (LessonVideoOptionDTO option : getLessonOptions(null)) {
            if (option.isUploadedVideo()) {
                linked.computeIfAbsent(option.getVideoKey(), k -> new ArrayList<>()).add(option.getLabel());
            }
        }
        return linked;
    }

    /** 업로드 — 강사 파일은 강사 폴더(videos/uploaded/instructor-{id}/)에 저장 */
    public UploadedVideo upload(MultipartFile file, Long instructorIdOrNull) {
        return videoStorage.store(file, instructorIdOrNull);
    }

    /** 미리보기 파일 경로 — 강사는 자기 파일만 (남의 파일은 존재를 알리지 않고 NOT_FOUND) */
    public Path loadForPreview(String videoKey, Long instructorIdOrNull) {
        requireAccessible(videoKey, instructorIdOrNull);
        return videoStorage.load(videoKey);
    }

    /**
     * 차시에 영상 연결 — lessons.video_key 와 duration_sec(실제 영상 길이)를 바꾼다. 이전 영상 파일은 지우지 않는다.
     * @return 연결한 차시 이름 (예: 색연필화 · 입문 · 1차시)
     * @throws BusinessException NOT_FOUND(영상 없음·남의 영상), VIDEO_DURATION_UNKNOWN, LESSON_NOT_FOUND, VIDEO_LINK_DENIED(담당하지 않는 강의·권한 없는 취미)
     */
    @Transactional
    public String linkVideo(String videoKey, Long lessonId, Long instructorIdOrNull) {
        requireAccessible(videoKey, instructorIdOrNull);
        UploadedVideo video = videoStorage.find(videoKey);
        if (video.getDurationSec() == null) {
            throw new BusinessException(ErrorCode.VIDEO_DURATION_UNKNOWN);
        }
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (instructorIdOrNull != null && (!assignedCourseIds(instructorIdOrNull).contains(course.getId())
                || !instructorAccountService.hasHobbyGrant(instructorIdOrNull, course.getHobbyId()))) {
            throw new BusinessException(ErrorCode.VIDEO_LINK_DENIED);   // 같은 취미라도 남이 담당하는 강의의 차시는 바꿀 수 없다
        }

        BigDecimal newDuration = BigDecimal.valueOf(video.getDurationSec()).setScale(3, RoundingMode.HALF_UP);
        lesson.changeVideo(videoKey, newDuration);   // 변경 감지로 UPDATE
        // 길이가 달라진 영상이면 이미 본 학습자들의 진도를 새 길이에 맞춘다(짧아져서 60%를 넘은 차시는 바로 완료).
        // 맞추지 않으면 "100%인데 미완료"로 남아 학습자가 다시 재생해야만 완료됐다(체크리스트 ④ 검증)
        progressService.applyLessonDuration(lessonId, newDuration);

        HobbyDTO hobby = hobbyService.getHobbiesIgnoringVisibility(Set.of(course.getHobbyId())).get(course.getHobbyId());
        String hobbyName = hobby == null ? "취미 #" + course.getHobbyId() : hobby.getName();
        return hobbyName + " · " + course.getLevel().getLabel() + " · " + lesson.getLessonNo() + "차시";
    }

    /**
     * 삭제 — 강사는 자기 파일만. 차시에 연결된 영상은 지우지 않는다(학습자 재생이 404가 되므로).
     * @throws BusinessException NOT_FOUND, VIDEO_IN_USE
     */
    @Transactional(readOnly = true)
    public void delete(String videoKey, Long instructorIdOrNull) {
        requireAccessible(videoKey, instructorIdOrNull);
        if (lessonRepository.existsByVideoKey(videoKey)) {
            throw new BusinessException(ErrorCode.VIDEO_IN_USE);
        }
        videoStorage.delete(videoKey);
    }

    /** 강사가 담당하는 강의 id — 지금 그 강사가 담당 강사로 지정된 분반이 하나라도 있는 강의 (담당 공백 분반·이전 담당은 제외) */
    private Set<Long> assignedCourseIds(Long instructorId) {
        return sectionService.getSectionsByInstructor(instructorId).stream()
                .map(SectionDTO::getCourseId)
                .collect(Collectors.toSet());
    }

    /** 업로드 영상 key 인지 확인하고, 강사면 자기 폴더 파일인지 확인한다 */
    private void requireAccessible(String videoKey, Long instructorIdOrNull) {
        Long owner = videoStorage.ownerOf(videoKey);   // 규칙에 안 맞는 key 는 여기서 NOT_FOUND
        if (instructorIdOrNull != null && !instructorIdOrNull.equals(owner)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
