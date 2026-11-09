package com.wanted.lmspractice.domain.enrollment.entity;

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
 * [담당] 팀원 2 · #03(칼럼 매핑) · #09(isAssignable·getDisplayName) — 생성·정원 변경·담당 지정은 #10   [기획] D-52
 * [연결]
 *  - 읽는 곳: SectionAssignPolicy(남은 자리 최대 분반 고르기), EnrollEligibilityPolicy(담당 있는 분반 존재 확인),
 *            EnrollmentService(확정 안내 문구·신청 영역의 분반 이름)
 *  - 잠금:   CourseSectionRepository.findByCourseIdForUpdate — 신청·취소·자리 채우기가 이 행들을 먼저 잠가 한 강의씩 줄 세운다
 * [DB] schema.sql `course_sections` 표와 1:1 (name은 05 패치 H4). 다른 표 칼럼은 Long 값으로만 둔다.
 * [기술] 🟢 @Entity
 */
@Entity
@Table(name = "course_sections")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class CourseSection {

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

    /** 신청자를 배정할 수 있는 분반인지 — 담당 강사가 없는 분반은 배정 대상이 아니다(D-52, v_section_seats.assignable과 같은 기준) */
    public boolean isAssignable() {
        return instructorId != null;
    }

    /**
     * 화면에 보일 분반 이름. 이름을 비워 두었으면 번호로 만든다(1 → A분반) — schema.sql 05 패치 H4 주석의 규칙.
     * 호출: EnrollmentService(신청 결과·신청 영역), 이후 #10 분반 관리 화면에서도 같은 규칙을 쓰면 된다
     */
    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        // ❓ 임시: 27번째 분반부터는 알파벳이 모자라 "27분반"처럼 숫자로 표시
        return sectionNo <= 26 ? (char) ('A' + sectionNo - 1) + "분반" : sectionNo + "분반";
    }
}
