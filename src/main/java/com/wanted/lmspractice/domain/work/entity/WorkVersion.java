package com.wanted.lmspractice.domain.work.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 최종 과제·일반 작품의 제출·수정 버전 (제목·설명). 이미지(WorkFile)는 버전에 붙는다
 * 담당: 팀원 4 · #16
 * 칼럼은 resources/sql/schema.sql의 `work_versions` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "work_versions")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class WorkVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;  // DB가 자동 기록

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;  // DB가 자동 기록

    /** 새 버전 — versionNo 는 작품 안에서 1부터 (UNIQUE work_id+version_no) */
    public static WorkVersion create(Long workId, int versionNo, String title, String description) {
        WorkVersion version = new WorkVersion();
        version.workId = workId;
        version.versionNo = versionNo;
        version.title = title;
        version.description = description;
        return version;
    }

    /** 평가 전 수정 — 같은 버전의 제목·설명만 바꾼다 (버전 번호·평가 회차 연결 유지) */
    public void edit(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
