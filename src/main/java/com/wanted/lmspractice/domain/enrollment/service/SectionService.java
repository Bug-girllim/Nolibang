package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SectionFormDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.SectionQuery;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.user.dto.InstructorDTO;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자 분반 관리 — 분반 생성, 정원 변경(1~30, 활성 수강 미만 축소 불가), 담당 강사 지정·변경 + 분반 조회 계약(C6)
 *        자리를 만드는 "운영 측 트리거"(분반 생성·정원 증가·담당 공백 해소)는 모두 SeatService.fillVacancies로 넘긴다.
 * [담당] 팀원 2 · #10   [화면] A-S05   [기획] FR-052, D-52, D-59   [API] SE-01~04
 * [연결]
 *  - 호출받음: AdminSectionController(SE-01~04), AdminWaitlistController(WL-01 분반별 남은 자리),
 *             AdminInstructorController(#05, 담당 분반 수 — C6, 강사 비활성화·권한 회수 — deactivateInstructor·revokeHobbyGrant),
 *             팀원 1 #07(C6 getSectionsByInstructor),
 *             팀원 3 #14·팀원 4(C6 getCurrentInstructorId — 평가 권한·작품 열람·문의)
 *  - 호출함:   CourseSectionRepository(잠금·저장), EnrollmentRepository(활성 수강 수), SectionQuery(현황 조회),
 *             CourseService(강의 확인·취미 id), InstructorAccountService(강사 확인·C3 hasHobbyGrant),
 *             SeatService.fillVacancies(C4, #09 — 같은 트랜잭션), NotificationService.notify(C7 SECTION_ASSIGNED)
 *  - 계약:     C6 getCurrentInstructorId(Long), getSectionsByInstructor(Long) — 시그니처 변경 금지
 *  - 강사 교체: 수강 건을 옮기지 않는다(D-59). 평가 권한은 #14가 "현재 분반 담당자"로 판단하므로 별도 이관 처리가 없다.
 * [기술] 🟢 @Service·@Transactional, 🟢 native SELECT … FOR UPDATE(강의 분반 행 잠금)
 */
@Service
@RequiredArgsConstructor
public class SectionService {

    private final CourseSectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SectionQuery sectionQuery;
    private final CourseService courseService;
    private final InstructorAccountService instructorAccountService;
    private final SeatService seatService;
    private final NotificationService notificationService;

    // ───────────────────────── 조회 ─────────────────────────

    /**
     * 한 강의의 분반 목록 + "다음 배정 분반" 표시 (SE-01 모델 sections, WL-01 분반별 남은 자리).
     * 호출: AdminSectionController.list, AdminWaitlistController.list
     *
     * @throws BusinessException COURSE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public List<SectionDTO> getSectionsForAdmin(Long courseId) {
        findCourse(courseId);
        List<SectionDTO> sections = sectionQuery.findByCourseId(courseId);
        markNextAssign(sections);
        return sections;
    }

    /**
     * 분반 하나 (화면 이동에 필요한 강의 id, 정원 변경 전 확인용).
     * 호출: AdminSectionController.changeCapacity·changeInstructor
     *
     * @throws BusinessException SECTION_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public SectionDTO getSection(Long sectionId) {
        SectionDTO section = sectionQuery.findById(sectionId);
        if (section == null) {
            throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        }
        return section;
    }

    /**
     * 이 강의의 담당으로 지정할 수 있는 강사 — 활성 계정이고 강의 취미 활동 권한이 있는 강사 (SE-01 모델 candidates).
     * 시안 안내: "비활성·취미 권한 없는 강사는 선택할 수 없어요."
     * 호출: AdminSectionController.list
     *
     * @throws BusinessException COURSE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public List<InstructorDTO> getCandidates(Long courseId) {
        Long hobbyId = findCourse(courseId).getHobbyId();
        return instructorAccountService.getInstructors(null).stream()
                .filter(InstructorDTO::isActive)
                .filter(instructor -> instructor.hasHobby(hobbyId))
                .toList();
    }

    /** 강의 안에서 이 분반 이름을 쓸 수 있는지 — 비어 있으면 번호로 표시하므로 항상 가능. 호출: AdminSectionController.create(BindingResult) */
    @Transactional(readOnly = true)
    public boolean isNameAvailable(Long courseId, String trimmedName) {
        return trimmedName == null || !sectionRepository.existsByCourseIdAndName(courseId, trimmedName);
    }

    /**
     * 정원 변경 전 확인 — 문제가 있으면 그 ErrorCode를 돌려준다(화면이 같은 자리에서 안내하도록, SE-03 400·409 → flash).
     * 실제 변경(changeCapacity)에서도 잠금 후 같은 검사를 다시 한다(그 사이 수강이 늘었을 수 있음).
     * 호출: AdminSectionController.changeCapacity
     */
    @Transactional(readOnly = true)
    public Optional<ErrorCode> checkCapacity(Long sectionId, int capacity) {
        if (!isCapacityInRange(capacity)) {
            return Optional.of(ErrorCode.CAPACITY_OUT_OF_RANGE);
        }
        if (capacity < enrollmentRepository.countBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE)) {
            return Optional.of(ErrorCode.CAPACITY_BELOW_ACTIVE);
        }
        return Optional.empty();
    }

    // ───────────────────────── 변경 ─────────────────────────

    /**
     * 분반 생성 (SE-02) — 강의 분반 행 잠금 → 강사 확인 → 다음 번호로 저장 → 새 담당 강사 알림 → 새 자리만큼 대기자 자동 확정.
     * 호출: AdminSectionController.create (POST /admin/sections)
     *
     * @return 새 분반 id
     * @throws BusinessException COURSE_NOT_FOUND, CAPACITY_OUT_OF_RANGE, USER_NOT_FOUND, INSTRUCTOR_NOT_GRANTED,
     *                           INVALID_INPUT(같은 강의에 같은 분반 이름 — ❓ 전용 ErrorCode 요청 중)
     */
    @Transactional
    public Long create(Long courseId, SectionFormDTO dto) {
        CourseDetailDTO course = findCourse(courseId);
        sectionRepository.findByCourseIdForUpdate(courseId);  // 번호 중복·자동 확정 동시 처리 방지 — 잠금 후에 번호를 정한다
        checkAssignable(dto.getInstructorId(), course.getHobbyId());

        String name = dto.getTrimmedName();
        if (!isNameAvailable(courseId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);  // ❓ DUPLICATE_SECTION_NAME 추가 요청(팀원 1)
        }
        int capacity = dto.getCapacity() == null ? 0 : dto.getCapacity();
        CourseSection section = CourseSection.create(
                courseId, sectionRepository.findNextSectionNo(courseId), name, dto.getInstructorId(), capacity);
        // fillVacancies가 native 쿼리로 남은 자리를 읽으므로 새 분반 행을 먼저 DB에 반영한다
        sectionRepository.saveAndFlush(section);

        notifyAssigned(section, course.getTitle());
        seatService.fillVacancies(courseId);  // SE-02: 대기자가 있으면 새 자리만큼 자동 확정 (C4, 같은 트랜잭션)
        return section.getId();
    }

    /**
     * 정원 변경 (SE-03) — 1~30, 현재 활성 수강 수 이상. 늘어나면 늘어난 자리만큼 대기 1순위부터 자동 확정.
     * 호출: AdminSectionController.changeCapacity (POST /admin/sections/{sectionId}/capacity)
     *
     * @return 자동 확정된 인원 수 (#09 merge 전에는 스텁이라 0)
     * @throws BusinessException SECTION_NOT_FOUND, CAPACITY_OUT_OF_RANGE, CAPACITY_BELOW_ACTIVE
     * 트랜잭션: 강의 분반 행을 잠근 뒤 활성 수강 수를 센다 — 그 사이 신청·자동 확정으로 수가 바뀌지 않게
     */
    @Transactional
    public int changeCapacity(Long sectionId, int capacity) {
        if (!isCapacityInRange(capacity)) {
            throw new BusinessException(ErrorCode.CAPACITY_OUT_OF_RANGE);  // 범위 오류를 "인원 미만"보다 먼저 알린다
        }
        CourseSection section = lockSection(sectionId);
        // 잠금 읽기로 센다 — 일반 조회는 트랜잭션 첫 조회 시점 스냅숏이라 잠금을 기다리는 사이 확정된 수강을 못 볼 수 있다(#09와 같은 규칙)
        long activeCount = enrollmentRepository.countActiveBySectionIdForUpdate(sectionId);
        if (capacity < activeCount) {
            throw new BusinessException(ErrorCode.CAPACITY_BELOW_ACTIVE);  // FR-052: 활성 수강 미만 축소 불가
        }
        boolean increased = section.changeCapacity(capacity);
        if (!increased) {
            return 0;
        }
        sectionRepository.flush();  // fillVacancies의 native 조회가 바뀐 정원을 보도록 먼저 반영
        return seatService.fillVacancies(section.getCourseId());
    }

    /**
     * 담당 강사 지정·변경 (SE-04) — 수강생 분반 이동 없음(D-59), 새 담당 강사에게 알림, 담당 공백이 해소되면 대기자 자동 확정.
     * 같은 강사를 다시 고르면 아무것도 바꾸지 않는다(알림도 보내지 않음).
     * 호출: AdminSectionController.changeInstructor (POST /admin/sections/{sectionId}/instructor)
     *
     * @return 자동 확정된 인원 수 (#09 merge 전에는 스텁이라 0)
     * @throws BusinessException SECTION_NOT_FOUND, USER_NOT_FOUND, INSTRUCTOR_NOT_GRANTED
     */
    @Transactional
    public int changeInstructor(Long sectionId, Long instructorId) {
        CourseSection section = lockSection(sectionId);
        CourseDetailDTO course = findCourse(section.getCourseId());
        checkAssignable(instructorId, course.getHobbyId());
        if (instructorId.equals(section.getInstructorId())) {
            return 0;
        }

        boolean vacancyFilled = section.assignInstructor(instructorId);
        notifyAssigned(section, course.getTitle());
        if (!vacancyFilled) {
            return 0;  // 담당만 바뀌고 배정 대상 여부는 그대로 — 새 자리가 생기지 않는다
        }
        sectionRepository.flush();  // 배정 대상(assignable)이 된 것을 fillVacancies의 native 조회가 보도록 먼저 반영
        return seatService.fillVacancies(section.getCourseId());
    }

    // ───────────────────────── 강사 상태와 담당 분반 (AI-04·AI-06) ─────────────────────────
    // InstructorAccountService가 이 서비스를 주입하면 순환 참조가 되므로(그쪽 [주의] 참고),
    // "담당 분반과 엮인 강사 계정 변경"은 이 서비스가 맡아 한 트랜잭션으로 처리한다.

    /**
     * 강사 비활성화 (AI-04).
     * - 평소(emergency = false): 담당 분반이 있으면 거부한다 — 관리자가 분반 담당을 먼저 바꾸게 한다(D-59 "강사 교체 = 분반 담당 변경").
     * - 긴급(emergency = true, 갑작스러운 이탈·정지): 바로 비활성화하고 그 강사의 분반을 모두 담당 공백으로 바꾼다.
     *   담당 공백 분반은 신규 배정에서 빠지고(D-52) 관리자 대시보드 "담당 공백 분반"에 나타나 바로 새 담당을 지정할 수 있다.
     *   비활성 강사가 담당으로 남아 있으면 신규 학습자가 평가해 줄 사람이 없는 분반에 배정되기 때문이다.
     * 호출: AdminInstructorController.changeStatus (POST /admin/instructors/{instructorId}/status, active=false)
     *
     * @return 담당 공백으로 바뀐 분반 수
     * @throws BusinessException USER_NOT_FOUND, INSTRUCTOR_HAS_SECTIONS(평소 비활성화인데 담당 분반이 있음)
     */
    @Transactional
    public int deactivateInstructor(Long instructorId, boolean emergency) {
        instructorAccountService.getInstructor(instructorId);  // 강사가 아니면 USER_NOT_FOUND
        if (!emergency && sectionRepository.existsByInstructorId(instructorId)) {
            throw new BusinessException(ErrorCode.INSTRUCTOR_HAS_SECTIONS);
        }
        instructorAccountService.changeStatus(instructorId, false);  // 로그인 중인 세션도 끊는다

        int released = 0;
        for (Long courseId : sectionRepository.findCourseIdsByInstructorId(instructorId)) {
            // 신청·대기 확정과 같은 순서로 강의 분반 행을 먼저 잠근 뒤 바꾼다 (배정 중인 신청과 겹치지 않게)
            for (CourseSection section : sectionRepository.findByCourseIdForUpdate(courseId)) {
                if (instructorId.equals(section.getInstructorId())) {
                    section.releaseInstructor();  // 변경 감지로 저장
                    released++;
                }
            }
        }
        return released;
    }

    /**
     * 취미 활동 권한 회수 (AI-06) — 그 취미의 분반을 담당 중이면 거부한다(API 💡 "담당 중이면 거부").
     * 권한 없는 강사가 분반 담당으로 남지 않게 한다(담당 지정 때 checkAssignable과 같은 규칙).
     * 호출: AdminInstructorController.revokeHobby (POST /admin/instructors/{instructorId}/hobbies/{hobbyId}/revoke)
     *
     * @throws BusinessException USER_NOT_FOUND, HOBBY_NOT_FOUND, GRANT_IN_USE(그 취미 분반 담당 중)
     */
    @Transactional
    public void revokeHobbyGrant(Long instructorId, Long hobbyId) {
        boolean teachesHobby = sectionQuery.findByInstructorId(instructorId).stream()
                .map(SectionDTO::getCourseId)
                .distinct()
                .anyMatch(courseId -> hobbyId.equals(findCourse(courseId).getHobbyId()));
        if (teachesHobby) {
            throw new BusinessException(ErrorCode.GRANT_IN_USE);
        }
        instructorAccountService.revokeHobby(instructorId, hobbyId);
    }

    // ───────────────────────── 계약 C6 ─────────────────────────

    /**
     * 계약 C6: 수강 건의 현재 분반 담당 강사 id — 담당 공백이면 null.
     * 강사가 바뀌면 바로 새 강사 id가 나오므로, 이 값으로 권한을 판단하면 평가 대기 과제가 자동으로 승계된다(D-59).
     * 호출: 팀원 3 ReviewService(평가 권한), 팀원 4 작품 열람·문의
     *
     * @throws BusinessException ENROLLMENT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Long getCurrentInstructorId(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        return sectionRepository.findById(enrollment.getSectionId())
                .map(CourseSection::getInstructorId)
                .orElse(null);
    }

    /** 계약 C6: 강사의 담당 분반 목록 (사용: 팀원 1 #07 강사 홈·통계, #05 담당 분반 수) */
    @Transactional(readOnly = true)
    public List<SectionDTO> getSectionsByInstructor(Long instructorId) {
        if (instructorId == null) {
            return List.of();
        }
        return sectionQuery.findByInstructorId(instructorId);
    }

    // ───────────────────────── 내부 ─────────────────────────

    /** 정원 범위 1~30 (D-52) — 상수는 CourseSection 한곳에 둔다 */
    private static boolean isCapacityInRange(int capacity) {
        return capacity >= CourseSection.MIN_CAPACITY && capacity <= CourseSection.MAX_CAPACITY;
    }

    /** 관리자 기준으로 강의를 찾는다(닫힌 강의 포함). 없으면 COURSE_NOT_FOUND */
    private CourseDetailDTO findCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return courseService.getCourseDetail(courseId, Role.ADMIN);
    }

    /**
     * 분반이 속한 강의의 분반 행을 모두 잠그고, 잠긴 행 중 이 분반을 돌려준다.
     * 강의 id를 값으로 먼저 얻는 이유: 엔티티를 잠금 전에 읽어 두면 잠근 뒤에도 옛 값이 남기 때문.
     */
    private CourseSection lockSection(Long sectionId) {
        Long courseId = sectionRepository.findCourseIdById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
        return sectionRepository.findByCourseIdForUpdate(courseId).stream()
                .filter(section -> section.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
    }

    /**
     * 담당으로 지정할 수 있는 강사인지 — 강사 계정(아니면 USER_NOT_FOUND), 활성, 강의 취미 권한 보유(C3).
     * A-S04 안내 "권한 없는 취미 분반 지정 불가", A-S05 "비활성·취미 권한 없는 강사는 선택할 수 없어요"
     */
    private void checkAssignable(Long instructorId, Long hobbyId) {
        if (instructorId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        InstructorDTO instructor = instructorAccountService.getInstructor(instructorId);  // 강사가 아니면 USER_NOT_FOUND
        if (!instructor.isActive() || !instructorAccountService.hasHobbyGrant(instructorId, hobbyId)) {
            throw new BusinessException(ErrorCode.INSTRUCTOR_NOT_GRANTED);
        }
    }

    /**
     * 새 담당 강사에게 분반 배정 알림 (SE-02·SE-04, 알림 종류 SECTION_ASSIGNED — 대상 칼럼은 분반).
     * 중복 방지 키에 시각을 넣는 이유: 같은 강사가 A→B→A로 다시 배정돼도 알림이 가야 하는데,
     * 기본 키(종류:분반id)를 쓰면 두 번째부터 UNIQUE(user_id, dedupe_key)에 걸려 저장이 생략된다(C7).
     */
    private void notifyAssigned(CourseSection section, String courseTitle) {
        String sectionName = section.getDisplayName();
        notificationService.notify(
                section.getInstructorId(),
                NotificationKind.SECTION_ASSIGNED,
                section.getId(),
                "분반 담당 배정",
                courseTitle + " " + sectionName + " 담당 강사로 지정되었습니다.",
                NotificationKind.SECTION_ASSIGNED.name() + ":" + section.getId() + ":" + Instant.now().toEpochMilli());
    }

    /**
     * "다음 배정 분반" 표시 — 담당 강사가 있고 남은 자리가 있는 분반 중 남은 자리가 가장 큰 분반, 같으면 번호가 작은 분반(D-52).
     * 실제 배정은 #09(SectionAssignPolicy)가 같은 기준으로 하고, 여기서는 화면 안내만 한다.
     */
    private void markNextAssign(List<SectionDTO> sections) {
        sections.stream()
                .filter(section -> !section.isVacant() && section.getRemaining() > 0)
                .min(Comparator.comparingInt(SectionDTO::getRemaining).reversed()
                        .thenComparing(SectionDTO::getSectionNo))
                .ifPresent(section -> section.setNextAssign(true));
    }
}
