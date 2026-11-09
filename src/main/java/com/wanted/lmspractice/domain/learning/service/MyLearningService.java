package com.wanted.lmspractice.domain.learning.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.dto.MyWaitlistDTO;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.enrollment.service.WaitlistService;
import com.wanted.lmspractice.domain.learning.dto.LearningFilter;
import com.wanted.lmspractice.domain.learning.dto.LearningState;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.dto.MyCourseDTO;
import com.wanted.lmspractice.domain.learning.dto.MyLearningDTO;
import com.wanted.lmspractice.domain.learning.policy.ContinueLessonPolicy;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 한 학습자의 대기·수강 기록에 강의 정보·차시 진도·평가 상태를 붙여 "나의 학습 현황" 카드 목록으로 조립한다 (조회 전용)
 * [담당] 팀원 2 · #11   [화면] U-S08   [기획] FR-029, D-20·D-41·D-42·D-53·D-55   [API] ML-01
 * [연결]
 *  - 호출받음: MyLearningController.list (GET /my/learning)
 *  - 호출함:   WaitlistService.getMyWaiting(대기·대기 번호), EnrollmentService.getMyEnrollments(수강 건·분반),
 *             CourseService.getCourseBriefs(강의명·취미), ProgressService.getLessonStatuses·isAllCompleted(차시 진도 — 계약 C10, #12),
 *             ReviewQueryService.getLatestStatus(최종 과제 평가 상태 — 계약 C11, #14)
 *  - 본인만:   userId는 로그인 정보에서만 받고, 수강·대기 조회가 모두 userId로 걸러진다. 다른 학습자의 기록은 섞일 수 없다.
 *  - 진도·평가는 #12·#14가 구현되기 전까지 스텁(빈 진도·미제출)이라 그 전에는 "학습 중"으로만 보인다.
 * [기술] 🟢 @Service·@Transactional(readOnly)·DTO 조립. 다른 도메인 데이터는 모두 그 도메인의 Service로만 읽는다
 */
@Service
@RequiredArgsConstructor
public class MyLearningService {

    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    private final WaitlistService waitlistService;
    private final EnrollmentService enrollmentService;
    private final CourseService courseService;
    private final ProgressService progressService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 본인의 학습 현황 카드 목록 (이슈 입출력: getMyCourses(userId, 필터) → List&lt;MyCourseDTO&gt;).
     * 호출: 필요한 곳에서 목록만 쓸 때. 화면은 요약 숫자도 필요하므로 getMyLearning을 쓴다.
     */
    @Transactional(readOnly = true)
    public List<MyCourseDTO> getMyCourses(Long userId, LearningFilter filter) {
        return getMyLearning(userId, filter).getCourses();
    }

    /**
     * 나의 학습 현황 한 화면 — 선택한 탭의 카드 목록 + 위쪽 요약 숫자(전체 카드 기준).
     * 호출: MyLearningController.list (GET /my/learning?status=)
     * 순서: 진행 중·대기 카드를 먼저, 끝난(수료·만료·취소) 카드를 뒤에. 같은 묶음 안에서는 최근 확정 순이다.
     */
    @Transactional(readOnly = true)
    public MyLearningDTO getMyLearning(Long userId, LearningFilter filter) {
        LocalDateTime now = LocalDateTime.now();  // 시간 규칙: LocalDateTime.now() (docs/CONVENTIONS.md §9)
        List<MyWaitlistDTO> waiting = waitlistService.getMyWaiting(userId);
        List<MyEnrollmentDTO> enrollments = enrollmentService.getMyEnrollments(userId);

        Set<Long> courseIds = new HashSet<>();
        waiting.forEach(entry -> courseIds.add(entry.getCourseId()));
        enrollments.forEach(enrollment -> courseIds.add(enrollment.getCourseId()));
        Map<Long, CourseBriefDTO> briefs = courseService.getCourseBriefs(courseIds);

        List<MyCourseDTO> cards = new ArrayList<>();
        for (MyWaitlistDTO entry : waiting) {
            CourseBriefDTO brief = briefs.get(entry.getCourseId());
            if (brief != null) {
                cards.add(toWaitingCard(entry, brief));
            }
        }
        for (MyEnrollmentDTO enrollment : enrollments) {
            CourseBriefDTO brief = briefs.get(enrollment.getCourseId());
            if (brief != null) {
                cards.add(toEnrollmentCard(enrollment, brief, now));
            }
        }
        // List.sort는 안정 정렬이라 같은 묶음 안의 기존 순서(대기: 신청순, 수강: 최근 확정순)가 유지된다
        cards.sort(Comparator.comparingInt(card -> isOngoing(card.getState()) ? 0 : 1));
        return MyLearningDTO.of(cards, filter);
    }

    /** 대기 중 카드: 강의 소개 + 대기 번호·신청 시각 + [대기 취소]용 waitlistId */
    private MyCourseDTO toWaitingCard(MyWaitlistDTO entry, CourseBriefDTO brief) {
        MyCourseDTO card = baseCard(brief, LearningState.WAITING);
        card.setWaitlistId(entry.getWaitlistId());
        card.setWaitingNumber(entry.getWaitingNumber());
        card.setWaitlistAppliedAt(entry.getAppliedAt());
        return card;
    }

    /** 수강 건 카드: 강의 소개 + 분반·기간 + 차시 진도 + 평가 상태로 화면 상태를 정한다 */
    private MyCourseDTO toEnrollmentCard(MyEnrollmentDTO enrollment, CourseBriefDTO brief, LocalDateTime now) {
        Long enrollmentId = enrollment.getEnrollmentId();
        List<Lesson> courseLessons = courseService.getLessonsOfCourse(brief.getCourseId());  // 1·2·3차시 순서
        List<LessonProgressDTO> progress = progressService.getLessonStatuses(enrollmentId);
        List<LessonProgressDTO> lessons = withEveryLesson(courseLessons, progress);
        ReviewStatusDTO review = reviewQueryService.getLatestStatus(enrollmentId);
        boolean anyLessonCompleted = lessons.stream().anyMatch(LessonProgressDTO::isCompleted);

        LearningState state = LearningState.derive(
                enrollment.getStatus(), review.getStatus(), enrollment.getWaitlistAppliedAt() != null, anyLessonCompleted);

        MyCourseDTO card = baseCard(brief, state);
        card.setEnrollmentId(enrollmentId);
        card.setSectionName(enrollment.getSectionName());
        card.setConfirmedAt(enrollment.getConfirmedAt());
        card.setExpiresAt(enrollment.getExpiresAt());
        card.setCompletedAt(enrollment.getCompletedAt());
        card.setWaitlistAppliedAt(enrollment.getWaitlistAppliedAt());
        card.setLessons(lessons);
        card.setReview(review);
        card.setContinueLessonId(ContinueLessonPolicy.pick(courseLessons.stream().map(Lesson::getId).toList(), progress));
        if (state.isActive()) {
            card.setRemainingDays(remainingDays(enrollment.getExpiresAt(), now));
            card.setAllLessonsCompleted(progressService.isAllCompleted(enrollmentId));  // [최종 과제] 버튼 활성 조건 (C10)
        }
        return card;
    }

    /** 대기·수강 건이 함께 쓰는 강의 소개 부분 */
    private MyCourseDTO baseCard(CourseBriefDTO brief, LearningState state) {
        MyCourseDTO card = new MyCourseDTO();
        card.setCourseId(brief.getCourseId());
        card.setCourseTitle(brief.getTitle());
        card.setHobbyName(brief.getHobbyName());
        card.setHobbyOperating(brief.isHobbyOperating());
        card.setLevel(brief.getLevel());
        card.setState(state);
        card.setLessons(List.of());
        return card;
    }

    /**
     * 강의의 모든 차시(1·2·3차시 순서)에 진도를 채워 돌려준다. 진도 계약(C10)은 재생 기록이 있는 차시만 주므로,
     * 기록이 없는 차시는 0%·미완료로 채워서 카드의 "차시" 칸에 3차시가 항상 보이게 한다.
     */
    private List<LessonProgressDTO> withEveryLesson(List<Lesson> courseLessons, List<LessonProgressDTO> progress) {
        Map<Long, LessonProgressDTO> byLessonId = progress.stream()
                .collect(Collectors.toMap(LessonProgressDTO::getLessonId, lesson -> lesson, (first, second) -> first));
        return courseLessons.stream()
                .map(lesson -> byLessonId.getOrDefault(lesson.getId(),
                        new LessonProgressDTO(lesson.getId(), lesson.getLessonNo(), lesson.getTitle(), 0, null, false)))
                .toList();
    }

    /** 수강 종료까지 남은 일수 — 하루 단위로 올림하고 0 아래로는 내려가지 않는다 (D-day 표시: 확정+30일 기준, API ML-01) */
    private int remainingDays(LocalDateTime expiresAt, LocalDateTime now) {
        long seconds = Duration.between(now, expiresAt).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) ((seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY);
    }

    /** 대기 중이거나 수강 중이면 "진행 중" 묶음(앞쪽), 수료·만료·취소는 "끝난" 묶음(뒤쪽) */
    private boolean isOngoing(LearningState state) {
        return state == LearningState.WAITING || state.isActive();
    }
}
