package com.wanted.lmspractice.domain.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.dto.CourseDTO;
import com.wanted.lmspractice.domain.course.service.CourseCatalogService;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.dto.SeatSummaryDTO;
import com.wanted.lmspractice.domain.enrollment.entity.CourseSection;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.policy.EnrollEligibilityPolicy;
import com.wanted.lmspractice.domain.enrollment.policy.SectionAssignPolicy;
import com.wanted.lmspractice.domain.enrollment.repository.CourseSectionRepository;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.notification.service.NotificationService;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import com.wanted.lmspractice.domain.user.entity.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] 성능 개선(1단계) 일괄 조회의 회귀 테스트 — 결과가 기존 강의별 조회와 같은지, 조회 횟수가 강의 수와 무관한지 확인한다
 *        SeatService.getSeatSummaries, EnrollmentService.getStatusesForUser, CourseCatalogService.getCourses(일괄 조회 연결)
 * [전제] DB에 연결하지 않는다(Repository·EntityManager는 Mockito 가짜). 공용·로컬 DB 어느 쪽에도 쿼리를 보내지 않는다.
 *        실제 SQL 문법·뷰(v_section_seats) 결과는 읽기 전용 화면 측정(수정 전후 HTML 비교)으로 따로 확인한다.
 */
class EnrollmentBatchQueryTest {

