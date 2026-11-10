package com.wanted.lmspractice.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 차시 (강의당 3개, 영상 경로·길이)
 * 담당: 팀원 2 · #03
 * 칼럼은 resources/sql/schema.sql의 `lessons` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "lessons")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @JdbcTypeCode(SqlTypes.TINYINT)  // DB가 TINYINT 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "lesson_no", nullable = false)
    private Integer lessonNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "learning_objective", nullable = false)
    private String learningObjective;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "video_key", nullable = false, length = 512)
    private String videoKey;

    @Column(name = "duration_sec", nullable = false)
    private BigDecimal durationSec;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 차시 영상 교체 — 영상 경로와 실제 영상 길이를 함께 바꾼다(완료 판정이 길이의 60%라 길이가 틀리면 완료가 안 된다).
     * 호출: LessonVideoService.linkVideo (관리자·강사 영상 업로드 화면의 [차시에 연결])
     * DB CHECK ck_lessons_video(경로 비어 있지 않음, 길이 > 0)와 같은 조건을 먼저 확인한다.
     */
    public void changeVideo(String videoKey, BigDecimal durationSec) {
        if (videoKey == null || videoKey.isBlank() || durationSec == null || durationSec.signum() <= 0) {
            throw new IllegalArgumentException("영상 경로와 0보다 큰 길이가 필요합니다.");
        }
        this.videoKey = videoKey;
        this.durationSec = durationSec;
    }
}
