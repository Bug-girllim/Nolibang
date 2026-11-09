package com.wanted.lmspractice.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 강사의 세부 취미 활동 권한 (분반 담당 자격·공개 댓글 자격)
 * 담당: 팀원 1 · #05
 * 칼럼은 resources/sql/schema.sql의 `instructor_hobby_grants` 표와 1:1로 매핑했다(11-06). 표가 바뀌면 이 파일도 같이 바꾼다.
 * 다른 표를 가리키는 칼럼(…_id)은 Long 값으로만 둔다 — 다른 도메인 엔티티를 직접 참조하지 않는 규칙(같은 도메인 안의 연관관계가 필요하면 담당자가 @ManyToOne으로 바꿔도 된다).
 * 복합 기본키(instructor_id, hobby_id) → @IdClass(InstructorHobbyGrantId.class). Repository는 JpaRepository<InstructorHobbyGrant, InstructorHobbyGrantId>.
 * DB 기본값이 있는 NOT NULL 칼럼은 필드 초기값으로 맞춰 두었다. 비즈니스 메서드·정적 팩토리(create())는 도메인 담당자가 추가.
 */
@Entity
@Table(name = "instructor_hobby_grants")
@IdClass(InstructorHobbyGrantId.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class InstructorHobbyGrant {

    @Id
    @Column(name = "instructor_id")
    private Long instructorId;

    @Id
    @Column(name = "hobby_id")
    private Long hobbyId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;  // DB 기본값과 같은 초기값

    @Column(name = "granted_at", nullable = false, insertable = false)
    private LocalDateTime grantedAt;  // 생성 시 DB 기본값(현재 시각), 이후 변경 가능

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
