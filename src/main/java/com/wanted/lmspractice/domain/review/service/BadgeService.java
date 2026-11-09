package com.wanted.lmspractice.domain.review.service;

import com.wanted.lmspractice.domain.course.dto.CourseBriefDTO;
import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.enrollment.dto.MyEnrollmentDTO;
import com.wanted.lmspractice.domain.enrollment.entity.EnrollmentStatus;
import com.wanted.lmspractice.domain.enrollment.service.EnrollmentService;
import com.wanted.lmspractice.domain.review.dto.BadgeDTO;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 수준·마스터 배지를 계산한다. 저장하지 않고 조회할 때마다 수료한 수강 건에서 만든다(10_TRACE).
 * [담당] 팀원 3 · #15   [기획] FR-024·025
 * [연결]
 *  - 호출받음: ReviewResultService(결과 화면), 팀원 4 CommentPolicy(#18)·WorkLevelPolicy(#19)
 *  - 호출함:   EnrollmentService.getMyEnrollments(C18, 수료 건 조회), CourseService.getCourseBriefs(강의→취미·수준)
 *  - 계약:     C12 BadgeService — 시그니처 변경 금지
 * 규칙: 수료(COMPLETED)한 강의의 (취미, 수준)마다 수준 배지 1개. 한 취미에서 입문·중급·고급을 모두 수료하면 그 취미의 마스터 배지 1개를 더한다.
 * 평가 대기·진행 중, 반려, 취소·만료는 수료가 아니므로 배지가 없다.
 * [기술] 🟢 JPA 조회 + Java 컬렉션 그룹핑
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final EnrollmentService enrollmentService;
    private final CourseService courseService;

    /**
     * 계약 C12: 보유 배지 목록 — 취미 id 순, 한 취미 안에서는 입문→중급→고급, 마지막에 마스터.
     * 호출: ReviewResultService.getResult, 팀원 4
     */
    @Transactional(readOnly = true)
    public List<BadgeDTO> getBadges(Long userId) {
        List<Long> completedCourseIds = enrollmentService.getMyEnrollments(userId).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED)
                .map(MyEnrollmentDTO::getCourseId)
                .toList();
        if (completedCourseIds.isEmpty()) {
            return List.of();
        }

        // 취미별로 수료한 수준을 모은다 (같은 수준을 여러 강의로 수료해도 배지는 1개)
        Map<Long, String> hobbyNames = new TreeMap<>();
        Map<Long, Set<CourseLevel>> levelsByHobby = new TreeMap<>();
        for (CourseBriefDTO brief : courseService.getCourseBriefs(completedCourseIds).values()) {
            hobbyNames.put(brief.getHobbyId(), brief.getHobbyName());
            levelsByHobby.computeIfAbsent(brief.getHobbyId(), id -> EnumSet.noneOf(CourseLevel.class))
                    .add(brief.getLevel());
        }

        List<BadgeDTO> badges = new ArrayList<>();
        levelsByHobby.forEach((hobbyId, levels) -> {
            levels.forEach(level -> badges.add(BadgeDTO.levelBadge(hobbyId, hobbyNames.get(hobbyId), level)));
            if (levels.size() == CourseLevel.values().length) {   // 세 수준 모두 수료 → 마스터
                badges.add(BadgeDTO.masterBadge(hobbyId, hobbyNames.get(hobbyId)));
            }
        });
        return badges;
    }

    /**
     * 계약 C12: 해당 취미의 최고 배지 수준 (없으면 null).
     * 호출: 팀원 4 WorkLevelPolicy(#19) — 작품 수준 태그 허용 범위 판단
     */
    @Transactional(readOnly = true)
    public CourseLevel getHighestLevel(Long userId, Long hobbyId) {
        CourseLevel highest = null;
        for (BadgeDTO badge : getBadges(userId)) {
            if (!badge.isMaster() && badge.getHobbyId().equals(hobbyId)
                    && (highest == null || badge.getLevel().getCode() > highest.getCode())) {
                highest = badge.getLevel();
            }
        }
        return highest;
    }

    /**
     * 계약 C12: 특정 수준 배지 보유 여부.
     * 호출: 팀원 4 CommentPolicy(#18) — 같은 취미·수준 배지가 있어야 댓글 가능
     */
    @Transactional(readOnly = true)
    public boolean hasBadge(Long userId, Long hobbyId, CourseLevel level) {
        return getBadges(userId).stream()
                .anyMatch(b -> !b.isMaster() && b.getHobbyId().equals(hobbyId) && b.getLevel() == level);
    }
}
