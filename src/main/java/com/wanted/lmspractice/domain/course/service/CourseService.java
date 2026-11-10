package com.wanted.lmspractice.domain.course.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.dto.CourseFormDTO;
import com.wanted.lmspractice.domain.course.dto.LessonDTO;
import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.repository.CourseRepository;
import com.wanted.lmspractice.domain.course.repository.LessonRepository;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강의·차시 조회 + 관리자 강의 편집(A-S06 콘텐츠 검수) — 강의 데이터만 다룬다(정원·대기·내 신청 상태는 CourseCatalogService가 수강 도메인에서 받아 붙인다)
 * [담당] 팀원 2 · #08   [화면] U-S03·U-S04, A-S15   [기획] D-08(비회원도 강의 상세까지 열람), D-59(강의는 시드 데이터 — 관리자는 기존 강의의 내용·공개 상태만 편집)
 *       [API] CO-01·CO-02
 * [연결]
 *  - 호출받음: CourseCatalogService(강의 목록·상세·관리자 집계·#10 관리자 강의 선택), #09 EnrollEligibilityPolicy(신청할 강의 확인)·SeatService(알림용 강의 이름),
 *             #10 SectionService(강의 확인·취미 id)
 *  - 호출함:   CourseRepository, LessonRepository, HobbyService(강의가 속한 취미를 이 사람이 볼 수 있는지 확인)
 *  - 규칙:     비공개 취미의 강의·닫힌(CLOSED) 강의는 관리자만 본다. 그 밖의 사람에게는 "없는 강의"로 응답해 존재를 숨긴다.
 *  - 의존 방향: course → hobby. 수강 도메인(EnrollmentService·SeatService)은 여기서 부르지 않는다 — #09의 신청 로직이
 *             강의·취미 확인에 이 서비스를 쓰게 되어도 서로를 주입하는 순환이 생기지 않게 하려는 것이다.
 * [기술] 🟢 @Service·@Transactional(readOnly)·Repository 쿼리 메서드
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final HobbyService hobbyService;

    /**
     * 한 세부 취미의 강의 목록(수준 순서). level이 있으면 그 수준만 (CO-01).
     * 호출: CourseCatalogService.getCourses·getHobbyDetail
     *
     * @param levelOrNull 수준 필터 — null이면 전체
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 취미이거나 보이지 않는 비공개 취미
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getCourses(Long hobbyId, CourseLevel levelOrNull, Role viewerRole) {
        HobbyDTO hobby = hobbyService.getHobbySummary(hobbyId, viewerRole);  // 노출 규칙 확인(없으면 HOBBY_NOT_FOUND)
        List<Course> courses = (levelOrNull == null)
                ? courseRepository.findByHobbyIdOrderByLevelAsc(hobbyId)
                : courseRepository.findByHobbyIdAndLevelOrderByLevelAsc(hobbyId, levelOrNull);
        return courses.stream()
                .filter(course -> isVisible(course, viewerRole))
                .map(course -> CourseDTO.from(course, hobby.getCode()))
                .toList();
    }

    /**
     * 강의 상세(차시 포함, 정원·대기는 아직 비어 있음). U-S04.
     * 호출: CourseCatalogService.getCourseDetail ← CourseController.detail (GET /courses/{courseId}, API CO-02)
     *
     * @throws BusinessException COURSE_NOT_FOUND — 없는 강의, 닫힌 강의, 또는 볼 수 없는 취미의 강의
     */
    @Transactional(readOnly = true)
    public CourseDetailDTO getCourseDetail(Long courseId, Role viewerRole) {
        Course course = courseRepository.findById(courseId)
                .filter(found -> isVisible(found, viewerRole))
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        HobbyDTO hobby;
        try {
            hobby = hobbyService.getHobbySummary(course.getHobbyId(), viewerRole);
        } catch (BusinessException e) {
            // 비공개 취미의 강의는 취미가 아니라 "강의"를 못 찾는 것으로 응답한다 (API CO-02 실패: COURSE_NOT_FOUND)
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        List<LessonDTO> lessons = lessonRepository.findByCourseIdOrderByLessonNoAsc(courseId).stream()
                .map(LessonDTO::from)
                .toList();
        return CourseDetailDTO.from(course, hobby.getName(), lessons);
    }

    /**
     * 수강 중인 학습자가 학습 화면에서 쓰는 강의 정보 — 취미의 노출 상태(준비 중·비공개)로 걸러내지 않는다.
     * 취미가 준비 중으로 바뀌어도 기존 수강자의 학습·과제 접근은 유지해야 하므로(D-20), 신규 탐색용 getCourseDetail 의
     * 노출 규칙(준비 중은 강사·관리자에게만)을 거치지 않는다. 닫힌(CLOSED) 강의는 기존처럼 찾지 못한다.
     * 호출: ProgressService.getLessonView (GET /lessons/{lessonId}) — 수강 건 소유 확인은 호출한 쪽이 이미 끝냈다.
     *
     * @throws BusinessException COURSE_NOT_FOUND — 없는 강의 또는 닫힌 강의
     */
    @Transactional(readOnly = true)
    public CourseDetailDTO getCourseDetailForEnrolled(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(found -> isVisible(found, Role.USER))
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        HobbyDTO hobby = hobbyService.getHobbiesIgnoringVisibility(List.of(course.getHobbyId())).get(course.getHobbyId());
        if (hobby == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        List<LessonDTO> lessons = lessonRepository.findByCourseIdOrderByLessonNoAsc(courseId).stream()
                .map(LessonDTO::from)
                .toList();
        return CourseDetailDTO.from(course, hobby.getName(), lessons);
    }

    /**
     * 강의 여러 개의 한 줄 소개(강의명·수준·취미 이름·취미 운영 여부)를 노출 규칙 없이 읽는다 — 본인 수강 기록 화면용.
     * 준비 중으로 바뀐 취미·닫힌 강의도 이미 수강 중이면 계속 보여야 하므로(D-20) 걸러내지 않는다.
     * 호출: MyLearningService.getMyLearning (GET /my/learning, API ML-01)
     *
     * @return 강의 id → CourseBriefDTO (없는 id는 빠진다)
     */
    @Transactional(readOnly = true)
    public Map<Long, CourseBriefDTO> getCourseBriefs(Collection<Long> courseIds) {
        List<Course> courses = courseRepository.findAllById(courseIds);
        Map<Long, HobbyDTO> hobbies = hobbyService.getHobbiesIgnoringVisibility(
                courses.stream().map(Course::getHobbyId).distinct().toList());
        return courses.stream()
                .filter(course -> hobbies.containsKey(course.getHobbyId()))
                .collect(Collectors.toMap(Course::getId, course -> {
                    HobbyDTO hobby = hobbies.get(course.getHobbyId());
                    return CourseBriefDTO.from(course, hobby.getName(), hobby.isOperating());
                }));
    }

    /**
     * 관리자용 전체 강의 목록(닫힌 강의·비공개 취미 포함) — 취미·수준 순서 (#10 SE-01·WL-01 모델 courses, 강의 선택용).
     * 호출: CourseCatalogService.getAdminCourses
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getAllCoursesForAdmin() {
        Map<Long, String> hobbyCodes = new HashMap<>();  // 같은 취미를 여러 번 조회하지 않도록
        return courseRepository.findAllByOrderByHobbyIdAscLevelAsc().stream()
                .map(course -> CourseDTO.from(course, hobbyCodes.computeIfAbsent(course.getHobbyId(),
                        hobbyId -> hobbyService.getHobbySummary(hobbyId, Role.ADMIN).getCode())))
                .toList();
    }

    /**
     * 관리자 취미 조회(A-S15)용 강의·차시 수 집계를 세부 취미 DTO에 채운다 (공개/전체 강의 수, 차시 수).
     * 호출: CourseCatalogService.getAdminCategories
     */
    @Transactional(readOnly = true)
    public void fillCourseCounts(HobbyDTO hobby) {
        List<Long> courseIds = courseRepository.findByHobbyIdOrderByLevelAsc(hobby.getId()).stream()
                .map(Course::getId)
                .toList();
        hobby.setTotalCourseCount((int) courseRepository.countByHobbyId(hobby.getId()));
        hobby.setOpenCourseCount((int) courseRepository.countByHobbyIdAndStatus(hobby.getId(), CourseStatus.OPEN));
        hobby.setLessonCount(courseIds.isEmpty() ? 0 : (int) lessonRepository.countByCourseIdIn(courseIds));
    }

    /**
     * [#12 요청] 차시 한 건 조회 — 영상 길이·영상 경로가 필요한 #12(ProgressService)가 부른다.
     * 팀원 2 소유 파일에 팀원 3이 추가한 최소 메서드(계약 추가 요청 — 확인·수정은 팀원 2).
     * 호출: ProgressService (재생 보고, 영상 접근, 차시 화면)
     * @throws BusinessException LESSON_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Lesson getLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
    }

    /**
     * [#12 요청] 한 강의의 차시 목록(1·2·3차시 순서). 호출: ProgressService (차시 목록·3차시 완료 판정)
     * 팀원 2 소유 파일에 팀원 3이 추가한 최소 메서드(계약 추가 요청).
     */
    @Transactional(readOnly = true)
    public List<Lesson> getLessonsOfCourse(Long courseId) {
        return lessonRepository.findByCourseIdOrderByLessonNoAsc(courseId);
    }

    /**
     * [성능 개선] 여러 강의의 차시 목록을 한 번에 (강의마다 getLessonsOfCourse와 같은 1·2·3차시 순서).
     * 호출: MyLearningService.getMyLearning — 수강 카드마다 차시를 따로 읽지 않도록
     *
     * @return 강의 id → 차시 목록 (요청한 모든 id가 들어 있고, 차시가 없는 강의는 빈 목록)
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Lesson>> getLessonsOfCourses(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Lesson>> lessonsByCourse = new HashMap<>();
        courseIds.forEach(courseId -> lessonsByCourse.put(courseId, new ArrayList<>()));
        for (Lesson lesson : lessonRepository.findByCourseIdInOrderByCourseIdAscLessonNoAsc(courseIds)) {
            lessonsByCourse.computeIfAbsent(lesson.getCourseId(), id -> new ArrayList<>()).add(lesson);
        }
        return lessonsByCourse;
    }

    /**
     * 강의 이름만 — 노출 규칙 없이 id로 찾는다(이미 수강·대기 중인 사람에게 보내는 안내 문구용).
     * 호출: SeatService.fillVacancies (#09 대기 자동 확정 알림 본문)
     * @throws BusinessException COURSE_NOT_FOUND — 없는 강의
     */
    @Transactional(readOnly = true)
    public String getCourseTitle(Long courseId) {
        return courseRepository.findById(courseId)
                .map(Course::getTitle)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    /**
     * [#13 요청] 강의의 최종 과제 제목만 — 노출 규칙 없이 id로 찾는다(이미 수강 중인 학습자의 과제 화면용).
     * 팀원 2 소유 파일에 팀원 3이 추가한 최소 메서드(계약 추가 요청 — 확인·수정은 팀원 2). getCourseTitle과 같은 방식.
     * 호출: AssignmentService.getAssignmentView (AS-01 제목 칸 기본값 = 강의의 과제 제목)
     * @throws BusinessException COURSE_NOT_FOUND — 없는 강의
     */
    @Transactional(readOnly = true)
    public String getAssignmentTitle(Long courseId) {
        return courseRepository.findById(courseId)
                .map(Course::getAssignmentTitle)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    /** 관리자는 닫힌 강의도 보고, 그 밖의 사람은 열린(OPEN) 강의만 본다 */
    private boolean isVisible(Course course, Role viewerRole) {
        return course.getStatus() == CourseStatus.OPEN || viewerRole == Role.ADMIN;
    }

    // ===== 관리자 강의 편집 (A-S06 콘텐츠 검수) =====

    /** 강의 id → 공개 상태 (콘텐츠 검수 표의 "검수 상태" 칸). 호출: AdminHobbyController.contentReview */
    @Transactional(readOnly = true)
    public Map<Long, CourseStatus> getCourseStatuses() {
        Map<Long, CourseStatus> statuses = new HashMap<>();
        courseRepository.findAll().forEach(course -> statuses.put(course.getId(), course.getStatus()));
        return statuses;
    }

    /**
     * 편집할 강의의 현재 값 (편집 패널을 미리 채운다). 호출: AdminHobbyController.contentReview(?edit=)
     *
     * @throws BusinessException COURSE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public CourseFormDTO getCourseForm(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        return new CourseFormDTO(course.getTitle(), course.getDescription(), course.getSuppliesText(),
                course.getAssignmentTitle(), course.getAssignmentDescription(), course.getStatus());
    }

    /**
     * 강의 편집 (A-S06). 비공개(CLOSED)로 바꾸면 학습자·비회원 목록에서 빠진다(관리자만 봄). 이미 수강 중인 학습자의 학습은 그대로.
     * 호출: AdminHobbyController.updateCourse
     *
     * @return 바꾼 뒤 강의명(안내 문구용)
     * @throws BusinessException COURSE_NOT_FOUND
     */
    @Transactional
    public String updateCourse(Long courseId, CourseFormDTO form) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        course.update(CourseFormDTO.trimmed(form.getTitle()), CourseFormDTO.trimmed(form.getDescription()),
                CourseFormDTO.trimmed(form.getSuppliesText()), CourseFormDTO.trimmed(form.getAssignmentTitle()),
                CourseFormDTO.trimmed(form.getAssignmentDescription()), form.getStatus());
        return course.getTitle();
    }
}
