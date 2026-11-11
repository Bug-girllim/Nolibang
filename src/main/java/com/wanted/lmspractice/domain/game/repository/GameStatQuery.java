package com.wanted.lmspractice.domain.game.repository;

import com.wanted.lmspractice.domain.game.dto.ActivityStatDTO;
import com.wanted.lmspractice.domain.game.dto.PeriodStatDTO;
import com.wanted.lmspractice.domain.game.policy.GameRules;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * [역할] RPG 경험치·퀘스트·길드 계산에 쓰는 조회 전용 집계 쿼리 (EntityManager native)
 * [담당] RPG 성장 요소   [화면] 내 캐릭터(game/character.html), 길드(game/guild.html)
 * [연결]
 *  - 호출받음: GameService
 *  - 호출함:   DB(users, enrollments, courses, course_sections, hobbies, lesson_progresses, review_rounds, works, work_comments, work_likes, quest_claims) — 읽기만 한다
 * [근거] 여러 도메인의 표를 한 번에 세야 해서 다른 도메인 Repository 를 주입하지 않고 별도 클래스로 둔다(InstructorStatQuery 와 같은 방식).
 * [주의] 다른 표의 상태값 글자(COMPLETED·GENERAL·PUBLIC·NORMAL·ACTIVE)에 의존한다. enum·CHECK 값이 바뀌면 여기 SQL도 함께 고친다.
 * [기술] 🟢 EntityManager native 쿼리, 🟢 상관 서브쿼리 / 🟡 Java 텍스트 블록(""")
 */
@Repository
public class GameStatQuery {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 사용자 여러 명의 활동 기록을 한 번에 센다 (캐릭터 1명 / 길드 순위 전체 모두 이 쿼리를 쓴다).
     * 호출: GameService.getCharacterPage, getGuildPage, equip
     *
     * @return 사용자 id → 집계. 없는 id 는 들어 있지 않다.
     */
    public Map<Long, ActivityStatDTO> findActivity(Collection<Long> userIds) {
        Map<Long, ActivityStatDTO> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        Query query = entityManager.createNativeQuery("""
                SELECT u.id,
                  (SELECT COUNT(*) FROM lesson_progresses lp
                     JOIN enrollments e ON e.id = lp.enrollment_id
                    WHERE e.user_id = u.id AND lp.completed_at IS NOT NULL),
                  (SELECT COUNT(*) FROM review_rounds rr
                     JOIN enrollments e ON e.id = rr.enrollment_id
                    WHERE e.user_id = u.id),
                  (SELECT COALESCE(SUM(:bossBase + :bossPerLevel * c.level), 0) FROM enrollments e
                     JOIN courses c ON c.id = e.course_id
                    WHERE e.user_id = u.id AND e.status = 'COMPLETED'),
                  (SELECT COUNT(*) FROM works w
                    WHERE w.author_id = u.id AND w.kind = 'GENERAL' AND w.visibility = 'PUBLIC'
                      AND w.deleted_at IS NULL AND w.admin_hidden = 0),
                  (SELECT COUNT(*) FROM work_comments wc
                    WHERE wc.author_id = u.id AND wc.status = 'NORMAL'),
                  (SELECT COUNT(*) FROM work_likes wl
                     JOIN works w ON w.id = wl.work_id
                    WHERE w.author_id = u.id AND wl.user_id <> u.id AND w.deleted_at IS NULL),
                  (SELECT COALESCE(SUM(q.reward_xp), 0) FROM quest_claims q WHERE q.user_id = u.id),
                  (SELECT COUNT(*) FROM enrollments e WHERE e.user_id = u.id AND e.status = 'COMPLETED'),
                  (SELECT COUNT(*) FROM quest_claims q WHERE q.user_id = u.id)
                FROM users u
                WHERE u.id IN (:ids)
                """);
        query.setParameter("bossBase", GameRules.XP_BOSS_BASE);
        query.setParameter("bossPerLevel", GameRules.XP_BOSS_PER_LEVEL);
        query.setParameter("ids", userIds);
        for (Object row : query.getResultList()) {
            Object[] r = (Object[]) row;
            Long id = num(r[0]);
            result.put(id, new ActivityStatDTO(id, (int) num(r[1]), (int) num(r[2]), num(r[3]),
                    (int) num(r[4]), (int) num(r[5]), (int) num(r[6]), num(r[7]),
                    (int) num(r[8]), (int) num(r[9])));
        }
        return result;
    }

    /**
     * 한 사람의 기간(since 이후) 활동 수 — 퀘스트 진행도.
     * since 는 서버 시간대의 LocalDateTime 이다(CONVENTIONS §9 — Hibernate 가 UTC 로 바꿔 비교한다).
     * 호출: GameService.getCharacterPage, claimQuest
     */
    public PeriodStatDTO findSince(Long userId, LocalDateTime since) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                  (SELECT COUNT(*) FROM lesson_progresses lp
                     JOIN enrollments e ON e.id = lp.enrollment_id
                    WHERE e.user_id = :uid AND lp.completed_at >= :since),
                  (SELECT COUNT(*) FROM work_comments wc
                    WHERE wc.author_id = :uid AND wc.status = 'NORMAL' AND wc.created_at >= :since),
                  (SELECT COUNT(*) FROM work_likes wl
                    WHERE wl.user_id = :uid AND wl.created_at >= :since),
                  (SELECT COUNT(*) FROM works w
                    WHERE w.author_id = :uid AND w.kind = 'GENERAL' AND w.visibility = 'PUBLIC'
                      AND w.deleted_at IS NULL AND w.created_at >= :since),
                  (SELECT COUNT(*) FROM review_rounds rr
                     JOIN enrollments e ON e.id = rr.enrollment_id
                    WHERE e.user_id = :uid AND rr.submitted_at >= :since)
                """);
        query.setParameter("uid", userId);
        query.setParameter("since", since);
        Object[] r = (Object[]) query.getSingleResult();
        return new PeriodStatDTO((int) num(r[0]), (int) num(r[1]), (int) num(r[2]), (int) num(r[3]), (int) num(r[4]));
    }

    /**
     * 길드(분반)별 멤버 목록 — 수강 중(ACTIVE)·수료(COMPLETED)한 사람만, 닉네임만 읽는다(실명 안 읽음).
     * 호출: GameService.getGuildPage
     * 열: 분반 id, 분반 번호, 강의 제목, 취미 이름, 담당 강사 닉네임, 멤버 id, 멤버 닉네임, 길드 기여 경험치
     * 길드 기여 경험치 = 그 수강 건에서 쌓은 경험치(완료한 차시 + 최종 과제 제출 + 수료 보너스).
     * 한 사람이 여러 강의를 들어도 총 경험치를 모든 길드에 중복으로 얹지 않도록, 길드 점수는 이 값만 쓴다.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findGuildMembers() {
        Query query = entityManager.createNativeQuery("""
                SELECT s.id, s.section_no, c.title, h.name, ins.nickname, u.id, u.nickname,
                  (SELECT COUNT(*) FROM lesson_progresses lp
                    WHERE lp.enrollment_id = e.id AND lp.completed_at IS NOT NULL) * :lessonXp
                  + (SELECT COUNT(*) FROM review_rounds rr WHERE rr.enrollment_id = e.id) * :submitXp
                  + CASE WHEN e.status = 'COMPLETED' THEN :bossBase + :bossPerLevel * c.level ELSE 0 END
                FROM enrollments e
                JOIN course_sections s ON s.id = e.section_id
                JOIN courses c ON c.id = s.course_id
                JOIN hobbies h ON h.id = c.hobby_id
                JOIN users u ON u.id = e.user_id
                LEFT JOIN users ins ON ins.id = s.instructor_id
                WHERE e.status IN ('ACTIVE', 'COMPLETED') AND u.status = 'ACTIVE' AND u.role = 'USER'
                ORDER BY s.id, u.id
                """);
        query.setParameter("lessonXp", GameRules.XP_LESSON);
        query.setParameter("submitXp", GameRules.XP_SUBMIT);
        query.setParameter("bossBase", GameRules.XP_BOSS_BASE);
        query.setParameter("bossPerLevel", GameRules.XP_BOSS_PER_LEVEL);
        return query.getResultList();
    }

    private static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
