package com.wanted.lmspractice.domain.work.entity;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.entity.CourseLevelConverter;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
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
 * [#19 추가] 일반 작품 생성·수정, 공개 설정(published_at 기록), 한 줄 회고 — 상태 변경은 모두 이름 있는 메서드로만 한다.
 */
@Entity
@Table(name = "works")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Work {

    /** 회고 최대 길이 = works.reflection VARCHAR(500). ❓ "한 줄"의 화면 기준 길이는 미정이라 DB 길이를 그대로 쓴다 */
    public static final int MAX_REFLECTION_LENGTH = 500;

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

    /**
     * 일반 작품 생성 (#19, FR-027) — 수강과 무관하므로 enrollment_id 는 NULL(ck_works_kind). 수료 상태에 영향 없음.
     * 호출: WorkService.saveGeneralWork (취미 운영 여부·수준 태그·참고 피드백은 호출 전에 검사)
     */
    public static Work createGeneral(Long authorId, Long hobbyId, CourseLevel level, Visibility visibility,
                                     String reflection, Long referenceReviewId, LocalDateTime now) {
        Work work = new Work();
        work.authorId = authorId;
        work.kind = WorkKind.GENERAL;
        work.hobbyId = hobbyId;
        work.level = level;
        work.referenceReviewId = referenceReviewId;
        work.writeReflection(reflection);
        work.changeVisibility(visibility, now);
        return work;
    }

    /**
     * 일반 작품 수정 (#19, MW-04) — 취미·수준·공개·회고·참고 피드백을 바꾼다.
     * created_at 은 바꾸지 않으므로 성장 타임라인 순서가 그대로다. 관리자 숨김(admin_hidden)은 작성자가 수정해도 풀리지 않는다.
     */
    public void editGeneral(Long hobbyId, CourseLevel level, Visibility visibility, String reflection,
                            Long referenceReviewId, LocalDateTime now) {
        this.hobbyId = hobbyId;
        this.level = level;
        this.referenceReviewId = referenceReviewId;
        writeReflection(reflection);
        changeVisibility(visibility, now);
    }

    /**
     * 공개 설정 변경 (MW-05). null 이면 기본값 비공개.
     * ck_works_published: 공개 작품은 공개 시각이 있어야 한다 → 처음 공개될 때만 기록한다.
     * ❓ 임시값: 비공개 → 다시 공개해도 처음 공개 시각을 유지(게시판 맨 위로 다시 올리는 효과를 막음) — #01에서 확정 필요
     * 관리자 숨김 중이면 공개로 바꿔도 isPubliclyVisible() 이 false 라 노출되지 않는다.
     */
    public void changeVisibility(Visibility visibility, LocalDateTime now) {
        this.visibility = visibility == null ? Visibility.PRIVATE : visibility;
        if (this.visibility == Visibility.PUBLIC && this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    /**
     * 한 줄 회고 저장 (MW-06, D-14) — 선택 입력. 앞뒤 공백을 지우고, 빈 값이면 회고를 지운다(NULL).
     * @throws BusinessException INVALID_INPUT (works.reflection VARCHAR(500) 초과)
     */
    public void writeReflection(String reflection) {
        String value = reflection == null ? null : reflection.strip();
        if (value != null && value.length() > MAX_REFLECTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.reflection = (value == null || value.isEmpty()) ? null : value;
    }

    /** 본인이 쓴, 삭제되지 않은 작품인지 — 작성자 전용 기능(수정·공개 설정·회고)의 대상 확인 */
    public boolean isOwnedActiveBy(Long userId) {
        return isAuthor(userId) && !isDeleted();
    }

    public boolean isGeneral() {
        return kind == WorkKind.GENERAL;
    }

    /**
     * 작성자 삭제 (논리 삭제) — deleted_at 을 기록한다. 작품 행·버전·파일은 남기고 목록·상세·파일 URL 에서만 사라진다.
     * ck_works_deleted: 삭제된 작품은 display_version_id 가 NULL 이어야 하므로 함께 비운다.
     * D-32: '참고한 공식 피드백' 연결(reference_review_id)만 끊는다 — 원본 피드백(review_rounds)은 건드리지 않는다.
     *       댓글·좋아요 정리는 다른 표라 WorkService.deleteGeneralWork 가 함께 한다.
     * 호출: WorkService.deleteGeneralWork (일반 작품만 — 최종 과제는 수강·평가·수료 기록과 묶여 있어 삭제 대상이 아니다)
     */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
        this.displayVersionId = null;
        this.referenceReviewId = null;
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

    public boolean isAdminHidden() {
        return Boolean.TRUE.equals(adminHidden);
    }

    /**
     * 관리자 숨김 (Q-01) — 공개 작품만. 일반 노출만 막고 작성자의 공개 설정(visibility)은 건드리지 않는다.
     * 이미 숨김이면 ALREADY_HIDDEN, 비공개 작품이면 INVALID_INPUT. DB CHECK(ck_works_hidden_reason): 숨김이면 사유·시각 필수.
     */
    public void hide(Long adminId, String reason, LocalDateTime now) {
        if (isAdminHidden()) {
            throw new BusinessException(ErrorCode.ALREADY_HIDDEN);
        }
        if (visibility != Visibility.PUBLIC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);   // 비공개 작품은 숨길 대상이 아니다 (API AW-02)
        }
        this.adminHidden = Boolean.TRUE;
        this.hiddenReason = reason;
        this.hiddenAt = now;
        this.hiddenBy = adminId;
    }

    /**
     * 숨김 복구 — admin_hidden 만 푼다. 작성자가 그 사이 비공개로 바꿨다면 visibility 가 PRIVATE 그대로라 복구해도 공개되지 않는다.
     * 숨김 상태가 아니면 NOT_HIDDEN. 지난 숨김 사유는 work_moderation_logs 이력에 남아 있어 여기서는 비운다.
     */
    public void restore() {
        if (!isAdminHidden()) {
            throw new BusinessException(ErrorCode.NOT_HIDDEN);
        }
        this.adminHidden = Boolean.FALSE;
        this.hiddenReason = null;
        this.hiddenAt = null;
        this.hiddenBy = null;
    }
}
