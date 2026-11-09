package com.wanted.lmspractice.domain.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.dto.SectionDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SectionFormDTO;
import com.wanted.lmspractice.domain.enrollment.dto.WaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.enrollment.scheduler.EnrollmentExpiryScheduler;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] #10 핵심 규칙 테스트 — 분반 생성·정원(1~30, 활성 수강 미만 축소 불가)·담당 지정(취미 권한·활성)·C6 현재 담당·만료 처리·대기열 조회
 * [담당] 팀원 2 · #10 (이슈 테스트 항목·완료 조건)
 * [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql이 적재돼 있어야 한다. 각 테스트는 @Transactional이라 끝나면 롤백된다.
 *       시드: 강의 1(색연필화, 분반 1 A·teacherA / 분반 2 B·teacherB, 수강 e6 ACTIVE는 분반 1), 강의 3(분반 4, e3 ACTIVE),
 *            강의 4(디지털, 분반 5 정원 1 만석, 대기 w2 learner3), 강의 6(분반 7 teacherA, 분반 8 담당 공백), 강의 7(디저트, 분반 9)
 *            teacherA(6) 취미 1·2 / teacherB(7) 취미 3·1 / teacherC(8) 비활성·취미 3 / teacherD(9) 취미 2 / learner(2)
 * [자동 확정] issue09(#09 fillVacancies)를 합친 뒤 정원 증가·만료 → 대기 1순위 자동 확정을 확인한다(autoConfirm… 테스트).
 * [미확인] 평가 대기 과제가 있는 만료 건 유지는 ReviewQueryService(#14) 스텁이 false라 #14 merge 후 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5에서 핵심 규칙 테스트용으로 허용)
 */
@SpringBootTest
@Transactional
class SectionServiceTest {

    private static final long COURSE_PENCIL_BASIC = 1L;  // 취미 1
    private static final long COURSE_PENCIL_ADVANCED = 3L;
    private static final long COURSE_DIGITAL_BASIC = 4L;  // 취미 2
    private static final long COURSE_DIGITAL_ADVANCED = 6L;
    private static final long COURSE_DESSERT_BASIC = 7L;  // 취미 3
    private static final long SECTION_PENCIL_A = 1L;
    private static final long SECTION_DIGITAL_VACANT = 8L;
    private static final long LEARNER_ID = 2L;
    private static final long TEACHER_A = 6L;
    private static final long TEACHER_B = 7L;
    private static final long TEACHER_C_INACTIVE = 8L;
    private static final long TEACHER_D = 9L;
    private static final long ENROLLMENT_E3 = 3L;  // learner · 강의 3 · ACTIVE
    private static final long ENROLLMENT_E6 = 6L;  // learner3 · 강의 1 분반 1 · ACTIVE

    @Autowired
    private SectionService sectionService;
    @Autowired
    private WaitlistService waitlistService;
    @Autowired
    private EnrollmentExpiryScheduler expiryScheduler;
    @Autowired
    private CourseSectionRepository sectionRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired
    private EntityManager entityManager;

    private SectionFormDTO form(long courseId, String name, Integer capacity, Long instructorId) {
        return new SectionFormDTO(courseId, name, capacity, instructorId);
    }

    // ───────────────────────── 분반 생성 ─────────────────────────

    @Test
    @DisplayName("분반을 만들면 다음 번호로 저장되고, 이름을 비우면 번호로 'C분반'이라 표시된다")
    void create() {
        Long sectionId = sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, "  ", 10, TEACHER_A));

        CourseSection saved = sectionRepository.findById(sectionId).orElseThrow();
        assertThat(saved.getSectionNo()).isEqualTo(3);
        assertThat(saved.getName()).isNull();
        assertThat(saved.getCapacity()).isEqualTo(10);
        SectionDTO shown = sectionService.getSection(sectionId);
        assertThat(shown.getDisplayName()).isEqualTo("C분반");
        assertThat(shown.getRemaining()).isEqualTo(10);
        // 남은 자리가 가장 큰 분반이 "다음 배정 분반"(D-52)
        assertThat(sectionService.getSectionsForAdmin(COURSE_PENCIL_BASIC))
                .filteredOn(SectionDTO::isNextAssign).extracting(SectionDTO::getId).containsExactly(sectionId);
    }

    @Test
    @DisplayName("분반 생성: 취미 권한 없는 강사·비활성 강사는 INSTRUCTOR_NOT_GRANTED, 강사가 아닌 회원은 USER_NOT_FOUND")
    void createRejectsInvalidInstructor() {
        assertThatThrownBy(() -> sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, null, 10, TEACHER_D)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED);
        assertThatThrownBy(() -> sectionService.create(COURSE_DESSERT_BASIC, form(COURSE_DESSERT_BASIC, null, 10, TEACHER_C_INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED);
        assertThatThrownBy(() -> sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, null, 10, LEARNER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("분반 생성: 정원 0·31은 거부, 같은 강의의 같은 이름은 사용할 수 없다")
    void createRejectsCapacityAndDuplicateName() {
        assertThatThrownBy(() -> sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, null, 0, TEACHER_A)))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThatThrownBy(() -> sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, null, 31, TEACHER_A)))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThat(sectionService.isNameAvailable(COURSE_PENCIL_BASIC, "A분반")).isFalse();
        assertThat(sectionService.isNameAvailable(COURSE_PENCIL_BASIC, "새분반")).isTrue();
    }

    // ───────────────────────── 정원 변경 ─────────────────────────

    @Test
    @DisplayName("정원 변경: 0·31은 범위 오류, 현재 활성 수강보다 작게는 줄일 수 없다")
    void changeCapacityRejects() {
        addActiveEnrollment(3L, COURSE_PENCIL_BASIC, SECTION_PENCIL_A);  // 분반 1 활성 수강 2명(e6 + learner2)

        assertThat(sectionService.checkCapacity(SECTION_PENCIL_A, 0)).contains(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThat(sectionService.checkCapacity(SECTION_PENCIL_A, 31)).contains(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThat(sectionService.checkCapacity(SECTION_PENCIL_A, 1)).contains(ErrorCode.CAPACITY_BELOW_ACTIVE);
        assertThat(sectionService.checkCapacity(SECTION_PENCIL_A, 2)).isEqualTo(Optional.empty());

        assertThatThrownBy(() -> sectionService.changeCapacity(SECTION_PENCIL_A, 31))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThatThrownBy(() -> sectionService.changeCapacity(SECTION_PENCIL_A, 1))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_BELOW_ACTIVE);
        assertThatThrownBy(() -> sectionService.changeCapacity(999_999L, 5))
                .extracting("errorCode").isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("정원 변경: 활성 수강 수와 같게 줄이거나 늘릴 수 있고, 남은 자리가 다시 계산된다")
    void changeCapacity() {
        sectionService.changeCapacity(SECTION_PENCIL_A, 1);  // 활성 1명과 같게
        assertThat(sectionService.getSection(SECTION_PENCIL_A).getRemaining()).isZero();

        sectionService.changeCapacity(SECTION_PENCIL_A, 12);  // 늘리면 fillVacancies 호출(대기자 없는 강의라 확정 0명)
        SectionDTO section = sectionService.getSection(SECTION_PENCIL_A);
        assertThat(section.getCapacity()).isEqualTo(12);
        assertThat(section.getRemaining()).isEqualTo(11);
    }

    // ───────────────────────── 담당 지정·변경 ─────────────────────────

    @Test
    @DisplayName("담당 변경: 수강 건은 분반을 옮기지 않고, 현재 담당 강사(C6)가 새 강사로 바뀐다(D-59)")
    void changeInstructorKeepsEnrollment() {
        assertThat(sectionService.getCurrentInstructorId(ENROLLMENT_E6)).isEqualTo(TEACHER_A);

        sectionService.changeInstructor(SECTION_PENCIL_A, TEACHER_B);

        assertThat(sectionService.getCurrentInstructorId(ENROLLMENT_E6)).isEqualTo(TEACHER_B);
        assertThat(enrollmentRepository.findById(ENROLLMENT_E6).orElseThrow().getSectionId()).isEqualTo(SECTION_PENCIL_A);
        assertThat(sectionService.getSectionsByInstructor(TEACHER_B)).extracting(SectionDTO::getId).contains(SECTION_PENCIL_A);
        assertThat(sectionService.getSectionsByInstructor(TEACHER_A)).extracting(SectionDTO::getId).doesNotContain(SECTION_PENCIL_A);
    }

    @Test
    @DisplayName("담당 지정: 공백 분반에 권한 있는 강사를 지정하면 신규 배정 대상이 되고, 권한 없는 강사는 거부된다")
    void assignVacantSection() {
        assertThat(sectionService.getSection(SECTION_DIGITAL_VACANT).getStatusLabel()).isEqualTo("신규 배정 제외");
        assertThatThrownBy(() -> sectionService.changeInstructor(SECTION_DIGITAL_VACANT, TEACHER_B))  // 취미 2 권한 없음
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED);

        sectionService.changeInstructor(SECTION_DIGITAL_VACANT, TEACHER_D);

        SectionDTO section = sectionService.getSection(SECTION_DIGITAL_VACANT);
        assertThat(section.isVacant()).isFalse();
        assertThat(section.getInstructorName()).isEqualTo("윤강사");
        // 분반 7·8 모두 남은 자리 5 → 번호가 작은 분반 7이 다음 배정 분반(D-52)
        assertThat(sectionService.getSectionsForAdmin(COURSE_DIGITAL_ADVANCED))
                .filteredOn(SectionDTO::isNextAssign).extracting(SectionDTO::getId).containsExactly(7L);
        // 후보 강사 = 취미 2 권한이 있는 활성 강사
        assertThat(sectionService.getCandidates(COURSE_DIGITAL_ADVANCED))
                .extracting(candidate -> candidate.getId()).containsExactlyInAnyOrder(TEACHER_A, TEACHER_D);
    }

    // ───────────────────────── 만료 처리 ─────────────────────────

    @Test
    @DisplayName("만료: 만료 시각이 지난 수강 건은 EXPIRED가 되고 자리가 반환된다, 기간 안 수강 건은 그대로")
    void expire() {
        // e3을 40일 전 확정으로 바꾼다 (CHECK: expires_at = confirmed_at + 30일)
        entityManager.createNativeQuery("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = :id")
                .setParameter("id", ENROLLMENT_E3)
                .executeUpdate();
        entityManager.clear();
        int activeBefore = sectionService.getSectionsForAdmin(COURSE_PENCIL_ADVANCED).get(0).getActiveCount();

        int expired = expiryScheduler.expire();

        entityManager.clear();
        Enrollment e3 = enrollmentRepository.findById(ENROLLMENT_E3).orElseThrow();
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(e3.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(e3.getEndedAt()).isNotNull();
        assertThat(sectionService.getSectionsForAdmin(COURSE_PENCIL_ADVANCED).get(0).getActiveCount()).isEqualTo(activeBefore - 1);
        assertThat(enrollmentRepository.findById(ENROLLMENT_E6).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    // ───────────────────────── 자동 확정 (#09 fillVacancies 연결) ─────────────────────────

    @Test
    @DisplayName("정원 증가: 만석 분반(강의 4)의 정원을 늘리면 대기 1순위(learner3)가 자동 수강 확정된다")
    void autoConfirmOnCapacityIncrease() {
        int confirmed = sectionService.changeCapacity(5L, 2);  // 분반 5: 정원 1·활성 1 → 2

        assertThat(confirmed).isEqualTo(1);
        assertThat(waitlistService.getWaitlist(COURSE_DIGITAL_BASIC)).isEmpty();
        assertThat(waitlistService.getRecentConfirmed(COURSE_DIGITAL_BASIC))
                .extracting(WaitlistDTO::getLoginId).containsExactly("learner3");
        assertThat(sectionService.getSection(5L).getRemaining()).isZero();
    }

    @Test
    @DisplayName("만료: 만석 강의의 수강 건이 만료되면 반환된 자리로 대기 1순위가 자동 확정된다")
    void autoConfirmOnExpiry() {
        // e5(learner2 · 강의 4 분반 5 · 만석)의 과제는 시드에서 평가 진행 중(평가 회차 4)이라 만료가 보류된다(05 §4-2, #14 hasPendingReview).
        // 이 시나리오는 "평가가 끝난 뒤 기간이 지난" 경우이므로 회차 4를 반려로 마무리한다(CHECK: 반려 사유 50자 이상·평가자·확정 시각 필수).
        entityManager.createNativeQuery("UPDATE review_rounds SET status = 'REJECTED', draft_body = NULL, draft_author_id = NULL, "
                        + "draft_updated_at = NULL, feedback_body = REPEAT('가', 60), reviewer_id = 6, finalized_at = UTC_TIMESTAMP(6) WHERE id = 4")
                .executeUpdate();
        // e5 를 40일 전 확정으로 바꾼다
        entityManager.createNativeQuery("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = 5")
                .executeUpdate();
        entityManager.clear();

        expiryScheduler.expire();

        entityManager.clear();
        assertThat(enrollmentRepository.findById(5L).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(waitlistService.getWaitlist(COURSE_DIGITAL_BASIC)).isEmpty();
        assertThat(waitlistService.getRecentConfirmed(COURSE_DIGITAL_BASIC))
                .extracting(WaitlistDTO::getLoginId).containsExactly("learner3");
    }

    @Test
    @DisplayName("만료 보류: 평가 대기·진행 중인 과제가 있는 수강 건은 기간이 지나도 만료되지 않고 대기열도 그대로다 (05 §4-2)")
    void pendingReviewBlocksExpiry() {
        // e5 의 과제는 시드에서 평가 진행 중(평가 회차 4). 기간만 지난 상태로 만든다
        entityManager.createNativeQuery("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = 5")
                .executeUpdate();
        entityManager.clear();

        expiryScheduler.expire();

        entityManager.clear();
        assertThat(enrollmentRepository.findById(5L).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(waitlistService.getWaitlist(COURSE_DIGITAL_BASIC)).hasSize(1);   // 자리가 반환되지 않아 대기자는 그대로
    }

    // ───────────────────────── 대기열 조회 ─────────────────────────

    @Test
    @DisplayName("대기열 조회: 대기 중 학습자를 순번대로, 신청 시각은 엔티티로 읽은 값과 같은 시각으로 보여 준다")
    void waitlist() {
        List<WaitlistDTO> waiting = waitlistService.getWaitlist(COURSE_DIGITAL_BASIC);

        assertThat(waiting).hasSize(1);
        WaitlistDTO first = waiting.get(0);
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(first.getLoginId()).isEqualTo("learner3");
        // native 조회 시각과 JPA 엔티티 시각이 같은지 — 시간대 변환이 어긋나지 않는지 확인
        assertThat(first.getRequestedAt())
                .isEqualTo(waitlistEntryRepository.findById(first.getEntryId()).orElseThrow().getCreatedAt());

        List<WaitlistDTO> confirmed = waitlistService.getRecentConfirmed(2L);  // 시드 w1: learner → 강의 2 A분반 확정
        assertThat(confirmed).extracting(WaitlistDTO::getLoginId).contains("learner");
        assertThat(confirmed.get(0).getSectionName()).isEqualTo("A분반");
    }

    /** 시드에 없는 활성 수강 건 추가 (expires_at = confirmed_at + 30일 CHECK를 지키도록 같은 시각 기준) */
    private void addActiveEnrollment(long userId, long courseId, long sectionId) {
        entityManager.createNativeQuery("INSERT INTO enrollments (user_id, course_id, section_id, status, confirmed_at, expires_at) "
                        + "VALUES (:userId, :courseId, :sectionId, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 30 DAY)")
                .setParameter("userId", userId)
                .setParameter("courseId", courseId)
                .setParameter("sectionId", sectionId)
                .executeUpdate();
    }
}
