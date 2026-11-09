package com.wanted.lmspractice.domain.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.enrollment.dto.ApplyResultDTO;
import com.wanted.lmspractice.domain.enrollment.dto.EnrollmentStatusDTO;
import com.wanted.lmspractice.domain.enrollment.entity.Enrollment;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistEntry;
import com.wanted.lmspractice.domain.enrollment.entity.WaitlistStatus;
import com.wanted.lmspractice.domain.enrollment.repository.EnrollmentRepository;
import com.wanted.lmspractice.domain.enrollment.repository.WaitlistEntryRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 수강 신청·분반 자동 배정·단일 대기열·자동 확정·수강 취소 규칙 테스트 (이슈 #09 테스트 항목, DEV_REVIEW 시나리오 31~33)
 * [담당] 팀원 2 · #09   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 각 테스트는 끝나면 롤백된다(@Transactional).
 * [시드] 학습자 2 learner · 3 learner2 · 4 learner3 · 5 newbie(DB상 학습자라 신청 데이터로만 사용)
 *       강의 1: A분반(1, 정원 5, 활성 1 = learner3 e6) · B분반(2, 정원 5, 활성 0), learner의 e1은 수료
 *       강의 4: A분반(5, 정원 1) 만석(learner2 e5) + 대기 1번 learner3(w2)
 *       강의 6: A분반(7, 담당 있음) · B분반(8, 담당 없음)
 * [한계] 하나의 트랜잭션 안에서 도는 테스트라 "브라우저 2개 동시 신청"은 확인하지 못한다 → PR에 수동 테스트 결과를 적는다.
 *       평가 대기·진행 중 취소 거부는 ReviewQueryService(#14) 스텁이 항상 false라 #14 merge 후 추가한다.
 *       알림 레코드 생성은 NotificationService(#17) 스텁이라 #17 merge 후 확인한다.
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용)
 */
@SpringBootTest
@Transactional
class EnrollmentServiceTest {

    private static final long LEARNER = 2L, LEARNER2 = 3L, LEARNER3 = 4L, NEWBIE = 5L;

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private SeatService seatService;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    /** 시드를 테스트 상황에 맞게 바꾼다(롤백됨). 엔티티를 불러오기 전에만 쓴다 */
    private void sql(String statement) {
        entityManager.createNativeQuery(statement).executeUpdate();
    }

    @Test
    @DisplayName("여유 분반 자동 배정: 남은 자리가 가장 큰 분반(B분반 5석 > A분반 4석)에 확정, 만료 = 확정 + 30일")
    void applyConfirmsSectionWithMostSeats() {
        ApplyResultDTO result = enrollmentService.apply(LEARNER2, 1L);

        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.getSectionName()).isEqualTo("B분반");
        Enrollment saved = enrollmentRepository.findByUserIdAndCourseId(LEARNER2, 1L).orElseThrow();
        assertThat(saved.getSectionId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(Duration.between(saved.getConfirmedAt(), saved.getExpiresAt())).isEqualTo(Duration.ofDays(30));
        assertThat(enrollmentService.getStatusForUser(LEARNER2, 1L).getState()).isEqualTo(EnrollmentStatusDTO.LEARNING);
    }

    @Test
    @DisplayName("남은 자리가 같으면 분반 번호순(D-52)")
    void tieGoesToLowerSectionNo() {
        sql("UPDATE course_sections SET capacity = 6 WHERE id = 1");  // A분반 6 − 1 = 5석, B분반 5석 → 동률

        assertThat(enrollmentService.apply(LEARNER2, 1L).getSectionName()).isEqualTo("A분반");
    }

    @Test
    @DisplayName("담당 공백 분반은 배정에서 제외: 담당 없는 B분반에 자리가 더 많아도 A분반 배정, A분반이 차면 대기")
    void sectionWithoutInstructorIsSkipped() {
        sql("UPDATE course_sections SET capacity = 1 WHERE id = 7");  // 강의 6 A분반 1석, B분반(담당 없음) 5석

        assertThat(enrollmentService.apply(LEARNER2, 6L).getSectionName()).isEqualTo("A분반");
        ApplyResultDTO next = enrollmentService.apply(LEARNER3, 6L);
        assertThat(next.isConfirmed()).isFalse();
        assertThat(next.getWaitingNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 분반에 담당 강사가 없으면 신청도 대기도 불가 — NO_ASSIGNABLE_SECTION")
    void noAssignableSection() {
        sql("UPDATE course_sections SET instructor_id = NULL WHERE course_id = 5");

        assertThatThrownBy(() -> enrollmentService.apply(LEARNER2, 5L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NO_ASSIGNABLE_SECTION));
        assertThat(enrollmentService.getStatusForUser(LEARNER2, 5L).getState()).isEqualTo(EnrollmentStatusDTO.UNAVAILABLE);
    }

    @Test
    @DisplayName("만석이면 대기 순번(대기 1번 다음 → 2번)")
    void fullCourseGoesToWaitlist() {
        ApplyResultDTO result = enrollmentService.apply(LEARNER, 4L);

        assertThat(result.getResult()).isEqualTo(ApplyResultDTO.WAITING);
        assertThat(result.getWaitingNumber()).isEqualTo(2);
        EnrollmentStatusDTO status = enrollmentService.getStatusForUser(LEARNER, 4L);
        assertThat(status.getState()).isEqualTo(EnrollmentStatusDTO.WAITING);
        assertThat(status.getWaitingNumber()).isEqualTo(2);
        assertThat(status.getWaitlistId()).isNotNull();
    }

    @Test
    @DisplayName("시나리오 31: 대기자가 있으면 빈자리가 있어도 대기열 끝으로 간다(빈자리 직행 금지)")
    void waitersFirstEvenIfSeatIsFree() {
        sql("UPDATE course_sections SET capacity = 2 WHERE id = 5");  // 강의 4에 빈자리 1석 + 대기 1명(learner3)

        ApplyResultDTO result = enrollmentService.apply(LEARNER, 4L);

        assertThat(result.isConfirmed()).isFalse();
        assertThat(result.getWaitingNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("중복·재신청 거부: 학습 중 ALREADY_ENROLLED, 대기 중 ALREADY_WAITING, 수료 CANNOT_REAPPLY")
    void duplicateAndReapplyRejected() {
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER3, 1L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_ENROLLED));
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER3, 4L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_WAITING));
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER, 1L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANNOT_REAPPLY));
    }

    @Test
    @DisplayName("운영 중이 아닌 취미의 강의는 HOBBY_NOT_OPEN, 없는 강의는 COURSE_NOT_FOUND")
    void courseMustBeOpen() {
        sql("UPDATE hobbies SET state = 'PREPARING' WHERE id = 3");  // 디저트 만들기 → 준비 중

        assertThatThrownBy(() -> enrollmentService.apply(LEARNER2, 8L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HOBBY_NOT_OPEN));
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER2, 9999L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    @DisplayName("수강 취소 → 자리 반환 → 대기 1순위 자동 확정, 취소한 강의는 재신청 불가(D-55)")
    void cancelFillsFirstWaiterAndBlocksReapply() {
        enrollmentService.cancel(LEARNER2, 5L);  // 강의 4의 learner2 수강(e5) 취소

        Enrollment cancelled = enrollmentRepository.findById(5L).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        // 대기 1번 learner3가 자동 확정 (수락 단계 없음)
        WaitlistEntry confirmed = waitlistEntryRepository.findById(2L).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(WaitlistStatus.CONFIRMED);
        Enrollment promoted = enrollmentRepository.findByUserIdAndCourseId(LEARNER3, 4L).orElseThrow();
        assertThat(confirmed.getEnrollmentId()).isEqualTo(promoted.getId());
        assertThat(Duration.between(promoted.getConfirmedAt(), promoted.getExpiresAt())).isEqualTo(Duration.ofDays(30));
        assertThat(enrollmentService.getStatusForUser(LEARNER3, 4L).getState()).isEqualTo(EnrollmentStatusDTO.LEARNING);
        // D-55: 취소한 강의는 다시 신청·대기할 수 없다
        assertThat(enrollmentService.getStatusForUser(LEARNER2, 4L).getState()).isEqualTo(EnrollmentStatusDTO.CANNOT_REAPPLY);
        assertThatThrownBy(() -> enrollmentService.apply(LEARNER2, 4L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANNOT_REAPPLY));
    }

    @Test
    @DisplayName("남의 수강 건 취소는 ENROLLMENT_NOT_FOUND, 이미 끝난 수강 건 취소는 INVALID_REVIEW_STATE")
    void cancelRules() {
        assertThatThrownBy(() -> enrollmentService.cancel(LEARNER2, 6L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
        assertThatThrownBy(() -> enrollmentService.cancel(LEARNER, 1L))  // e1은 수료
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }

    @Test
    @DisplayName("시나리오 33: 자리 2개가 한꺼번에 생기면 1·2순위가 각각 1건씩 확정되고, 다시 불러도 중복 확정이 없다")
    void twoSeatsConfirmFirstAndSecondWaiter() {
        waitlistService.join(LEARNER, 4L);  // 대기 2번 (1번은 learner3)
        sql("UPDATE course_sections SET capacity = 3 WHERE id = 5");  // 남은 자리 2석

        assertThat(seatService.fillVacancies(4L)).isEqualTo(2);
        assertThat(seatService.fillVacancies(4L)).isZero();
        assertThat(enrollmentRepository.findByUserIdAndCourseId(LEARNER3, 4L)).isPresent();
        assertThat(enrollmentRepository.findByUserIdAndCourseId(LEARNER, 4L)).isPresent();
        assertThat(waitlistService.hasWaiting(4L)).isFalse();
    }

    @Test
    @DisplayName("대기 취소 후 다시 대기하면 마지막 순번, 남의 대기 취소는 NOT_FOUND")
    void cancelWaitThenRejoinGoesLast() {
        waitlistService.join(LEARNER, 4L);   // 2번
        waitlistService.join(NEWBIE, 4L);    // 3번
        Long myWait = waitlistService.findWaiting(LEARNER, 4L).orElseThrow().getId();

        assertThatThrownBy(() -> waitlistService.cancel(NEWBIE, myWait))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
        waitlistService.cancel(LEARNER, myWait);

        assertThat(enrollmentService.getStatusForUser(NEWBIE, 4L).getWaitingNumber()).isEqualTo(2);  // 앞으로 당겨짐
        assertThat(enrollmentService.apply(LEARNER, 4L).getWaitingNumber()).isEqualTo(3);           // 끝순번
        assertThatThrownBy(() -> waitlistService.cancel(LEARNER, myWait))                              // 이미 취소된 줄
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("계약 C5: complete는 수료 + 자리 반환(대기 1순위 확정), 두 번 부르면 INVALID_REVIEW_STATE, 기간 안 반려 종료는 거부")
    void completeContract() {
        enrollmentService.complete(5L);  // 강의 4 learner2 수료

        assertThat(enrollmentRepository.findById(5L).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollmentRepository.findByUserIdAndCourseId(LEARNER3, 4L)).isPresent();  // 대기 1번 자동 확정
        assertThatThrownBy(() -> enrollmentService.complete(5L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
        assertThatThrownBy(() -> enrollmentService.closeAfterRejection(6L))  // e6은 수강 기간 안
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REVIEW_STATE));
    }
}
