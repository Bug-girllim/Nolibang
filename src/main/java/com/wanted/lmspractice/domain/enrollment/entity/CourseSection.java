package com.wanted.lmspractice.domain.enrollment.entity;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [역할] 분반 한 행 (정원 1~30, 담당 강사 0~1명) + 자기 칼럼만으로 판단하는 규칙(배정 대상인지·화면 이름)
 * [담당] 팀원 2 · #03(칼럼 매핑) · #09(isAssignable·getDisplayName) · #10(create·changeCapacity·assignInstructor)   [기획] D-52, D-59
 * [연결]
 *  - 읽는 곳: SectionAssignPolicy(남은 자리 최대 분반 고르기), EnrollEligibilityPolicy(담당 있는 분반 존재 확인),
 *            EnrollmentService(확정 안내 문구·신청 영역의 분반 이름)
 *  - 변경:   SectionService(#10 분반 생성·정원 변경·담당 지정) — 정원 1~30은 여기서, 활성 수강 미만 축소 불가는 서비스에서 본다
 *  - 잠금:   CourseSectionRepository.findByCourseIdForUpdate — 신청·취소·자리 채우기가 이 행들을 먼저 잠가 한 강의씩 줄 세운다
 * [DB] schema.sql `course_sections` 표와 1:1 (name은 05 패치 H4). 다른 표 칼럼은 Long 값으로만 둔다.
 * [기술] 🟢 @Entity, 정적 팩토리 create(), 이름 있는 상태 변경 메서드(public setter 없음)
 */
@Entity
@Table(name = "course_sections")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class CourseSection {

    /** 분반 정원 하한·상한 (D-52, DB CHECK ck_sections_capacity와 같은 값) */
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "section_no", nullable = false)
    private Integer sectionNo;

    @Column(name = "instructor_id")
    private Long instructorId;

    @JdbcTypeCode(SqlTypes.TINYINT)  // DB가 TINYINT 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    @Column(name = "name", length = 30)
    private String name;

    /**
     * 새 분반 — 번호는 SectionService가 강의 안에서 이어서 정한다.
     * 호출: SectionService.create (SE-02)
     *
     * @param name 분반 이름(비어 있으면 null — 화면이 번호로 "A분반"처럼 표시한다, DB H4)
     * @throws BusinessException CAPACITY_OUT_OF_RANGE
     */
    public static CourseSection create(Long courseId, int sectionNo, String name, Long instructorId, int capacity) {
        checkCapacityRange(capacity);
        CourseSection section = new CourseSection();
        section.courseId = courseId;
        section.sectionNo = sectionNo;
        section.name = name;
        section.instructorId = instructorId;
        section.capacity = capacity;
        return section;
    }

    /**
     * 정원 변경 — 범위(1~30)만 여기서 본다. "현재 활성 수강 수 미만 축소 불가"는 수강 행을 세야 하므로 SectionService가 먼저 확인한다.
     * 호출: SectionService.changeCapacity (SE-03)
     *
     * @return 늘어났으면 true (늘어난 자리만큼 대기 1순위부터 자동 확정해야 한다)
     * @throws BusinessException CAPACITY_OUT_OF_RANGE
     */
    public boolean changeCapacity(int newCapacity) {
        checkCapacityRange(newCapacity);
        boolean increased = newCapacity > this.capacity;
        this.capacity = newCapacity;
        return increased;
    }

    /**
     * 담당 강사 지정·변경 — 수강 건은 옮기지 않는다(D-59). 평가 권한은 "현재 분반 담당자"로 판단하므로(#14) 이 값만 바꾸면 승계된다.
     * 호출: SectionService.changeInstructor (SE-04)
     *
     * @return 담당 공백이었다가 채워졌으면 true (신규 배정 대상이 되므로 대기자 자동 확정이 필요하다)
     */
    public boolean assignInstructor(Long newInstructorId) {
        boolean wasVacant = this.instructorId == null;
        this.instructorId = newInstructorId;
        return wasVacant;
    }

    private static void checkCapacityRange(int capacity) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new BusinessException(ErrorCode.CAPACITY_OUT_OF_RANGE);  // D-52: 분반 정원 1~30
        }
    }

    /** 신청자를 배정할 수 있는 분반인지 — 담당 강사가 없는 분반은 배정 대상이 아니다(D-52, v_section_seats.assignable과 같은 기준) */
    public boolean isAssignable() {
        return instructorId != null;
    }

    /**
     * 화면에 보일 분반 이름. 이름을 비워 두었으면 번호로 만든다(1 → A분반) — schema.sql 05 패치 H4 주석의 규칙.
     * 호출: EnrollmentService(신청 결과·신청 영역), SectionService(#10 배정 알림 문구)
     */
    public String getDisplayName() {
        return displayName(sectionNo, name);
    }

    /**
     * 분반 표시 이름 규칙 한곳 — 엔티티가 없는 조회 결과(SectionDTO·WaitlistDTO, #10 native 조회)도 이 메서드를 쓴다.
     * ❓ 임시: 27번째 분반부터는 알파벳이 모자라 "27분반"처럼 숫자로 표시
     */
    public static String displayName(Integer sectionNo, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (sectionNo == null) {
            return "";
        }
        return sectionNo <= 26 ? (char) ('A' + sectionNo - 1) + "분반" : sectionNo + "분반";
    }
}
