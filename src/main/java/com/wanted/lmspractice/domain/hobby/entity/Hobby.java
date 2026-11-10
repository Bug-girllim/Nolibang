package com.wanted.lmspractice.domain.hobby.entity;

import jakarta.persistence.Column;
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
 * 세부 취미 (시드 데이터)
 * 담당: 팀원 2 · #03
 * 칼럼은 resources/sql/schema.sql의 `hobbies` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "hobbies")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Hobby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "thumbnail_key", length = 512)
    private String thumbnailKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ExposureState state = ExposureState.PRIVATE;  // DB 기본값과 같은 초기값

    @JdbcTypeCode(SqlTypes.TINYINT)  // DB가 TINYINT 이라 validate 통과용 (schema.sql 기준)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;  // DB 기본값과 같은 초기값

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 관리자 세부 취미 추가(A-S03) — 새 세부 취미 엔티티를 만든다. 호출: HobbyService.createHobby
     * 대표 이미지(thumbnail_key)는 아직 업로드 기능이 없어 비워 둔다. 소개가 없으면 빈 문자열(DB NOT NULL).
     */
    public static Hobby create(Long categoryId, String code, String name, String description,
                               ExposureState state, int sortOrder) {
        return new Hobby(null, categoryId, code, name, description == null ? "" : description,
                null, state, sortOrder, null, null);
    }

    /**
     * 노출 상태 바꾸기 (A-S15 세부 취미 상태 편집). 호출: HobbyService.changeHobbyState — 트랜잭션 안에서 바꾸면 변경 감지로 저장된다.
     * 대분류가 비공개·준비 중이면 세부 취미를 운영 중으로 바꿔도 학습자에게는 보이지 않는다(상위 규칙 우선). 수강 중인 학습자의 접근은 그대로(D-20).
     */
    public void changeState(ExposureState newState) {
        this.state = newState;
    }

    /** 관리자 세부 취미 편집(A-S03) — 대분류·이름·소개·노출 상태를 바꾼다. 호출: HobbyService.updateHobby (변경 감지로 저장) */
    public void update(Long categoryId, String name, String description, ExposureState newState) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description == null ? "" : description;
        this.state = newState;
    }
}
