package com.wanted.lmspractice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.repository.CourseRepository;
import com.wanted.lmspractice.domain.course.repository.LessonRepository;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionSummaryDTO;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery;
import com.wanted.lmspractice.domain.instructor.service.InstructorDashboardService;
import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.MyCourseDTO;
import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import com.wanted.lmspractice.domain.learning.policy.VideoAccessPolicy;
import com.wanted.lmspractice.domain.learning.repository.LessonProgressRepository;
import com.wanted.lmspractice.domain.learning.service.MyLearningService;
import com.wanted.lmspractice.domain.learning.service.ProgressService;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.entity.ReviewRound;
import com.wanted.lmspractice.domain.review.entity.ReviewStatus;
import com.wanted.lmspractice.domain.review.repository.ReviewRoundRepository;
import com.wanted.lmspractice.domain.review.service.BadgeService;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDetailDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.policy.CommentPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkBoardQuery;
import com.wanted.lmspractice.domain.work.repository.WorkCommentRepository;
import com.wanted.lmspractice.domain.work.repository.WorkLikeRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.service.WorkBoardService;
import com.wanted.lmspractice.domain.work.service.WorkService;
import com.wanted.lmspractice.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] 성능 개선(3단계) 회귀 테스트 — 반복 조회를 일괄 조회로 바꾼 뒤에도 결과가 기존 단건 계약과 같은지, 조회 횟수가 줄었는지 확인한다
 *        ProgressService·ReviewQueryService·CourseService 일괄 조회, MyLearningService, CommentPolicy·WorkBoardService, InstructorDashboardService
 * [전제] DB·Spring 컨텍스트 없이 Mockito 가짜만 쓴다. Repository 메서드 호출 1번 = SQL 1번으로 센다.
 */
class ServiceQueryReductionTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 10, 0);

    // ───────────────────────── ProgressService.getProgressByEnrollmentIds ─────────────────────────

    /** 강의 1: 차시 11·12·13, 강의 2: 차시 21·22·23 */
    private final Map<Long, List<Lesson>> lessons = Map.of(
            1L, List.of(lesson(11L, 1L, 1), lesson(12L, 1L, 2), lesson(13L, 1L, 3)),
            2L, List.of(lesson(21L, 2L, 1), lesson(22L, 2L, 2), lesson(23L, 2L, 3)));

    /** 수강 100: 2차시 완료 + 1차시 진행, 101: 3차시 모두 완료, 102: 재생 기록 없음, 103: 1차시만 진행 */
    private Map<Long, List<LessonProgress>> progressFixture() {
        Map<Long, List<LessonProgress>> rows = new HashMap<>();
        rows.put(100L, List.of(progress(100L, 12L, 1L, true), progress(100L, 11L, 1L, true), progress(100L, 13L, 1L, false)));
        rows.put(101L, List.of(progress(101L, 11L, 1L, true), progress(101L, 12L, 1L, true), progress(101L, 13L, 1L, true)));
        rows.put(102L, List.of());
        rows.put(103L, List.of(progress(103L, 21L, 2L, false)));
        return rows;
    }

    private ProgressService progressService(LessonProgressRepository repo, CourseService courseService) {
        Map<Long, List<LessonProgress>> rows = progressFixture();
        when(repo.findByEnrollmentId(anyLong())).thenAnswer(inv -> rows.getOrDefault(inv.<Long>getArgument(0), List.of()));
        when(repo.countByEnrollmentIdAndCompletedAtIsNotNull(anyLong())).thenAnswer(inv ->
                rows.getOrDefault(inv.<Long>getArgument(0), List.of()).stream().filter(LessonProgress::isCompleted).count());
        when(repo.findByEnrollmentIdIn(anyCollection())).thenAnswer(inv -> inv.<Collection<Long>>getArgument(0).stream()
                .flatMap(id -> rows.getOrDefault(id, List.of()).stream()).toList());
        when(courseService.getLessonsOfCourse(anyLong())).thenAnswer(inv -> lessons.get(inv.<Long>getArgument(0)));
        return new ProgressService(repo, mock(EnrollmentService.class), courseService, new VideoAccessPolicy());
    }

    @Test
    @DisplayName("진도 일괄 조회 = 수강마다 getLessonStatuses + isAllCompleted(계약 C10)를 부른 결과, 진도 조회 1번·차시 재조회 없음")
    void progressBatch_sameAsContract() {
        LessonProgressRepository repo = mock(LessonProgressRepository.class);
        CourseService courseService = mock(CourseService.class);
        ProgressService service = progressService(repo, courseService);
        List<Long> ids = List.of(100L, 101L, 102L, 103L);

        Map<Long, ProgressService.EnrollmentProgress> batch = service.getProgressByEnrollmentIds(ids, lessons);

        verify(repo, times(1)).findByEnrollmentIdIn(anyCollection());
        verify(repo, never()).findByEnrollmentId(anyLong());
        verify(repo, never()).countByEnrollmentIdAndCompletedAtIsNotNull(anyLong());
        verify(courseService, never()).getLessonsOfCourse(anyLong());   // 넘겨받은 차시를 다시 읽지 않는다
        for (Long id : ids) {
            assertThat(batch.get(id).lessons()).as("lessons of %d", id)
                    .usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(service.getLessonStatuses(id));
            assertThat(batch.get(id).allCompleted()).as("allCompleted of %d", id).isEqualTo(service.isAllCompleted(id));
        }
        assertThat(batch.get(101L).allCompleted()).isTrue();
        assertThat(batch.get(100L).lessons()).extracting("lessonId").containsExactly(11L, 12L, 13L);   // 차시 번호 순서
        assertThat(batch.get(102L).lessons()).isEmpty();
    }

    @Test
    @DisplayName("진도 일괄 조회: 넘겨받지 못한 강의의 차시만 따로 읽는다 / 빈 목록이면 조회 없음")
    void progressBatch_fallbackAndEmpty() {
        LessonProgressRepository repo = mock(LessonProgressRepository.class);
        CourseService courseService = mock(CourseService.class);
        ProgressService service = progressService(repo, courseService);

        Map<Long, ProgressService.EnrollmentProgress> batch = service.getProgressByEnrollmentIds(List.of(103L), Map.of());
        assertThat(batch.get(103L).lessons()).extracting("lessonId").containsExactly(21L);
        verify(courseService, times(1)).getLessonsOfCourse(2L);

        clearInvocations(repo);
        assertThat(service.getProgressByEnrollmentIds(List.of(), lessons)).isEmpty();
        verifyNoInteractions(repo);
    }

    // ───────────────────────── ReviewQueryService.getLatestStatuses ─────────────────────────

    @Test
    @DisplayName("평가 상태 일괄 조회 = 수강마다 getLatestStatus(계약 C11) 결과 (최신 회차 + 1회차 제출 시각, 미제출), 조회 1번")
    void reviewBatch_sameAsContract() {
        Map<Long, List<ReviewRound>> rounds = new HashMap<>();
        rounds.put(200L, List.of(round(1L, 200L, 2, ReviewStatus.WAITING, T0.plusDays(3)),
                round(2L, 200L, 1, ReviewStatus.REJECTED, T0)));            // 2회차가 최신, 1회차 제출 시각 = T0
        rounds.put(201L, List.of(round(3L, 201L, 1, ReviewStatus.APPROVED, T0.plusDays(1))));
        rounds.put(202L, List.of());                                       // 미제출
        ReviewRoundRepository repo = mock(ReviewRoundRepository.class);
        when(repo.findFirstByEnrollmentIdOrderByRoundNoDesc(anyLong())).thenAnswer(inv -> rounds.get(inv.<Long>getArgument(0)).stream()
                .max((a, b) -> a.getRoundNo() - b.getRoundNo()));
        when(repo.findFirstByEnrollmentIdOrderByRoundNoAsc(anyLong())).thenAnswer(inv -> rounds.get(inv.<Long>getArgument(0)).stream()
                .min((a, b) -> a.getRoundNo() - b.getRoundNo()));
        when(repo.findByEnrollmentIdIn(anyCollection())).thenAnswer(inv -> inv.<Collection<Long>>getArgument(0).stream()
                .flatMap(id -> rounds.get(id).stream()).toList());
        ReviewQueryService service = new ReviewQueryService(repo, mock(CourseService.class));
        List<Long> ids = List.of(200L, 201L, 202L);

        Map<Long, ReviewStatusDTO> batch = service.getLatestStatuses(ids);

        verify(repo, times(1)).findByEnrollmentIdIn(anyCollection());
        verify(repo, never()).findFirstByEnrollmentIdOrderByRoundNoDesc(anyLong());
        for (Long id : ids) {
            assertThat(batch.get(id)).as("review of %d", id).usingRecursiveComparison().isEqualTo(service.getLatestStatus(id));
        }
        assertThat(batch.get(200L).getStatus()).isEqualTo(ReviewStatus.WAITING);
        assertThat(batch.get(200L).getFirstSubmittedAt()).isEqualTo(T0);
        assertThat(batch.get(202L).getStatus()).isNull();
    }

    // ───────────────────────── CourseService.getLessonsOfCourses ─────────────────────────

    @Test
    @DisplayName("차시 일괄 조회: 강의별로 차시 번호 순서, 차시가 없는 강의는 빈 목록, 조회 1번")
    void lessonsBatch() {
        LessonRepository repo = mock(LessonRepository.class);
        List<Lesson> all = new ArrayList<>(lessons.get(1L));
        all.addAll(lessons.get(2L));
        when(repo.findByCourseIdInOrderByCourseIdAscLessonNoAsc(anyCollection())).thenReturn(all);
        CourseService service = new CourseService(mock(CourseRepository.class), repo, mock(HobbyService.class));

        Map<Long, List<Lesson>> result = service.getLessonsOfCourses(List.of(1L, 2L, 3L));

        assertThat(result.get(1L)).containsExactlyElementsOf(lessons.get(1L));
        assertThat(result.get(2L)).containsExactlyElementsOf(lessons.get(2L));
        assertThat(result.get(3L)).isEmpty();
        verify(repo, times(1)).findByCourseIdInOrderByCourseIdAscLessonNoAsc(anyCollection());
        assertThat(service.getLessonsOfCourses(List.of())).isEmpty();
        verify(repo, times(1)).findByCourseIdInOrderByCourseIdAscLessonNoAsc(anyCollection());
    }

    // ───────────────────────── MyLearningService ─────────────────────────

    @Test
    @DisplayName("나의 학습 현황: 수강 카드마다 차시·진도·평가를 따로 읽지 않고 종류별 1번, 카드 값은 일괄 결과로 채운다")
    void myLearning_batchedPerKind() {
        WaitlistService waitlistService = mock(WaitlistService.class);
        EnrollmentService enrollmentService = mock(EnrollmentService.class);
        CourseService courseService = mock(CourseService.class);
        ProgressService progressService = mock(ProgressService.class);
        ReviewQueryService reviewQueryService = mock(ReviewQueryService.class);
        MyLearningService service = new MyLearningService(waitlistService, enrollmentService, courseService,
                progressService, reviewQueryService);

        when(waitlistService.getMyWaiting(2L)).thenReturn(List.of());
        when(enrollmentService.getMyEnrollments(2L)).thenReturn(List.of(
                myEnrollment(100L, 1L, EnrollmentStatus.ACTIVE),
                myEnrollment(101L, 2L, EnrollmentStatus.COMPLETED),
                myEnrollment(102L, 9L, EnrollmentStatus.ACTIVE)));      // 강의 정보가 없는 수강 — 카드에서 빠진다(기존 규칙)
        Map<Long, CourseBriefDTO> briefs = new HashMap<>();
        briefs.put(1L, new CourseBriefDTO(1L, 1L, "색연필화", true, CourseLevel.BASIC, "강의1"));
        briefs.put(2L, new CourseBriefDTO(2L, 1L, "색연필화", true, CourseLevel.INTERMEDIATE, "강의2"));
        when(courseService.getCourseBriefs(anyCollection())).thenReturn(briefs);
        when(courseService.getLessonsOfCourses(anyCollection())).thenReturn(lessons);
        Map<Long, ProgressService.EnrollmentProgress> progress = new HashMap<>();
        progress.put(100L, new ProgressService.EnrollmentProgress(List.of(), true));
        progress.put(101L, new ProgressService.EnrollmentProgress(List.of(), false));
        when(progressService.getProgressByEnrollmentIds(anyCollection(), any())).thenReturn(progress);
        Map<Long, ReviewStatusDTO> reviews = new HashMap<>();
        reviews.put(100L, ReviewStatusDTO.notSubmitted());
        reviews.put(101L, ReviewStatusDTO.notSubmitted());
        when(reviewQueryService.getLatestStatuses(anyCollection())).thenReturn(reviews);

        List<MyCourseDTO> cards = service.getMyLearning(2L, LearningFilter.ALL).getCourses();

        assertThat(cards).extracting(MyCourseDTO::getEnrollmentId).containsExactly(100L, 101L);
        assertThat(cards.get(0).isAllLessonsCompleted()).isTrue();          // ACTIVE 카드: EnrollmentProgress.allCompleted
        assertThat(cards.get(0).getLessons()).hasSize(3);                    // 기록 없는 차시도 0%로 채워 3차시 모두 보인다
        assertThat(cards.get(0).getReview()).isSameAs(reviews.get(100L));
        verify(courseService, times(1)).getLessonsOfCourses(List.of(1L, 2L));
        verify(progressService, times(1)).getProgressByEnrollmentIds(List.of(100L, 101L), lessons);
        verify(reviewQueryService, times(1)).getLatestStatuses(List.of(100L, 101L));
        verify(courseService, never()).getLessonsOfCourse(anyLong());
        verify(progressService, never()).getLessonStatuses(anyLong());
        verify(progressService, never()).isAllCompleted(anyLong());
        verify(reviewQueryService, never()).getLatestStatus(anyLong());
    }

    // ───────────────────────── CommentPolicy + WorkBoardService.getDetail ─────────────────────────

    @Test
    @DisplayName("답글 자격 오버로드는 기존 canWriteReply와 모든 역할·작성자 조합에서 같다")
    void commentPolicy_overloadSame() {
        BadgeService badgeService = mock(BadgeService.class);
        InstructorAccountService instructorAccountService = mock(InstructorAccountService.class);
        CommentPolicy policy = new CommentPolicy(badgeService, instructorAccountService);
        Work work = work(7L, 1L, CourseLevel.INTERMEDIATE);
        when(badgeService.getHighestLevel(eq(3L), eq(1L))).thenReturn(CourseLevel.ADVANCED);   // 3: 배지 있음
        when(instructorAccountService.hasHobbyGrant(6L, 1L)).thenReturn(true);
        for (Long userId : List.of(2L, 3L, 4L, 6L, 9L)) {
            for (Role role : new Role[] {Role.USER, Role.INSTRUCTOR, Role.ADMIN, null}) {
                boolean old = policy.canWriteReply(work, userId, role);
                boolean reused = policy.canWriteReply(work, userId, role, policy.canWriteComment(work, userId, role));
                assertThat(reused).as("user %d role %s", userId, role).isEqualTo(old);
            }
        }
    }

    @Test
    @DisplayName("작품 상세(학습자): 배지 계산을 댓글·답글 자격에서 1번만 한다 (기존 2번)")
    void workDetail_badgeOnce() {
        BadgeService badgeService = mock(BadgeService.class);
        when(badgeService.getHighestLevel(3L, 1L)).thenReturn(CourseLevel.ADVANCED);
        WorkBoardFixture f = new WorkBoardFixture(new CommentPolicy(badgeService, mock(InstructorAccountService.class)));
        Work work = work(2L, 1L, CourseLevel.BASIC);   // 작성자 2, 보는 사람 3
        when(f.workService.getWork(50L, 3L, Role.USER)).thenReturn(new WorkDTO());
        when(f.workRepository.findById(50L)).thenReturn(Optional.of(work));
        when(f.workCommentRepository.findByWorkIdOrderById(50L)).thenReturn(List.of());
        when(f.userService.getPublicNames(anyCollection())).thenReturn(Map.of());
        when(f.hobbyService.getHobbiesIgnoringVisibility(anyCollection())).thenReturn(Map.of());

        WorkDetailDTO detail = f.service.getDetail(50L, 3L, Role.USER);

        assertThat(detail.isCanComment()).isTrue();
        assertThat(detail.isCanReply()).isTrue();
        verify(badgeService, times(1)).getHighestLevel(3L, 1L);
    }

    // ───────────────────────── WorkBoardService 강사 담당 취미 ─────────────────────────

    @Test
    @DisplayName("강사 게시판: 담당 취미를 취미마다 hasHobbyGrant로 묻지 않고 1번에 읽는다 — 결과는 같은 기준(보이는 취미 ∩ 활동 권한)")
    void workBoard_instructorGrants() {
        WorkBoardFixture f = new WorkBoardFixture(mock(CommentPolicy.class));
        // 강사에게 보이는 취미: 대분류 1(취미 1·2), 대분류 3(취미 4). 활동 권한: 1·4·8(8은 보이지 않는 비공개 취미)
        when(f.hobbyService.getCategories(Role.INSTRUCTOR)).thenReturn(List.of(
                category(1L, hobby(1L, "색연필화"), hobby(2L, "디지털 드로잉")), category(3L, hobby(4L, "목공"))));
        when(f.instructorAccountService.getActiveGrantHobbyIds(6L)).thenReturn(Set.of(1L, 4L, 8L));
        when(f.workBoardQuery.find(any(), any(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(f.hobbyService.getHobbiesIgnoringVisibility(anyCollection())).thenReturn(Map.of());

        assertThat(f.service.getGrantedHobbyNames(6L)).containsExactly("색연필화", "목공");

        WorkSearchDTO cond = new WorkSearchDTO();
        cond.setCategoryId(1L);   // 대분류 1 ∩ 내 담당(1·4) = 취미 1
        f.service.search(cond, 6L, Role.INSTRUCTOR);
        verify(f.workBoardQuery).count(eq(cond), eq(List.of(1L)), eq(6L));

        verify(f.instructorAccountService, never()).hasHobbyGrant(anyLong(), anyLong());
        verify(f.instructorAccountService, times(2)).getActiveGrantHobbyIds(6L);   // 화면 메서드마다 1번 (예전: 취미 수만큼)
        verify(f.hobbyService, times(2)).getCategories(Role.INSTRUCTOR);         // 예전: getGrantedHobbyNames 2번 + search 2번
    }

    /** WorkBoardService와 가짜 의존성 */
    private static class WorkBoardFixture {
        final WorkBoardQuery workBoardQuery = mock(WorkBoardQuery.class);
        final WorkRepository workRepository = mock(WorkRepository.class);
        final WorkCommentRepository workCommentRepository = mock(WorkCommentRepository.class);
        final WorkService workService = mock(WorkService.class);
        final UserService userService = mock(UserService.class);
        final HobbyService hobbyService = mock(HobbyService.class);
        final InstructorAccountService instructorAccountService = mock(InstructorAccountService.class);
        final WorkBoardService service;

        WorkBoardFixture(CommentPolicy commentPolicy) {
            service = new WorkBoardService(workBoardQuery, workRepository, workCommentRepository, mock(WorkLikeRepository.class),
                    workService, commentPolicy, userService, hobbyService, instructorAccountService,
                    mock(com.wanted.lmspractice.domain.game.service.AuthorStyleService.class));
        }
    }

    // ───────────────────────── InstructorDashboardService ─────────────────────────

    @Test
    @DisplayName("학습자 관리: 이미 읽은 담당 분반 목록으로 소유를 확인해 담당 분반 집계를 다시 하지 않는다 — 남의 분반은 그대로 SECTION_NOT_FOUND")
    void instructorStudents_reuseSections() {
        InstructorStatQuery query = mock(InstructorStatQuery.class);
        when(query.findStudents(anyLong(), any(), any())).thenReturn(List.of());
        InstructorDashboardService service = new InstructorDashboardService(query);
        SectionSummaryDTO mine = new SectionSummaryDTO();
        mine.setSectionId(31L);
        List<SectionSummaryDTO> assigned = List.of(mine);

        assertThat(service.getStudents(6L, assigned, 31L, null, null)).isEmpty();
        assertThat(service.getStudents(6L, assigned, null, null, null)).isEmpty();
        assertThatThrownBy(() -> service.getStudents(6L, assigned, 99L, null, null)).isInstanceOf(BusinessException.class)
                .hasMessage(new BusinessException(com.wanted.lmspractice.global.exception.ErrorCode.SECTION_NOT_FOUND).getMessage());
        verify(query, never()).findSectionStats(anyLong(), any());

        // 기존 메서드(시그니처 유지)는 그대로 집계로 확인한다 — 같은 결과
        SectionStatDTO myStat = sectionStat(31L);   // 가짜 stub 안에서 다른 stub을 만들지 않도록 먼저 만든다
        when(query.findSectionStats(eq(6L), any())).thenReturn(List.of(myStat));
        assertThat(service.getStudents(6L, 31L, null, null)).isEmpty();
        assertThatThrownBy(() -> service.getStudents(6L, 99L, null, null)).isInstanceOf(BusinessException.class);
    }

    // ───────────────────────── fixtures ─────────────────────────

    private static Lesson lesson(Long id, Long courseId, int no) {
        Lesson lesson = new Lesson();
        ReflectionTestUtils.setField(lesson, "id", id);
        ReflectionTestUtils.setField(lesson, "courseId", courseId);
        ReflectionTestUtils.setField(lesson, "lessonNo", no);
        ReflectionTestUtils.setField(lesson, "title", no + "차시");
        ReflectionTestUtils.setField(lesson, "durationSec", new BigDecimal("600"));
        return lesson;
    }

    private static LessonProgress progress(Long enrollmentId, Long lessonId, Long courseId, boolean completed) {
        LessonProgress p = LessonProgress.create(enrollmentId, lessonId, courseId);
        if (completed) {
            ReflectionTestUtils.setField(p, "completedAt", T0);
        }
        return p;
    }

    private static ReviewRound round(Long id, Long enrollmentId, int roundNo, ReviewStatus status, LocalDateTime submittedAt) {
        ReviewRound round = ReviewRound.create(enrollmentId, 1L, roundNo, submittedAt);
        ReflectionTestUtils.setField(round, "id", id);
        ReflectionTestUtils.setField(round, "status", status);
        return round;
    }

    private static MyEnrollmentDTO myEnrollment(Long enrollmentId, Long courseId, EnrollmentStatus status) {
        MyEnrollmentDTO dto = new MyEnrollmentDTO();
        dto.setEnrollmentId(enrollmentId);
        dto.setCourseId(courseId);
        dto.setStatus(status);
        dto.setSectionName("A분반");
        dto.setConfirmedAt(T0);
        dto.setExpiresAt(LocalDateTime.now().plusDays(10));
        return dto;
    }

    private static Work work(Long authorId, Long hobbyId, CourseLevel level) {
        Work work = new Work();
        ReflectionTestUtils.setField(work, "id", 50L);
        ReflectionTestUtils.setField(work, "authorId", authorId);
        ReflectionTestUtils.setField(work, "hobbyId", hobbyId);
        ReflectionTestUtils.setField(work, "level", level);
        return work;
    }

    private static HobbyDTO hobby(Long id, String name) {
        HobbyDTO hobby = new HobbyDTO();
        hobby.setId(id);
        hobby.setName(name);
        return hobby;
    }

    private static CategoryDTO category(Long id, HobbyDTO... hobbies) {
        CategoryDTO category = new CategoryDTO();
        category.setId(id);
        category.setHobbies(List.of(hobbies));
        return category;
    }

    private static SectionStatDTO sectionStat(Long sectionId) {
        SectionStatDTO stat = mock(SectionStatDTO.class);
        when(stat.getSectionId()).thenReturn(sectionId);
        return stat;
    }
}
