package com.wanted.lmspractice.domain.enrollment.service;

import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.policy.SectionAssignPolicy;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] ★ 자리 반환 → 대기 1순위 자동 확정 → 알림 (호출자 트랜잭션 안에서 실행) + 강의별 정원 현황 조회(#08)
 *        자리가 생기는 경우(수강 취소·수료·만료·만료 후 반려·정원 증가/담당 지정)가 모두 fillVacancies 하나로 모인다 — 다른 곳에 같은 로직을 만들지 않는다.
 * [담당] 팀원 2 · #09 (getSeatSummary 조회 계약은 #08에서 추가)   [기획] FR-053·054, D-52   [API] EN-03 처리, NT 알림 WAITLIST_CONFIRMED
 * [연결]
 *  - 호출받음: EnrollmentService(cancel·complete·closeAfterRejection, #09), SectionService·EnrollmentExpiryScheduler(#10) → fillVacancies
 *             CourseCatalogService(#08) → getSeatSummary
 *  - 호출함:   CourseSectionRepository(강의 잠금), SectionAssignPolicy(분반 선택), WaitlistEntryRepository(1순위),
 *             NotificationService.notify(계약 C7, 팀원 4 — #17 전에는 스텁), CourseService.getCourseTitle(알림 문구)
 *  - 계약:     C4 int fillVacancies(Long courseId) — 시그니처 변경 금지
 * [기술] 🟢 @Transactional(기본 전파 = 호출자 트랜잭션 참여), 🟢 native FOR UPDATE, 🟢 EntityManager native 조회(getSeatSummary)
 */
@Service
@RequiredArgsConstructor
public class SeatService {

    private static final String CONFIRMED_TITLE = "수강 확정";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final CourseSectionRepository courseSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SectionAssignPolicy sectionAssignPolicy;
    private final NotificationService notificationService;
    private final CourseService courseService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 계약 C4: 남은 자리가 있는 동안 대기 1순위부터 수강 확정하고, 확정한 인원 수를 돌려준다.
     * 대기자는 초대·수락 단계 없이 바로 확정되고 사이트 알림(WAITLIST_CONFIRMED)을 받는다(U-S05, D-57 사이트 알림만).
     * 호출: EnrollmentService.cancel·complete·closeAfterRejection(#09), SectionService 정원 증가·담당 지정, EnrollmentExpiryScheduler(#10)
     * 트랜잭션: 반드시 "자리를 비운 변경과 같은 트랜잭션"에서 실행한다(별도 트랜잭션 금지 — 둘 중 하나만 반영되면 자리가 사라지거나 넘친다).
     *          @Transactional 기본값(REQUIRED)이라 호출자 트랜잭션에 그대로 참여한다.
     * 동시성: 강의 분반 행을 먼저 FOR UPDATE로 잠그고, 남은 자리·1순위도 잠금 읽기로 본다 — 호출자가 앞에서 일반 조회를 했더라도
     *        (= 오래된 스냅숏) 항상 최신 값으로 판단한다. 그래서 동시에 자리 2개가 반환돼도 1·2순위가 각각 1건씩 확정된다(시나리오 33).
     * 주의: 자리를 비운 변경(취소·수료 등)은 이 메서드를 부르기 전에 flush되어 있어야 남은 자리로 계산된다(EnrollmentService.releaseSeat 참고).
     */
    @Transactional
    public int fillVacancies(Long courseId) {
        List<CourseSection> sections = courseSectionRepository.findByCourseIdForUpdate(courseId);
        LocalDateTime now = LocalDateTime.now();
        String courseTitle = null;  // 확정자가 있을 때만 한 번 읽는다
        int confirmedCount = 0;
        while (true) {
            Optional<CourseSection> section = sectionAssignPolicy.pickSection(sections);
            if (section.isEmpty()) {
                break;  // 남은 자리 없음
            }
            Optional<WaitlistEntry> head = waitlistEntryRepository.findFirstWaitingForUpdate(courseId);
            if (head.isEmpty()) {
                break;  // 대기자 없음
            }
            if (courseTitle == null) {
                courseTitle = courseService.getCourseTitle(courseId);
            }
            confirmHead(head.get(), section.get(), now, courseTitle);
            confirmedCount++;
        }
        return confirmedCount;
    }

    /**
     * 대기 1순위 한 명을 확정한다: 수강 건 생성 → 대기 줄 CONFIRMED(수강 건 연결) → 알림.
     * 순서 이유: waitlist_entries.enrollment_id가 enrollments를 FK로 가리키므로 수강 건이 먼저 있어야 한다.
     * 각 단계를 바로 flush해야 다음 반복의 잠금 읽기(남은 자리·1순위)가 방금 확정한 결과를 본다.
     */
    private void confirmHead(WaitlistEntry head, CourseSection section, LocalDateTime now, String courseTitle) {
        Enrollment enrollment = enrollmentRepository.saveAndFlush(
                Enrollment.create(head.getUserId(), head.getCourseId(), section.getId(), now));
        head.confirm(enrollment.getId(), now);
        waitlistEntryRepository.saveAndFlush(head);
        notificationService.notify(head.getUserId(), NotificationKind.WAITLIST_CONFIRMED, enrollment.getId(),
                CONFIRMED_TITLE,
                courseTitle + " 대기 순서가 되어 수강이 확정되었습니다. ("
                        + section.getDisplayName() + ", " + enrollment.getExpiresAt().format(DATE) + "까지)");
    }

    /**
     * 계약 C16(#08 추가): 한 강의의 정원 합계·남은 자리 합계·대기 인원 — 강의 목록·상세 화면용 조회.
     * 신청 가능한 분반(담당 강사가 있는 분반, v_section_seats.assignable)만 센다. 담당 강사가 없는 분반은 신청 대상이 아니다(D-52).
     * 호출: CourseCatalogService.getCourses·getCourseDetail, EnrollmentService.getStatusForUser
     * 읽기 전용 화면 표시용이다 — 신청·자리 채우기의 실제 판단은 잠금 읽기(SectionAssignPolicy)로 따로 한다.
     */
    @Transactional(readOnly = true)
    public SeatSummaryDTO getSeatSummary(Long courseId) {
        Object[] seats = (Object[]) entityManager.createNativeQuery(
                        "SELECT COALESCE(SUM(capacity), 0), COALESCE(SUM(remaining), 0) "
                                + "FROM v_section_seats WHERE course_id = :courseId AND assignable = 1")
                .setParameter("courseId", courseId)
                .getSingleResult();
        Number waiting = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM waitlist_entries WHERE course_id = :courseId AND status = 'WAITING'")
                .setParameter("courseId", courseId)
                .getSingleResult();
        return new SeatSummaryDTO(((Number) seats[0]).intValue(), ((Number) seats[1]).intValue(), waiting.intValue());
    }
}
