package com.wanted.lmspractice.domain.learning.service;

import com.wanted.lmspractice.domain.course.dto.CourseDetailDTO;
import com.wanted.lmspractice.domain.course.entity.Lesson;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import com.wanted.lmspractice.domain.learning.dto.LessonViewDTO;
import com.wanted.lmspractice.domain.learning.dto.ProgressReportDTO;
import com.wanted.lmspractice.domain.learning.entity.LessonProgress;
import com.wanted.lmspractice.domain.learning.policy.VideoAccessPolicy;
import com.wanted.lmspractice.domain.learning.repository.LessonProgressRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 재생 시간 누적(서버 관측 시간 상한)·60% 완료 판정, 차시 화면 데이터, 영상 접근 확인 (FR-020)
 * [담당] 팀원 3 · #12   [화면] U-S06   [기획] FR-020, D-42   [API] LE-01·LE-02·LE-03
 * [연결]
 *  - 호출받음: LessonController(화면·재생 보고), LessonVideoController(영상 접근 확인),
 *             계약 C10 — SubmissionPolicy(#13), MyLearningService(#11), InstructorDashboardService(#07)
 *  - 호출함:   EnrollmentService.getOwnedEnrollment(계약 C6, 본인 확인), CourseService.getLesson·getLessonsOfCourse(#12 요청 메서드),
 *             VideoAccessPolicy, LessonProgressRepository, LessonProgress.applyReport(누적 규칙)
 *  - 계약:     C10 isAllCompleted·getLessonStatuses — 시그니처 변경 금지
 * [기술] 🟢 @Transactional, 🟢 JPA 변경 감지, 🟢 native FOR UPDATE
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    /** 최종 과제 제출에 필요한 완료 차시 수 (강의당 3차시, DB ck_lessons_no) */
    public static final int REQUIRED_LESSONS = 3;

    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentService enrollmentService;
    private final CourseService courseService;
    private final VideoAccessPolicy videoAccessPolicy;

    /**
     * 재생 시간 보고 한 건을 처리한다.
     * 호출: LessonController.report (POST /lessons/{lessonId}/progress, API LE-02)
     * @return 누적 비율·완료 여부
     * @throws BusinessException ENROLLMENT_NOT_FOUND 남의 수강 id(C6이 숨김), LESSON_NOT_FOUND 수강한 강의의 차시가 아님,
     *                           ENROLLMENT_EXPIRED 기간이 끝났거나 취소·만료된 수강
     * 트랜잭션: 진도 행을 FOR UPDATE로 잠근 뒤 처리한다(다중 탭 동시 보고도 한 건씩 순서대로).
     */
    @Transactional
    public LessonProgressDTO report(Long userId, Long lessonId, ProgressReportDTO report) {
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, report.getEnrollmentId());   // 본인 확인
        Lesson lesson = courseService.getLesson(lessonId);
        LocalDateTime now = LocalDateTime.now();   // CONVENTIONS §9: 현재 시각은 LocalDateTime.now()

        if (!enrollment.getCourseId().equals(lesson.getCourseId())) {
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);   // 다른 강의의 차시 id 조작
        }

        LessonProgress progress = lessonProgressRepository.findForUpdate(enrollment.getId(), lessonId).orElse(null);

        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            // 수료자는 다시 봐도 진도를 바꾸지 않는다
            return LessonProgressDTO.of(lessonId, lesson.getLessonNo(), lesson.getTitle(), lesson.getDurationSec(), progress);
        }
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE || !enrollment.isWithinPeriod(now)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_EXPIRED);   // D-42: 기간이 끝난 미수료자는 진도 적립 불가
        }

        if (progress == null) {
            // 처음 재생: 행을 만든다. 같은 순간 다른 탭이 먼저 만들면 PK 충돌로 이 보고만 실패하고, 다음 보고(5초 뒤)부터 정상 처리된다.
            progress = lessonProgressRepository.save(LessonProgress.create(enrollment.getId(), lessonId, lesson.getCourseId()));
        }
        progress.applyReport(report.getStreamKey(), report.getSeq(), report.isPlaying(),
                report.getPositionSec(), lesson.getDurationSec(), now);   // 변경 감지로 저장
        return LessonProgressDTO.of(lessonId, lesson.getLessonNo(), lesson.getTitle(), lesson.getDurationSec(), progress);
    }

    /**
     * 차시 영상 학습 화면 데이터 (차시 목록·진도·학습 목표·수강 기간).
     * 호출: LessonController.lesson (GET /lessons/{lessonId}?enrollmentId=, API LE-01)
     * @throws BusinessException VIDEO_ACCESS_DENIED 기간이 끝난 미수료자, LESSON_NOT_FOUND, ENROLLMENT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public LessonViewDTO getLessonView(Long userId, Long enrollmentId, Long lessonId) {
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, enrollmentId);
        Lesson lesson = courseService.getLesson(lessonId);
        LocalDateTime now = LocalDateTime.now();
        videoAccessPolicy.checkVideoAccess(enrollment, lesson.getCourseId(), now);

        Map<Long, LessonProgress> progressByLesson = new HashMap<>();
        for (LessonProgress p : lessonProgressRepository.findByEnrollmentId(enrollment.getId())) {
            progressByLesson.put(p.getLessonId(), p);
        }
        List<LessonProgressDTO> lessons = new ArrayList<>();
        int completedCount = 0;
        for (Lesson l : courseService.getLessonsOfCourse(enrollment.getCourseId())) {
            LessonProgressDTO dto = LessonProgressDTO.of(l.getId(), l.getLessonNo(), l.getTitle(), l.getDurationSec(),
                    progressByLesson.get(l.getId()));
            completedCount += dto.isCompleted() ? 1 : 0;
            lessons.add(dto);
        }
        LessonProgressDTO current = LessonProgressDTO.of(lessonId, lesson.getLessonNo(), lesson.getTitle(),
                lesson.getDurationSec(), progressByLesson.get(lessonId));

        CourseDetailDTO course = courseService.getCourseDetailForEnrolled(enrollment.getCourseId());   // D-20: 준비 중 취미여도 수강 중이면 접근
        return new LessonViewDTO(enrollment.getId(), enrollment.getCourseId(), course.getTitle(), course.getLevel().getLabel(),
                lessonId, lesson.getLessonNo(), lesson.getTitle(), lesson.getLearningObjective(), lesson.getContentText(),
                lesson.getDurationSec(), current.getPercent(), current.isCompleted(),
                lessons, completedCount, REQUIRED_LESSONS, completedCount >= REQUIRED_LESSONS,
                enrollment.getExpiresAt(), periodText(enrollment, now),
                LessonProgress.COMPLETE_RATIO.movePointRight(2).intValue());
    }

    /**
     * 영상 출력 전 권한 확인 후 영상 파일 경로(video_key)를 돌려준다. 파일 읽기·전송은 컨트롤러가 한다.
     * 호출: LessonVideoController.video (GET /videos/{lessonId}?enrollmentId=, API LE-03)
     * @throws BusinessException VIDEO_ACCESS_DENIED 미수료 만료자, LESSON_NOT_FOUND, ENROLLMENT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public String authorizeVideo(Long userId, Long enrollmentId, Long lessonId) {
        Enrollment enrollment = enrollmentService.getOwnedEnrollment(userId, enrollmentId);
        Lesson lesson = courseService.getLesson(lessonId);
        videoAccessPolicy.checkVideoAccess(enrollment, lesson.getCourseId(), LocalDateTime.now());
        return lesson.getVideoKey();
    }

    /** 계약 C10: 3차시 모두 완료했는지 (사용: SubmissionPolicy #13) */
    @Transactional(readOnly = true)
    public boolean isAllCompleted(Long enrollmentId) {
        return lessonProgressRepository.countByEnrollmentIdAndCompletedAtIsNotNull(enrollmentId) >= REQUIRED_LESSONS;
    }

    /**
     * 계약 C10: 차시별 진도 (사용: 팀원 2 #11, 팀원 1 #07). 차시 번호 순서.
     * ❓ 한 번도 재생하지 않은 차시는 목록에 없다 — 인자가 enrollmentId뿐이라 강의를 알 수 없어서다(진도 행의 course_id만 안다).
     */
    @Transactional(readOnly = true)
    public List<LessonProgressDTO> getLessonStatuses(Long enrollmentId) {
        List<LessonProgress> rows = lessonProgressRepository.findByEnrollmentId(enrollmentId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, LessonProgress> byLesson = new HashMap<>();
        for (LessonProgress p : rows) {
            byLesson.put(p.getLessonId(), p);
        }
        List<LessonProgressDTO> result = new ArrayList<>();
        for (Lesson l : courseService.getLessonsOfCourse(rows.get(0).getCourseId())) {
            if (byLesson.containsKey(l.getId())) {
                result.add(LessonProgressDTO.of(l.getId(), l.getLessonNo(), l.getTitle(), l.getDurationSec(),
                        byLesson.get(l.getId())));
            }
        }
        return result;
    }

    /** 수강 종료 표시. 수료자는 "수료", 진행 중이면 "D-남은 날수" */
    private String periodText(Enrollment enrollment, LocalDateTime now) {
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            return "수료";
        }
        long days = Math.max(Duration.between(now, enrollment.getExpiresAt()).toDays(), 0);
        return "D-" + days;
    }
}
