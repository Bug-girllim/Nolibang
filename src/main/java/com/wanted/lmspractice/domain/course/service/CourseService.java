package com.wanted.lmspractice.domain.course.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.dto.LessonDTO;
import com.wanted.lmspractice.domain.course.entity.Course;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.CourseStatus;
import com.wanted.lmspractice.domain.course.repository.CourseRepository;
import com.wanted.lmspractice.domain.course.repository.LessonRepository;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강의·차시 조회 — 강의 데이터만 다룬다(정원·대기·내 신청 상태는 CourseCatalogService가 수강 도메인에서 받아 붙인다)
 * [담당] 팀원 2 · #08   [화면] U-S03·U-S04, A-S15   [기획] D-08(비회원도 강의 상세까지 열람), D-59(강의는 시드 데이터라 조회만)
 *       [API] CO-01·CO-02
 * [연결]
 *  - 호출받음: CourseCatalogService(강의 목록·상세·관리자 집계)
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

    /** 관리자는 닫힌 강의도 보고, 그 밖의 사람은 열린(OPEN) 강의만 본다 */
    private boolean isVisible(Course course, Role viewerRole) {
        return course.getStatus() == CourseStatus.OPEN || viewerRole == Role.ADMIN;
    }
}
