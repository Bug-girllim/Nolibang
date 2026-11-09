package com.wanted.lmspractice.domain.work.entity;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.CourseLevelConverter;
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
 * 작품 (kind = FINAL 최종 과제 / GENERAL 일반 작품)
 * 담당: 팀원 4 · #03
 * 칼럼은 resources/sql/schema.sql의 `works` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "works")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private WorkKind kind;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "hobby_id", nullable = false)
    private Long hobbyId;

    @Convert(converter = CourseLevelConverter.class)
    @JdbcTypeCode(SqlTypes.TINYINT)  // DB가 TINYINT 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "level", nullable = false)
    private CourseLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;  // DB 기본값과 같은 초기값

    @Column(name = "reflection", length = 500)
    private String reflection;

    @Column(name = "reference_review_id")
    private Long referenceReviewId;

    @Column(name = "display_version_id")
    private Long displayVersionId;

    @Column(name = "admin_hidden", nullable = false)
    private Boolean adminHidden = Boolean.FALSE;  // DB 기본값과 같은 초기값

    @Column(name = "hidden_reason", length = 1000)
    private String hiddenReason;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 최종 과제 작품 생성 — 수강당 1개(works.enrollment_id UNIQUE). 공개 여부는 DB 기본값(PRIVATE)
     * 호출: WorkService.saveFinalWorkVersion (취미·수준은 강의 값, 호출한 AssignmentService가 넘긴다)
     */
    public static Work createFinal(Long authorId, Long enrollmentId, Long hobbyId, CourseLevel level) {
        Work work = new Work();
        work.authorId = authorId;
        work.kind = WorkKind.FINAL;
        work.enrollmentId = enrollmentId;
        work.hobbyId = hobbyId;
        work.level = level;
        return work;
    }

    /** 화면에 보여 줄 버전을 바꾼다 (같은 작품의 버전만 — DB 복합 FK가 한 번 더 막는다) */
    public void display(Long workVersionId) {
        this.displayVersionId = workVersionId;
    }

    public boolean isAuthor(Long userId) {
        return authorId != null && authorId.equals(userId);
    }

    /** Q-02: 일반 노출 가능 = 공개 + 관리자 숨김 아님 + 삭제 아님 */
    public boolean isPubliclyVisible() {
        return visibility == Visibility.PUBLIC && !Boolean.TRUE.equals(adminHidden) && deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