    private static final Long USER = 2L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 10, 0);

    // ───────────────────────── SeatService.getSeatSummaries ─────────────────────────

    @Test
    @DisplayName("정원 현황 일괄 조회: 강의 수와 관계없이 쿼리 1번(UNION ALL), 분반·대기가 없는 강의는 0으로 채운다")
    void seatSummaries_oneQuery_andZeroDefaults() {
        EntityManager em = mock(EntityManager.class);
        // 첫 칸 kind: 0 = 정원 줄(v_section_seats), 1 = 대기 줄(waitlist_entries). UNION 결과라 숫자 칸은 DECIMAL, id는 BIGINT UNSIGNED
        Query seatQuery = query(
                new Object[] {0L, BigInteger.valueOf(1), new BigDecimal("20"), new BigDecimal("3")},
                new Object[] {0L, BigInteger.valueOf(2), new BigDecimal("10"), new BigDecimal("0")},
                new Object[] {1L, BigInteger.valueOf(2), new BigDecimal("4"), new BigDecimal("0")});
        when(em.createNativeQuery(contains("UNION ALL"))).thenReturn(seatQuery);
        SeatService seatService = seatService(em);

        Map<Long, SeatSummaryDTO> result = seatService.getSeatSummaries(List.of(1L, 2L, 3L));

        assertSeats(result.get(1L), 20, 3, 0);
        assertSeats(result.get(2L), 10, 0, 4);
        assertSeats(result.get(3L), 0, 0, 0);       // 분반(담당 강사)·대기가 없는 강의 — 기존 COALESCE 결과와 같음
        verify(em, times(1)).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("정원 현황 일괄 조회: 빈 목록이면 쿼리를 보내지 않는다(IN () 문법 오류 방지)")
    void seatSummaries_empty_noQuery() {
        EntityManager em = mock(EntityManager.class);
        assertThat(seatService(em).getSeatSummaries(List.of())).isEmpty();
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("기존 getSeatSummary(계약 C16)는 같은 값을 돌려준다")
    void seatSummary_single_sameContract() {
        EntityManager em = mock(EntityManager.class);
        Query seatQuery = query(new Object[] {0L, BigInteger.valueOf(7), new BigDecimal("5"), new BigDecimal("1")});
        when(em.createNativeQuery(contains("UNION ALL"))).thenReturn(seatQuery);

        assertSeats(seatService(em).getSeatSummary(7L), 5, 1, 0);
    }

    // ───────────────────────── EnrollmentService.getStatusesForUser ─────────────────────────

    private EnrollmentRepository enrollmentRepository;
    private CourseSectionRepository courseSectionRepository;
    private WaitlistService waitlistService;
    private SeatService seatService;
    private ReviewQueryService reviewQueryService;
    private EnrollmentService enrollmentService;

    /** 강의 10~17: 수강 중·수강 중(평가 대기)·수료·취소·만료·대기 중·기록 없음(신청 가능)·기록 없음(대기자 있음) + 18 만석, 19 담당 분반 없음 */
    private final Map<Long, Enrollment> enrollmentByCourse = new HashMap<>();
    private final Map<Long, WaitlistEntry> waitingByCourse = new HashMap<>();
    private final Map<Long, SeatSummaryDTO> seatsByCourse = new HashMap<>();
    private final List<Long> allCourses = List.of(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L);

    @BeforeEach
    void setUpEnrollmentFixture() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        courseSectionRepository = mock(CourseSectionRepository.class);
        waitlistService = mock(WaitlistService.class);
        seatService = mock(SeatService.class);
        reviewQueryService = mock(ReviewQueryService.class);
        enrollmentService = new EnrollmentService(courseSectionRepository, enrollmentRepository,
                mock(EnrollEligibilityPolicy.class), mock(SectionAssignPolicy.class), waitlistService, seatService, reviewQueryService);

        enrollmentByCourse.put(10L, enrollment(100L, 10L, EnrollmentStatus.ACTIVE));
        enrollmentByCourse.put(11L, enrollment(101L, 11L, EnrollmentStatus.ACTIVE));
        enrollmentByCourse.put(12L, enrollment(102L, 12L, EnrollmentStatus.COMPLETED));
        enrollmentByCourse.put(13L, enrollment(103L, 13L, EnrollmentStatus.CANCELLED));
        enrollmentByCourse.put(14L, enrollment(104L, 14L, EnrollmentStatus.EXPIRED));
        waitingByCourse.put(15L, waiting(500L, 15L));
        seatsByCourse.put(16L, new SeatSummaryDTO(10, 4, 0));   // 빈자리 있음, 대기 없음 → 신청 가능
        seatsByCourse.put(17L, new SeatSummaryDTO(10, 4, 2));   // 빈자리 있어도 대기자 있음 → 대기 신청 (D-52)
        seatsByCourse.put(18L, new SeatSummaryDTO(10, 0, 0));   // 만석 → 대기 신청
        seatsByCourse.put(19L, new SeatSummaryDTO(0, 0, 0));    // 담당 강사 있는 분반 없음 → 신청 불가

        // 단건 경로(기존 getStatusForUser)와 일괄 경로가 같은 데이터를 보도록 같은 fixture로 응답한다
        when(enrollmentRepository.findByUserIdAndCourseId(eq(USER), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(enrollmentByCourse.get(inv.<Long>getArgument(1))));
        when(enrollmentRepository.findByUserIdAndCourseIdIn(eq(USER), anyCollection()))
                .thenAnswer(inv -> inv.<Collection<Long>>getArgument(1).stream()
                        .filter(enrollmentByCourse::containsKey).map(enrollmentByCourse::get).toList());
        when(waitlistService.findWaiting(eq(USER), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(waitingByCourse.get(inv.<Long>getArgument(1))));
        when(waitlistService.findWaitingByCourseIds(eq(USER), anyCollection()))
                .thenAnswer(inv -> {
                    Map<Long, WaitlistEntry> found = new HashMap<>();
                    inv.<Collection<Long>>getArgument(1).forEach(id -> {
                        if (waitingByCourse.containsKey(id)) {
                            found.put(id, waitingByCourse.get(id));
                        }
                    });
                    return found;
                });
        when(waitlistService.getWaitingNumber(any(WaitlistEntry.class))).thenReturn(3);
        when(seatService.getSeatSummary(anyLong())).thenAnswer(inv -> seatsByCourse.get(inv.<Long>getArgument(0)));
        when(seatService.getSeatSummaries(anyCollection())).thenAnswer(inv -> {
            Map<Long, SeatSummaryDTO> found = new HashMap<>();
            inv.<Collection<Long>>getArgument(0).forEach(id -> found.put(id, seatsByCourse.get(id)));
            return found;
        });
        CourseSection section = mock(CourseSection.class);
        when(section.getDisplayName()).thenReturn("A분반");
        when(courseSectionRepository.findById(anyLong())).thenReturn(Optional.of(section));
        when(reviewQueryService.hasPendingReview(101L)).thenReturn(true);   // 강의 11: 평가 대기 → 수강 취소 막힘
    }

    @Test
    @DisplayName("일괄 신청 상태는 강의마다 기존 getStatusForUser를 부른 결과와 모든 상태에서 같다")
    void statusesForUser_sameAsSingle() {
        Map<Long, EnrollmentStatusDTO> batch = enrollmentService.getStatusesForUser(USER, allCourses, Map.of());

        assertThat(batch).hasSize(allCourses.size());
        for (Long courseId : allCourses) {
            EnrollmentStatusDTO single = enrollmentService.getStatusForUser(USER, courseId);
            assertThat(batch.get(courseId)).as("course %d", courseId).usingRecursiveComparison().isEqualTo(single);
        }
        // 기대값을 사람이 읽을 수 있게 몇 가지는 직접 확인
        assertThat(batch.get(10L).getState()).isEqualTo(EnrollmentStatusDTO.LEARNING);
        assertThat(batch.get(11L).isCancelBlocked()).isTrue();
        assertThat(batch.get(12L).getState()).isEqualTo(EnrollmentStatusDTO.COMPLETED);
        assertThat(batch.get(13L).getState()).isEqualTo(EnrollmentStatusDTO.CANNOT_REAPPLY);
        assertThat(batch.get(14L).getState()).isEqualTo(EnrollmentStatusDTO.CANNOT_REAPPLY);
        assertThat(batch.get(15L).getState()).isEqualTo(EnrollmentStatusDTO.WAITING);
        assertThat(batch.get(15L).getWaitingNumber()).isEqualTo(3);
        assertThat(batch.get(16L).getState()).isEqualTo(EnrollmentStatusDTO.CAN_APPLY);
        assertThat(batch.get(17L).getState()).isEqualTo(EnrollmentStatusDTO.CAN_WAIT);
        assertThat(batch.get(18L).getState()).isEqualTo(EnrollmentStatusDTO.CAN_WAIT);
        assertThat(batch.get(19L).getState()).isEqualTo(EnrollmentStatusDTO.UNAVAILABLE);
    }

    @Test
    @DisplayName("일괄 신청 상태: 수강·대기 기록은 IN 조회 1번씩, 강의별 단건 조회는 하지 않는다")
    void statusesForUser_batchedQueries() {
        enrollmentService.getStatusesForUser(USER, allCourses, Map.of());

        verify(enrollmentRepository, times(1)).findByUserIdAndCourseIdIn(eq(USER), anyCollection());
        verify(enrollmentRepository, never()).findByUserIdAndCourseId(anyLong(), anyLong());
        verify(waitlistService, times(1)).findWaitingByCourseIds(eq(USER), anyCollection());
        verify(waitlistService, never()).findWaiting(anyLong(), anyLong());
        verify(seatService, times(1)).getSeatSummaries(anyCollection());
        verify(seatService, never()).getSeatSummary(anyLong());
    }

    @Test
    @DisplayName("일괄 신청 상태: 호출한 쪽이 넘긴 정원 현황은 다시 읽지 않는다")
    void statusesForUser_reusesKnownSeats() {
        Map<Long, SeatSummaryDTO> known = new HashMap<>(seatsByCourse);  // 16~19 모두 이미 읽음

        Map<Long, EnrollmentStatusDTO> batch = enrollmentService.getStatusesForUser(USER, allCourses, known);

        assertThat(batch.get(16L).getState()).isEqualTo(EnrollmentStatusDTO.CAN_APPLY);
        verify(seatService).getSeatSummaries(List.of());   // 빠진 강의가 없으면 빈 목록 → SeatService가 쿼리를 보내지 않는다
        verify(seatService, never()).getSeatSummary(anyLong());
    }

    @Test
    @DisplayName("일괄 신청 상태: 빈 목록이면 아무것도 조회하지 않는다")
    void statusesForUser_empty() {
        assertThat(enrollmentService.getStatusesForUser(USER, List.of(), Map.of())).isEmpty();
        verifyNoInteractions(enrollmentRepository, waitlistService, seatService);
    }

    // ───────────────────────── CourseCatalogService.getCourses ─────────────────────────

    @Test
    @DisplayName("강의 목록(비회원): 정원 현황을 한 번에 읽고, 수강 도메인에는 묻지 않으며, 카드마다 다른 상태 객체를 쓴다")
    void catalog_guest_batchedSeats() {
        CatalogFixture f = new CatalogFixture(true);

        List<CourseDTO> courses = f.catalog.getCourses(1L, null, null, null);

        verify(f.seatService, times(1)).getSeatSummaries(List.of(1L, 2L, 3L));
        verify(f.seatService, never()).getSeatSummary(anyLong());
        verifyNoInteractions(f.enrollmentService);
        assertThat(courses).extracting(c -> c.getEnrollStatus().getState()).containsOnly(EnrollmentStatusDTO.LOGIN_REQUIRED);
        assertThat(courses.get(0).getEnrollStatus()).isNotSameAs(courses.get(1).getEnrollStatus());
        assertThat(courses.get(0).getSeats().getRemainingSeats()).isEqualTo(5);
    }

    @Test
    @DisplayName("강의 목록(학습자): 신청 상태를 한 번에 묻고, 이미 읽은 정원 현황을 넘긴다")
    void catalog_learner_batchedStatuses() {
        CatalogFixture f = new CatalogFixture(true);
        Map<Long, EnrollmentStatusDTO> statuses = new HashMap<>();
        statuses.put(1L, EnrollmentStatusDTO.of(EnrollmentStatusDTO.CAN_APPLY));
        statuses.put(2L, EnrollmentStatusDTO.of(EnrollmentStatusDTO.CAN_WAIT));
        statuses.put(3L, EnrollmentStatusDTO.of(EnrollmentStatusDTO.CANNOT_REAPPLY));
        when(f.enrollmentService.getStatusesForUser(eq(USER), eq(List.of(1L, 2L, 3L)), any())).thenReturn(statuses);

        List<CourseDTO> courses = f.catalog.getCourses(1L, null, USER, Role.USER);

        verify(f.enrollmentService, times(1)).getStatusesForUser(USER, List.of(1L, 2L, 3L), f.seats);
        verify(f.enrollmentService, never()).getStatusForUser(anyLong(), anyLong());
        assertThat(courses).extracting(c -> c.getEnrollStatus().getState())
                .containsExactly(EnrollmentStatusDTO.CAN_APPLY, EnrollmentStatusDTO.CAN_WAIT, EnrollmentStatusDTO.CANNOT_REAPPLY);
    }

    @Test
    @DisplayName("강의 목록: 준비 중 취미는 누구에게나 신청 불가, 강사·관리자는 학습자 전용 (기존 노출 규칙 유지)")
    void catalog_fixedStates() {
        CatalogFixture preparing = new CatalogFixture(false);
        assertThat(preparing.catalog.getCourses(1L, null, USER, Role.USER))
                .extracting(c -> c.getEnrollStatus().getState()).containsOnly(EnrollmentStatusDTO.UNAVAILABLE);

        CatalogFixture operating = new CatalogFixture(true);
        assertThat(operating.catalog.getCourses(1L, null, 6L, Role.INSTRUCTOR))
                .extracting(c -> c.getEnrollStatus().getState()).containsOnly(EnrollmentStatusDTO.NOT_LEARNER);
        verifyNoInteractions(preparing.enrollmentService, operating.enrollmentService);
    }

    /** CourseCatalogService와 가짜 의존성 — 취미 1의 강의 1·2·3 (차시·진도 조회가 필요 없는 상태만 사용) */
    private static class CatalogFixture {
        final SeatService seatService = mock(SeatService.class);
        final EnrollmentService enrollmentService = mock(EnrollmentService.class);
        final Map<Long, SeatSummaryDTO> seats = new HashMap<>();
        final CourseCatalogService catalog;

        CatalogFixture(boolean hobbyOperating) {
            HobbyService hobbyService = mock(HobbyService.class);
            CourseService courseService = mock(CourseService.class);
            HobbyDTO hobby = mock(HobbyDTO.class);
            when(hobby.isOperating()).thenReturn(hobbyOperating);
            when(hobbyService.getHobbySummary(eq(1L), any())).thenReturn(hobby);
            List<CourseDTO> courses = new ArrayList<>();
            for (long id = 1; id <= 3; id++) {
                CourseDTO course = new CourseDTO();
                course.setId(id);
                course.setHobbyId(1L);
                courses.add(course);
                seats.put(id, new SeatSummaryDTO(10, 5, 0));
            }
            when(courseService.getCourses(eq(1L), any(), any())).thenReturn(courses);
            when(seatService.getSeatSummaries(anyCollection())).thenReturn(seats);
            catalog = new CourseCatalogService(hobbyService, courseService, seatService, enrollmentService, mock(ProgressService.class));
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private static SeatService seatService(EntityManager em) {
        SeatService service = new SeatService(mock(CourseSectionRepository.class), mock(EnrollmentRepository.class),
                mock(WaitlistEntryRepository.class), mock(SectionAssignPolicy.class), mock(NotificationService.class),
                mock(CourseService.class));
        ReflectionTestUtils.setField(service, "entityManager", em);
        return service;
    }

    /** native 조회 결과 행(Object[])들을 돌려주는 가짜 Query — 행 배열이 varargs로 풀리지 않도록 직접 담는다 */
    private static Query query(Object[]... resultRows) {
        List<Object> rows = new ArrayList<>();
        for (Object[] row : resultRows) {
            rows.add(row);
        }
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }

    private static void assertSeats(SeatSummaryDTO seats, int capacity, int remaining, int waiting) {
        assertThat(seats.getTotalCapacity()).isEqualTo(capacity);
        assertThat(seats.getRemainingSeats()).isEqualTo(remaining);
        assertThat(seats.getWaitingCount()).isEqualTo(waiting);
    }

    private static Enrollment enrollment(Long id, Long courseId, EnrollmentStatus status) {
        Enrollment e = new Enrollment();
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "userId", USER);
        ReflectionTestUtils.setField(e, "courseId", courseId);
        ReflectionTestUtils.setField(e, "sectionId", 1L);
        ReflectionTestUtils.setField(e, "status", status);
        ReflectionTestUtils.setField(e, "confirmedAt", T0);
        ReflectionTestUtils.setField(e, "expiresAt", T0.plusDays(30));
        return e;
    }

    private static WaitlistEntry waiting(Long id, Long courseId) {
        WaitlistEntry entry = WaitlistEntry.create(USER, courseId);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "createdAt", T0);
        return entry;
    }
}
