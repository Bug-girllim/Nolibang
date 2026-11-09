package com.wanted.lmspractice.domain.instructor.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 담당 학습자 한 줄 — 실명·차시 진행·남은 기간·최종 과제·평가 현황
 * [담당] 팀원 1 · #07   [화면] T-S02   [기획] 05 §7-0, NFR-04(실명은 담당 강사·관리자에게만)
 * [연결]
 *  - 만든 곳: InstructorStatQuery.findStudents
 *  - 쓰는 곳: InstructorDashboardService.getStudents → instructor/students.html
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class StudentProgressDTO {

    private Long enrollmentId;
    private Long sectionId;
    private String realName;
    private String courseTitle;
    private String sectionName;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiresAt;
    private int completedLessons;
    private int totalLessons;
    private Long latestReviewId;        // 평가 회차가 없으면 null (제출 전)
    private LocalDateTime reviewDueAt;  // 평가 대기·진행일 때만 값이 있다
    private StudentStatus status;
    private String remainingText;       // 서비스가 now 기준으로 채운다 ("12일 3시간" / "-")

    /** 최종 과제 제출 여부: 평가 회차가 하나라도 있으면 제출한 것으로 본다. */
    public boolean isSubmitted() {
        return latestReviewId != null;
    }
}
