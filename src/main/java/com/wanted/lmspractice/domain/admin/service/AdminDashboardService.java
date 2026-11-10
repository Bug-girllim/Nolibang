package com.wanted.lmspractice.domain.admin.service;

import com.wanted.lmspractice.domain.admin.dto.AdminSummaryDTO;
import com.wanted.lmspractice.domain.admin.dto.AdminSummaryDTO.Alert;
import com.wanted.lmspractice.domain.instructor.dto.InstructorHomeDTO.OverdueReviewDTO;
import com.wanted.lmspractice.domain.instructor.dto.SectionStatDTO;
import com.wanted.lmspractice.domain.instructor.repository.InstructorStatQuery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 관리자 홈 요약(강의·차시 수, 활성 수강, 평가 대기, 대기 인원, 담당 공백 분반)과 운영 확인 항목을 만든다.
 * [담당] 팀원 1 · #07   [화면] A-S01   [기획] D-59 (관리자는 공식 과제를 평가하지 않는다 — 조회 전용)
 * [연결]
 *  - 호출받음: AdminHomeController
 *  - 호출함:   InstructorStatQuery (같은 #07 소유의 집계 쿼리 재사용)
 * [기술] 🟢 @Transactional(readOnly = true)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final InstructorStatQuery instructorStatQuery;

    /**
     * 관리자 홈 데이터. 호출: AdminHomeController.home (GET /admin, AD-01)
     * 운영 확인 항목 = 담당 공백 분반(분반 관리로 이동) + 평가 기한 초과(조회 전용, 이동 버튼 없음).
     */
    public AdminSummaryDTO getSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<SectionStatDTO> vacant = instructorStatQuery.findVacantSections(now);

        List<Alert> alerts = new ArrayList<>();
        for (SectionStatDTO s : vacant) {
            alerts.add(new Alert("담당 공백 분반",
                    s.getCourseTitle() + " · " + s.getLevelLabel() + " " + s.getSectionName()
                            + " · 활성 " + s.getActiveCount() + "건 — 신규 배정 제외 중 · 담당 강사를 지정해 주세요",
                    "/admin/sections", "분반 관리", s.getSectionId()));
        }
        for (OverdueReviewDTO o : instructorStatQuery.findOverdueReviews(null, now)) {
            String who = o.getInstructorName() != null ? o.getInstructorName() : "담당 공백";
            alerts.add(new Alert("평가 기한 초과",
                    who + " · " + o.getCourseTitle() + " · " + o.getLevelLabel() + " / " + o.getStudentName()
                            + " (조회 전용, 평가는 담당 강사)",
                    null, null, null));
        }

        return new AdminSummaryDTO(instructorStatQuery.countCourses(), instructorStatQuery.countLessons(),
                instructorStatQuery.countActiveEnrollments(), instructorStatQuery.countPendingReviews(),
                instructorStatQuery.countWaitingUsers(), vacant.size(), alerts);
    }
}
