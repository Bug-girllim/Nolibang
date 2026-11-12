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
 *       시드: 강의 1(색연필화, 분반 1 A·teacherA 정원 1뿐 — learnerB e1 ACTIVE(평가 회차 없음)로 만석, 대기 w2 learnerC 1번),
 *            강의 3(분반 3, 수강 없음), 강의 4(디지털, 분반 4 A·teacherA 정원 5 e2 ACTIVE(r2 평가 대기) / 분반 5 B·teacherB 정원 5 e4 ACTIVE(r4 평가 중), 대기 없음),
 *            강의 6(분반 7 teacherA, 분반 8 담당 공백, 수강 없음), 강의 7(디저트, 분반 9)
 *            teacherA(6) 취미 1·2 / teacherB(7) 취미 2·3 / teacherC(8) 비활성·취미 3 / teacherD(9) 취미 2 / learnerA(2) 학습자·기록 없음
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
    private static final long SECTION_PENCIL_A = 1L;    // 강의 1 유일 분반, 정원 1 만석
    private static final long SECTION_PENCIL_ADVANCED = 3L;
    private static final long SECTION_DIGITAL_A = 4L;   // 강의 4 A분반, 정원 5 · 활성 1(e2)
    private static final long SECTION_DIGITAL_VACANT = 8L;
    private static final long LEARNER_ID = 2L;          // learnerA — 수강·대기 기록 없음
    private static final long TEACHER_A = 6L;
    private static final long TEACHER_B = 7L;
    private static final long TEACHER_C_INACTIVE = 8L;
    private static final long TEACHER_D = 9L;
    private static final long ENROLLMENT_E1 = 1L;  // learnerB · 강의 1 분반 1 · ACTIVE · 평가 회차 없음
    private static final long ENROLLMENT_E2 = 2L;  // learnerB · 강의 4 분반 4 · ACTIVE · r2 평가 대기
    private static final long ENROLLMENT_E4 = 4L;  // learnerC · 강의 4 분반 5 · ACTIVE · r4 평가 진행 중

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
        // 강의 4: 분반 A(1)·B(2) 두 개, 대기자 없음 → 새 분반은 3번, 자동 확정 0명
        Long sectionId = sectionService.create(COURSE_DIGITAL_BASIC, form(COURSE_DIGITAL_BASIC, "  ", 10, TEACHER_A));

        CourseSection saved = sectionRepository.findById(sectionId).orElseThrow();
        assertThat(saved.getSectionNo()).isEqualTo(3);
        assertThat(saved.getName()).isNull();
        assertThat(saved.getCapacity()).isEqualTo(10);
        SectionDTO shown = sectionService.getSection(sectionId);
        assertThat(shown.getDisplayName()).isEqualTo("C분반");
        assertThat(shown.getRemaining()).isEqualTo(10);
        // 남은 자리가 가장 큰 분반(10 > A분반 4·B분반 4)이 "다음 배정 분반"(D-52)
        assertThat(sectionService.getSectionsForAdmin(COURSE_DIGITAL_BASIC))
                .filteredOn(SectionDTO::isNextAssign).extracting(SectionDTO::getId).containsExactly(sectionId);
    }

    @Test
    @DisplayName("분반 생성: 취미 권한 없는 강사·비활성 강사는 INSTRUCTOR_NOT_GRANTED, 강사가 아닌 회원은 USER_NOT_FOUND")
    void createRejectsInvalidInstructor() {
        assertThatThrownBy(() -> sectionService.create(COURSE_PENCIL_BASIC, form(COURSE_PENCIL_BASIC, null, 10, TEACHER_D)))  // 취미 1 권한 없음
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
        addActiveEnrollment(LEARNER_ID, COURSE_DIGITAL_BASIC, SECTION_DIGITAL_A);  // 분반 4 활성 수강 2명(e2 + learnerA)

        assertThat(sectionService.checkCapacity(SECTION_DIGITAL_A, 0)).contains(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThat(sectionService.checkCapacity(SECTION_DIGITAL_A, 31)).contains(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThat(sectionService.checkCapacity(SECTION_DIGITAL_A, 1)).contains(ErrorCode.CAPACITY_BELOW_ACTIVE);
        assertThat(sectionService.checkCapacity(SECTION_DIGITAL_A, 2)).isEqualTo(Optional.empty());

        assertThatThrownBy(() -> sectionService.changeCapacity(SECTION_DIGITAL_A, 31))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_OUT_OF_RANGE);
        assertThatThrownBy(() -> sectionService.changeCapacity(SECTION_DIGITAL_A, 1))
                .extracting("errorCode").isEqualTo(ErrorCode.CAPACITY_BELOW_ACTIVE);
        assertThatThrownBy(() -> sectionService.changeCapacity(999_999L, 5))
                .extracting("errorCode").isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("정원 변경: 활성 수강 수와 같게 줄이거나 늘릴 수 있고, 남은 자리가 다시 계산된다")
    void changeCapacity() {
        sectionService.changeCapacity(SECTION_DIGITAL_A, 1);  // 정원 5 → 활성 1명(e2)과 같게
        assertThat(sectionService.getSection(SECTION_DIGITAL_A).getRemaining()).isZero();

        sectionService.changeCapacity(SECTION_DIGITAL_A, 12);  // 늘리면 fillVacancies 호출(대기자 없는 강의 4라 확정 0명)
        SectionDTO section = sectionService.getSection(SECTION_DIGITAL_A);
        assertThat(section.getCapacity()).isEqualTo(12);
        assertThat(section.getRemaining()).isEqualTo(11);
    }

    // ───────────────────────── 담당 지정·변경 ─────────────────────────

    @Test
    @DisplayName("담당 변경: 수강 건은 분반을 옮기지 않고, 현재 담당 강사(C6)가 새 강사로 바뀐다(D-59)")
    void changeInstructorKeepsEnrollment() {
        // e2는 강의 4 A분반(teacherA). teacherB도 취미 2(디지털 드로잉) 권한이 있다
        assertThat(sectionService.getCurrentInstructorId(ENROLLMENT_E2)).isEqualTo(TEACHER_A);

        sectionService.changeInstructor(SECTION_DIGITAL_A, TEACHER_B);

        assertThat(sectionService.getCurrentInstructorId(ENROLLMENT_E2)).isEqualTo(TEACHER_B);
        assertThat(enrollmentRepository.findById(ENROLLMENT_E2).orElseThrow().getSectionId()).isEqualTo(SECTION_DIGITAL_A);
        assertThat(sectionService.getSectionsByInstructor(TEACHER_B)).extracting(SectionDTO::getId).contains(SECTION_DIGITAL_A);
        assertThat(sectionService.getSectionsByInstructor(TEACHER_A)).extracting(SectionDTO::getId).doesNotContain(SECTION_DIGITAL_A);
    }

    @Test
    @DisplayName("담당 지정: 공백 분반에 권한 있는 강사를 지정하면 신규 배정 대상이 되고, 권한 없는 강사는 거부된다")
    void assignVacantSection() {
        // 시드에서 활성 강사(A·B·D)는 모두 취미 2 권한이 있다 → teacherB의 취미 2 권한을 회수한 상태로 만든다(AI-06과 같은 is_active = false, 롤백됨)
        entityManager.createNativeQuery("UPDATE instructor_hobby_grants SET is_active = FALSE, revoked_at = UTC_TIMESTAMP(6) "
                        + "WHERE instructor_id = :instructorId AND hobby_id = 2")
                .setParameter("instructorId", TEACHER_B)
                .executeUpdate();

        assertThat(sectionService.getSection(SECTION_DIGITAL_VACANT).getStatusLabel()).isEqualTo("신규 배정 제외");
        assertThatThrownBy(() -> sectionService.changeInstructor(SECTION_DIGITAL_VACANT, TEACHER_B))  // 취미 2 권한 회수됨
                .extracting("errorCode").isEqualTo(ErrorCode.INSTRUCTOR_NOT_GRANTED);

        sectionService.changeInstructor(SECTION_DIGITAL_VACANT, TEACHER_D);

        SectionDTO section = sectionService.getSection(SECTION_DIGITAL_VACANT);
        assertThat(section.isVacant()).isFalse();
        assertThat(section.getInstructorName()).isEqualTo("윤강사");
        // 분반 7·8 모두 남은 자리 5 → 번호가 작은 분반 7이 다음 배정 분반(D-52)
        assertThat(sectionService.getSectionsForAdmin(COURSE_DIGITAL_ADVANCED))
                .filteredOn(SectionDTO::isNextAssign).extracting(SectionDTO::getId).containsExactly(7L);
        // 후보 강사 = 취미 2 권한이 (유효하게) 있는 활성 강사 — 권한을 회수한 teacherB는 빠진다
        assertThat(sectionService.getCandidates(COURSE_DIGITAL_ADVANCED))
                .extracting(candidate -> candidate.getId()).containsExactlyInAnyOrder(TEACHER_A, TEACHER_D);
    }

    // ───────────────────────── 만료 처리 ─────────────────────────

    @Test
    @DisplayName("만료: 만료 시각이 지난 수강 건은 EXPIRED가 되고 자리가 반환된다, 기간 안 수강 건은 그대로")
    void expire() {
        // 시드에는 평가 대기·진행 중 과제가 없는 "기간이 지난" 수강 건이 없다 → 강의 3(대기자 없음)에 40일 전 확정한 learnerA 수강 건을 넣는다
        // (CHECK: expires_at = confirmed_at + 30일)
        entityManager.createNativeQuery("INSERT INTO enrollments (user_id, course_id, section_id, status, confirmed_at, expires_at) "
                        + "VALUES (:userId, :courseId, :sectionId, 'ACTIVE', UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "UTC_TIMESTAMP(6) - INTERVAL 40 DAY + INTERVAL 30 DAY)")
                .setParameter("userId", LEARNER_ID)
                .setParameter("courseId", COURSE_PENCIL_ADVANCED)
                .setParameter("sectionId", SECTION_PENCIL_ADVANCED)
                .executeUpdate();
        entityManager.clear();
        Long pastId = enrollmentRepository.findByUserIdAndCourseId(LEARNER_ID, COURSE_PENCIL_ADVANCED).orElseThrow().getId();
        int activeBefore = sectionService.getSectionsForAdmin(COURSE_PENCIL_ADVANCED).get(0).getActiveCount();

        int expired = expiryScheduler.expire();

        entityManager.clear();
        Enrollment past = enrollmentRepository.findById(pastId).orElseThrow();
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(past.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(past.getEndedAt()).isNotNull();
        assertThat(sectionService.getSectionsForAdmin(COURSE_PENCIL_ADVANCED).get(0).getActiveCount()).isEqualTo(activeBefore - 1);
        assertThat(enrollmentRepository.findById(ENROLLMENT_E1).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    // ───────────────────────── 자동 확정 (#09 fillVacancies 연결) ─────────────────────────

    @Test
    @DisplayName("정원 증가: 만석 분반(강의 1)의 정원을 늘리면 대기 1순위(learnerC)가 자동 수강 확정된다")
    void autoConfirmOnCapacityIncrease() {
        int confirmed = sectionService.changeCapacity(SECTION_PENCIL_A, 2);  // 분반 1: 정원 1·활성 1 → 2

        assertThat(confirmed).isEqualTo(1);
        assertThat(waitlistService.getWaitlist(COURSE_PENCIL_BASIC)).isEmpty();
        // 최신 확정순 — 방금 확정된 learnerC, 시드 w1(learnerB, 8일 전 확정)
        assertThat(waitlistService.getRecentConfirmed(COURSE_PENCIL_BASIC))
                .extracting(WaitlistDTO::getLoginId).containsExactly("learnerC", "learnerB");
        assertThat(sectionService.getSection(SECTION_PENCIL_A).getRemaining()).isZero();
    }

    @Test
    @DisplayName("만료: 만석 강의의 수강 건이 만료되면 반환된 자리로 대기 1순위가 자동 확정된다")
    void autoConfirmOnExpiry() {
        // e1(learnerB · 강의 1 분반 1 · 정원 1 만석)은 과제를 제출하지 않아 평가 회차가 없다 → 기간만 지나면 만료 대상
        // e1 을 40일 전 확정으로 바꾼다
        entityManager.createNativeQuery("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = :id")
                .setParameter("id", ENROLLMENT_E1)
                .executeUpdate();
        entityManager.clear();

        expiryScheduler.expire();

        entityManager.clear();
        assertThat(enrollmentRepository.findById(ENROLLMENT_E1).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(waitlistService.getWaitlist(COURSE_PENCIL_BASIC)).isEmpty();
        assertThat(waitlistService.getRecentConfirmed(COURSE_PENCIL_BASIC))
                .extracting(WaitlistDTO::getLoginId).containsExactly("learnerC", "learnerB");
    }

    @Test
    @DisplayName("만료 보류: 평가 대기·진행 중인 과제가 있는 수강 건은 기간이 지나도 만료되지 않고 대기열도 그대로다 (05 §4-2)")
    void pendingReviewBlocksExpiry() {
        // e2(r2 평가 대기)·e4(r4 평가 진행 중) — 둘 다 기간만 지난 상태로 만든다
        entityManager.createNativeQuery("UPDATE enrollments SET confirmed_at = UTC_TIMESTAMP(6) - INTERVAL 40 DAY, "
                        + "expires_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id IN (:e2, :e4)")
                .setParameter("e2", ENROLLMENT_E2)
                .setParameter("e4", ENROLLMENT_E4)
                .executeUpdate();
        // 강의 4를 만석(분반 4·5 모두 정원 1 = 활성 1)으로 만들고 learnerA를 대기 1번으로 세운다
        entityManager.createNativeQuery("UPDATE course_sections SET capacity = 1 WHERE course_id = :courseId")
                .setParameter("courseId", COURSE_DIGITAL_BASIC)
                .executeUpdate();
        entityManager.clear();
        waitlistService.join(LEARNER_ID, COURSE_DIGITAL_BASIC);

        expiryScheduler.expire();

        entityManager.clear();
        assertThat(enrollmentRepository.findById(ENROLLMENT_E2).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollmentRepository.findById(ENROLLMENT_E4).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(waitlistService.getWaitlist(COURSE_DIGITAL_BASIC)).hasSize(1);   // 자리가 반환되지 않아 대기자는 그대로
    }

    // ───────────────────────── 대기열 조회 ─────────────────────────

    @Test
    @DisplayName("대기열 조회: 대기 중 학습자를 순번대로, 신청 시각은 엔티티로 읽은 값과 같은 시각으로 보여 준다")
    void waitlist() {
        List<WaitlistDTO> waiting = waitlistService.getWaitlist(COURSE_PENCIL_BASIC);

        assertThat(waiting).hasSize(1);
        WaitlistDTO first = waiting.get(0);
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(first.getLoginId()).isEqualTo("learnerC");
        // native 조회 시각과 JPA 엔티티 시각이 같은지 — 시간대 변환이 어긋나지 않는지 확인
        assertThat(first.getRequestedAt())
                .isEqualTo(waitlistEntryRepository.findById(first.getEntryId()).orElseThrow().getCreatedAt());

        List<WaitlistDTO> confirmed = waitlistService.getRecentConfirmed(COURSE_PENCIL_BASIC);  // 시드 w1: learnerB → 강의 1 A분반 확정
        assertThat(confirmed).extracting(WaitlistDTO::getLoginId).contains("learnerB");
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
