package com.wanted.lmspractice.domain.hobby.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 대분류 (시드 데이터)
 * 담당: 팀원 2 · #03
 * 칼럼은 resources/sql/schema.sql의 `categories` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "categories")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ExposureState state = ExposureState.PRIVATE;  // DB 기본값과 같은 초기값

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;  // DB 기본값과 같은 초기값

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /**
     * 관리자 대분류 추가(A-S02) — 새 대분류 엔티티를 만든다. 호출: HobbyService.createCategory
     * code는 화면에서 받지 않고 서비스가 겹치지 않게 만들어 넣는다(이미지 매핑용 시드 code와 구분).
     */
    public static Category create(String code, String name, ExposureState state, int sortOrder) {
        return new Category(null, code, name, state, sortOrder, null, null);
    }

    /**
     * 노출 상태 바꾸기 (A-S15 대분류 상태 편집). 호출: HobbyService.changeCategoryState — 트랜잭션 안에서 바꾸면 변경 감지로 저장된다.
     * 상태가 바뀌어도 이미 수강 중인 학습자의 학습·기록 접근은 그대로다(D-20).
     */
    public void changeState(ExposureState newState) {
        this.state = newState;
    }

    /** 관리자 대분류 편집(A-S02) — 이름·노출 상태를 바꾼다. 호출: HobbyService.updateCategory (변경 감지로 저장) */
    public void update(String name, ExposureState newState) {
        this.name = name;
        this.state = newState;
    }
}
