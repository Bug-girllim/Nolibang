package com.wanted.lmspractice.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 공통 강의 9개 (시드 데이터)
 * 담당: 팀원 2 · #03
 * 칼럼은 resources/sql/schema.sql의 `courses` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "courses")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "hobby_id", nullable = false)
    private Long hobbyId;

    @Convert(converter = CourseLevelConverter.class)
    @JdbcTypeCode(SqlTypes.TINYINT)  // DB가 TINYINT 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "level", nullable = false)
    private CourseLevel level;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "thumbnail_key", length = 512)
    private String thumbnailKey;

    @Column(name = "supplies_text", nullable = false)
    private String suppliesText;

    @Column(name = "assignment_title", nullable = false, length = 200)
    private String assignmentTitle;

    @Column(name = "assignment_description", nullable = false)
    private String assignmentDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CourseStatus status = CourseStatus.OPEN;  // DB 기본값과 같은 초기값

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 관리자 강의 편집(A-S06 콘텐츠 검수) — 강의명·소개·준비물·과제·공개 상태를 바꾼다. 수준·취미는 바꾸지 않는다(수강 기록과 연결).
     * 호출: CourseService.updateCourse (변경 감지로 저장)
     */
    public void update(String title, String description, String suppliesText,
                       String assignmentTitle, String assignmentDescription, CourseStatus newStatus) {
        this.title = title;
        this.description = description;
        this.suppliesText = suppliesText;
        this.assignmentTitle = assignmentTitle;
        this.assignmentDescription = assignmentDescription;
        this.status = newStatus;
    }
}
