package com.wanted.lmspractice.domain.enrollment.dto;

import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [역할] 학습자 한 명의 수강 건 한 개를 화면용으로 읽은 값 — 상태·분반 이름·수강 기간·대기열에서 확정됐는지
 * [담당] 팀원 2 · #11(조회 계약 추가) — 수강 도메인 소유(#09)   [화면] U-S08   [API] ML-01
 * [연결]
 *  - 만든 곳: EnrollmentService.getMyEnrollments(from)
 *  - 쓰는 곳: MyLearningService → MyCourseDTO (나의 학습 현황 카드)
 * [기술] 🟢 DTO 정적 팩토리 from(entity)
 * Entity를 learning 도메인으로 넘기지 않으려고 필요한 값만 담았다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class MyEnrollmentDTO {

    private Long enrollmentId;
    private Long courseId;
    private EnrollmentStatus status;
    /** 배정된 분반 이름(예: "A분반"). 이름이 없으면 null */
    private String sectionName;
    private LocalDateTime confirmedAt;
    /** 수강 종료 시각 = 확정 + 30일 (DB CHECK) */
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    /** 취소·만료·수료로 끝난 시각. 진행 중이면 null */
    private LocalDateTime endedAt;
    /** 대기열을 거쳐 자동 확정된 수강이면 그 대기 신청 시각, 바로 확정됐으면 null */
    private LocalDateTime waitlistAppliedAt;

    /** Enrollment 엔티티 → 표시용 DTO. 분반 이름·대기 신청 시각은 EnrollmentService가 따로 찾아 넘긴다 */
    public static MyEnrollmentDTO from(Enrollment enrollment, String sectionName, LocalDateTime waitlistAppliedAt) {
        return new MyEnrollmentDTO(
                enrollment.getId(),
                enrollment.getCourseId(),
                enrollment.getStatus(),
                sectionName,
                enrollment.getConfirmedAt(),
                enrollment.getExpiresAt(),
                enrollment.getCompletedAt(),
                enrollment.getEndedAt(),
                waitlistAppliedAt);
    }
}
