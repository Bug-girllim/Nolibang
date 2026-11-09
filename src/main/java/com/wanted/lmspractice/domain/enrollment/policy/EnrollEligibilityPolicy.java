package com.wanted.lmspractice.domain.enrollment.policy;

import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 수강 신청(즉시 확정·대기 모두) 전에 "이 학습자가 이 강의를 신청할 수 있는가"를 판단한다 — 저장·상태 변경은 하지 않는다
 * [담당] 팀원 2 · #09   [화면] U-S05   [기획] FR-052·053·055, D-52(담당 공백 분반 제외)·D-55(재신청 불가)   [API] EN-01 처리 1
 * [연결]
 *  - 호출받음: EnrollmentService.apply (분반 행 잠금 직후)
 *  - 호출함:   CourseService.getCourseDetail(강의 노출 규칙), HobbyService.getHobbySummary(운영 중 취미인지) — 다른 도메인은 서비스로만,
 *             EnrollmentRepository·WaitlistEntryRepository(같은 도메인 — 이 학습자의 수강·대기 기록)
 * [기술] 🟢 Policy(@Component) + BusinessException
 */
@Component
@RequiredArgsConstructor
public class EnrollEligibilityPolicy {

    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final CourseService courseService;
    private final HobbyService hobbyService;

    /**
     * 신청 자격을 모두 확인한다. 하나라도 어기면 예외 — 통과하면 아무 일도 하지 않는다.
     * 호출: EnrollmentService.apply
     * @param lockedSections 이 강의의 분반(이미 FOR UPDATE로 잠근 목록)
     * @throws BusinessException COURSE_NOT_FOUND, HOBBY_NOT_OPEN, ALREADY_ENROLLED, CANNOT_REAPPLY, ALREADY_WAITING, NO_ASSIGNABLE_SECTION
     */
    public void check(Long userId, Long courseId, List<CourseSection> lockedSections) {
        checkCourseOpen(courseId);
        checkNoHistory(userId, courseId);
        checkHasAssignableSection(lockedSections);
    }

    /** 학습자에게 보이는 강의이고, 그 취미가 운영 중이어야 한다(준비 중 취미는 소개만 — 05 §3) */
    private void checkCourseOpen(Long courseId) {
        // 신청은 학습자만 한다(SecurityConfig: /enrollments/** = USER). 닫힌 강의·비공개 취미의 강의는 여기서 COURSE_NOT_FOUND
        CourseDetailDTO course = courseService.getCourseDetail(courseId, Role.USER);
        if (!hobbyService.getHobbySummary(course.getHobbyId(), Role.USER).isOperating()) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_OPEN);
        }
    }

    /**
     * 같은 강의의 수강·대기 기록이 없어야 한다.
     * 수강 건은 UNIQUE(user_id, course_id)라 상태와 관계없이 최대 1건 — ACTIVE면 수강 중, 그 밖(수료·취소·만료)이면 재신청 불가.
     */
    private void checkNoHistory(Long userId, Long courseId) {
        enrollmentRepository.findByUserIdAndCourseId(userId, courseId).ifPresent(enrollment -> {
            // D-55: 수료·취소·만료한 강의는 다시 신청·대기할 수 없다
            throw new BusinessException(enrollment.isActive() ? ErrorCode.ALREADY_ENROLLED : ErrorCode.CANNOT_REAPPLY);
        });
        if (waitlistEntryRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, WaitlistStatus.WAITING).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_WAITING);
        }
    }

    /** D-52: 담당 강사가 있는 분반이 하나도 없으면 신청도 대기도 받지 않는다(신청 영역 UNAVAILABLE과 같은 기준) */
    private void checkHasAssignableSection(List<CourseSection> lockedSections) {
        if (lockedSections.stream().noneMatch(CourseSection::isAssignable)) {
            throw new BusinessException(ErrorCode.NO_ASSIGNABLE_SECTION);
        }
    }
}
