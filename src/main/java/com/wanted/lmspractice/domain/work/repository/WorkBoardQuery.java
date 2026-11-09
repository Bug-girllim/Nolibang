package com.wanted.lmspractice.domain.work.repository;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * [역할] 게시판 목록 조회 전용 쿼리 — DB 뷰 v_public_works(공개 + 숨김 아님 + 삭제 아님)에 필터·정렬·페이징을 건다
 * [담당] 팀원 4 · #18   [화면] U-S09, T-S10   [API] WB-01   [기획] FR-034, D-09
 * [연결]
 *  - 호출받음: WorkBoardService.search
 *  - 호출함:   DB(v_public_works, work_comments, work_files, works) — 읽기만 한다
 * [근거] 게시판에 노출할 작품의 조건(공개·미숨김·미삭제)을 한곳(DB 뷰)에 두고, 닉네임·좋아요 수 집계도 뷰를 그대로 쓴다.
 *        카드 한 장에 필요한 댓글 수·대표 이미지·내 댓글 수를 서브쿼리로 한 번에 읽어 N+1 을 피한다(InstructorStatQuery 와 같은 방식).
 * [기술] 🟢 EntityManager native 쿼리 (LIKE 검색어는 %·_ 를 이스케이프해 문자 그대로 검색)
 */
@Repository
public class WorkBoardQuery {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 한 페이지 조회 (다음 쪽 유무를 알기 위해 limit + 1 건을 읽는다)
     * @param hobbyIds null 이면 취미 제한 없음, 빈 목록이면 결과 없음
     */
    @SuppressWarnings("unchecked")
    public List<WorkDTO> find(WorkSearchDTO cond, Collection<Long> hobbyIds, Long viewerId, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(cond, hobbyIds, viewerId, params);
        String order = cond.isSortByLikes()
                ? "ORDER BY v.like_count DESC, v.published_at DESC, v.work_id DESC"   // D-09 좋아요순
                : "ORDER BY v.published_at DESC, v.work_id DESC";                        // 기본: 최신 공개순
        String sql = "SELECT v.work_id, v.kind, v.hobby_id, v.level, v.title, v.published_at, v.author_nickname, v.like_count, "
                + "(SELECT COUNT(*) FROM work_comments c WHERE c.work_id = v.work_id AND c.status = 'NORMAL'), "
                + "(SELECT f.id FROM work_files f JOIN works w ON w.display_version_id = f.work_version_id "
                + "   WHERE w.id = v.work_id AND f.sort_order = 1), "
                + "(SELECT COUNT(*) FROM work_comments m WHERE m.work_id = v.work_id AND m.author_id = ? AND m.status = 'NORMAL'), "
                + "(SELECT COUNT(*) FROM work_likes wl WHERE wl.work_id = v.work_id AND wl.user_id = ?) "
                + "FROM v_public_works v " + where + " " + order + " LIMIT ? OFFSET ?";
        Query query = entityManager.createNativeQuery(sql);
        int index = 1;
        query.setParameter(index++, viewerId);          // 위 SELECT 절의 ? 두 개(내 댓글 수·내가 누른 좋아요)가 먼저 나온다
        query.setParameter(index++, viewerId);
        for (Object param : params) {
            query.setParameter(index++, param);
        }
        query.setParameter(index++, limit);
        query.setParameter(index, offset);

        List<WorkDTO> result = new ArrayList<>();
        for (Object[] row : (List<Object[]>) query.getResultList()) {
            WorkDTO dto = new WorkDTO();
            dto.setWorkId(((Number) row[0]).longValue());
            dto.setKind(WorkKind.valueOf((String) row[1]));
            dto.setHobbyId(((Number) row[2]).longValue());
            dto.setLevel(CourseLevel.fromCode(((Number) row[3]).intValue()));
            dto.setTitle((String) row[4]);
            dto.setPublishedAt(toLocalDateTime(row[5]));
            dto.setAuthorNickname((String) row[6]);
            dto.setLikeCount(((Number) row[7]).longValue());
            dto.setCommentCount(((Number) row[8]).longValue());
            dto.setThumbnailFileId(row[9] == null ? null : ((Number) row[9]).longValue());
            dto.setMyCommentCount(((Number) row[10]).longValue());
            dto.setLiked(((Number) row[11]).longValue() > 0);
            result.add(dto);
        }
        return result;
    }

    // Hibernate 는 DATETIME 을 LocalDateTime 으로 주지만 드라이버·설정에 따라 Timestamp 일 수 있어 둘 다 받는다
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return value == null ? null : ((Timestamp) value).toLocalDateTime();
    }

    /** 같은 조건의 전체 개수 ("공개 작품 N") */
    public long count(WorkSearchDTO cond, Collection<Long> hobbyIds, Long viewerId) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(cond, hobbyIds, viewerId, params);
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM v_public_works v " + where);
        int index = 1;
        for (Object param : params) {
            query.setParameter(index++, param);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    // WHERE 절과 그 ? 자리에 들어갈 값을 같이 만든다. 사용자 입력은 전부 바인딩 값으로만 들어간다(SQL 문자열에 붙이지 않음)
    private String buildWhere(WorkSearchDTO cond, Collection<Long> hobbyIds, Long viewerId, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (cond.getKind() != null) {
            where.append(" AND v.kind = ?");
            params.add(cond.getKind().name());
        }
        if (cond.getLevel() != null) {
            where.append(" AND v.level = ?");
            params.add(cond.getLevel().getCode());
        }
        if (hobbyIds != null) {
            if (hobbyIds.isEmpty()) {
                where.append(" AND 1 = 0");
            } else {
                where.append(" AND v.hobby_id IN (").append(String.join(",", hobbyIds.stream().map(id -> "?").toList())).append(")");
                params.addAll(hobbyIds);
            }
        }
        if (cond.getKeyword() != null && !cond.getKeyword().isBlank()) {
            String like = "%" + cond.getKeyword().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (v.title LIKE ? OR v.author_nickname LIKE ?)");   // 제목·작가 닉네임 (API WB-01)
            params.add(like);
            params.add(like);
        }
        if (cond.isMyCommented()) {
            where.append(" AND EXISTS (SELECT 1 FROM work_comments mc WHERE mc.work_id = v.work_id AND mc.author_id = ? AND mc.status = 'NORMAL')");
            params.add(viewerId);
        }
        return where.toString();
    }
}
