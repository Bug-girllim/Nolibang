package com.wanted.lmspractice.domain.notification.service;

import com.wanted.lmspractice.domain.notification.entity.Notification;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [역할] 알림이 가리키는 대상 화면 URL을 정한다. 대상이 없어졌으면(삭제된 작품·댓글 등) 비어 있는 값을 돌려준다.
 * [담당] 팀원 4 · #17   [근거] API NT-01 "이동 화면" 표, 10_TRACE §6-7(대상 존재 확인)
 * [연결]
 *  - 호출받음: NotificationService.open (GET /notifications/{id})
 *  - 읽음: 대상 표를 존재 확인용 native 쿼리로만 읽는다(수강·평가 회차·작품·댓글·문의·분반). 쓰지는 않는다.
 * [기술] 🟢 EntityManager native 쿼리 (#07 InstructorStatQuery 방식).
 *  다른 도메인 Repository를 주입하지 않으려고 이 방식을 썼다 — 이 클래스가 읽는 표가 바뀌면 같이 고친다.
 *  (NotificationService → EnrollmentService 같은 계약 호출은 SeatService → NotificationService와 순환 의존이 되어 쓰지 않았다.)
 * 대상 화면의 접근 권한(본인 것인지 등)은 각 화면이 다시 검사한다. 여기서는 "화면을 열 수 있는 대상이 남아 있는가"만 본다.
 */
@Component
@RequiredArgsConstructor
public class NotificationLinkResolver {

    private final EntityManager em;

    /** 이동할 URL. 대상이 없으면 empty */
    public Optional<String> resolve(Notification n) {
        Long id = n.getTargetId();
        return switch (n.getKind()) {
            case WAITLIST_CONFIRMED, COURSE_COMPLETED -> existsIn("enrollments", id, "") ? Optional.of("/my/learning") : Optional.empty();
            case REVIEW_APPROVED, REVIEW_REJECTED ->
                    existsIn("review_rounds", id, "") ? Optional.of("/assignments/" + id + "/result") : Optional.empty();
            case WORK_HIDDEN, WORK_RESTORED ->
                    existsIn("works", id, " AND deleted_at IS NULL") ? Optional.of("/my/works/" + id + "/moderation") : Optional.empty();
            case COMMENT_ON_MY_WORK, REPLY_TO_MY_COMMENT, PUBLIC_COMMENT ->
                    workIdOfComment(id).map(workId -> "/works/" + workId);
            case INQUIRY_ANSWERED ->
                    existsIn("inquiry_threads", id, "") ? Optional.of("/inquiries/" + id) : Optional.empty();
            case INQUIRY_RECEIVED ->
                    existsIn("inquiry_threads", id, "") ? Optional.of("/instructor/inquiries/" + id) : Optional.empty();
            case REVIEW_SUBMITTED ->
                    existsIn("review_rounds", id, "") ? Optional.of("/instructor/reviews/" + id) : Optional.empty();
            case SECTION_ASSIGNED -> existsIn("course_sections", id, "") ? Optional.of("/instructor") : Optional.empty();
        };
    }

    /** table·extra는 위 switch의 고정 문자열만 들어온다(사용자 입력 아님). id만 파라미터로 바인딩한다. */
    private boolean existsIn(String table, Long id, String extra) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE id = :id" + extra)
                .setParameter("id", id)
                .getSingleResult();
        return count.longValue() > 0;
    }

    /** 삭제된 댓글이나 삭제된 작품의 댓글이면 empty */
    private Optional<Long> workIdOfComment(Long commentId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT c.work_id FROM work_comments c JOIN works w ON w.id = c.work_id "
                                + "WHERE c.id = :id AND c.status = 'NORMAL' AND w.deleted_at IS NULL")
                .setParameter("id", commentId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(((Number) rows.get(0)).longValue());
    }
}
