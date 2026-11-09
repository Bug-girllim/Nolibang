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

    /*
     * 부여 시각 — create()에서 직접 넣는다. DB 기본값(insertable = false)에 맡기면 메모리 값이 null로 남아,
     * 같은 트랜잭션에서 회수(revoke)할 때 UPDATE가 granted_at = NULL을 보내 NOT NULL 제약에 걸린다(#05 테스트에서 확인).
     */
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * 새 권한 부여 (AI-05).
     * 호출: InstructorAccountService.grantHobby·create (#05)
     *
     * @param now 부여 시각 (regrant·revoke와 같이 서비스가 LocalDateTime.now()로 넘긴다 — UTC 변환은 Hibernate jdbc.time_zone)
     */
    public static InstructorHobbyGrant create(Long instructorId, Long hobbyId, LocalDateTime now) {
        InstructorHobbyGrant grant = new InstructorHobbyGrant();
        grant.instructorId = instructorId;
        grant.hobbyId = hobbyId;
        grant.grantedAt = now;
        return grant;
    }

    /** 현재 유효한 권한인지 (회수되면 false — 행은 이력으로 남긴다) */
    public boolean isGranted() {
        return Boolean.TRUE.equals(isActive);
    }

    /**
     * 회수했던 권한을 다시 부여 — 기본키(instructor_id, hobby_id)가 같아 새 행을 만들 수 없으므로 기존 행을 되살린다.
     * 호출: InstructorAccountService.grantHobby (#05)
     */
    public void regrant(LocalDateTime now) {
        this.isActive = Boolean.TRUE;
        this.grantedAt = now;
        this.revokedAt = null;
    }

    /** 권한 회수 (AI-06). 호출: InstructorAccountService.revokeHobby (#05) */
    public void revoke(LocalDateTime now) {
        this.isActive = Boolean.FALSE;
        this.revokedAt = now;
    }
}
