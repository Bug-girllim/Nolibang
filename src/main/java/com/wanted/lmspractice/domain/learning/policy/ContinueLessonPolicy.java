package com.wanted.lmspractice.domain.learning.policy;

import com.wanted.lmspractice.domain.learning.dto.LessonProgressDTO;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [역할] [이어서 학습]·[다시 보기] 버튼이 열 차시를 고른다 — 차시 순서대로 처음으로 안 끝낸 차시, 모두 끝났으면 마지막 차시
 * [담당] 팀원 2 · #11(나의 학습 현황)·#08(강의 목록·상세) 후속   [화면] U-S08, U-S03, U-S04, U-S02   [기획] D-42(수료 후에도 다시 보기)
 * [연결]
 *  - 호출받음: MyLearningService(내 학습 현황 카드), CourseCatalogService(강의 목록·상세·취미 상세의 신청 영역)
 *  - 호출함:   없음 (순수 계산, DB를 부르지 않는다)
 * [기술] 🟢 정적 메서드
 *
 * 왜 진도 목록만으로 고르지 않나: ProgressService.getLessonStatuses(계약 C10)는 "재생 기록이 있는 차시"만 돌려준다.
 * 아직 한 번도 재생하지 않은 차시는 목록에 없으므로, 그 목록에서 첫 미완료를 고르면 안 본 차시를 건너뛰고,
 * 새로 확정된 수강(기록이 하나도 없음)은 고를 차시가 없어 버튼이 막힌다. 그래서 차시 순서는 강의의 전체 차시 목록에서 받고,
 * 진도는 "끝낸 차시인지"만 확인하는 데 쓴다. (차시 id는 lesson_no 순서와 같지 않으므로 id 크기로 고르지 않는다.)
 */
public final class ContinueLessonPolicy {

    private ContinueLessonPolicy() {
    }

    /**
     * @param orderedLessonIds 강의의 전체 차시 id — 1차시, 2차시, 3차시 순서
     * @param progress         이 수강 건의 차시별 진도 (재생 기록이 있는 차시만 있을 수 있다)
     * @return 열 차시 id. 강의에 차시가 없으면 null
     */
    public static Long pick(List<Long> orderedLessonIds, List<LessonProgressDTO> progress) {
        if (orderedLessonIds.isEmpty()) {
            return null;
        }
        Set<Long> completed = new HashSet<>();
        for (LessonProgressDTO lesson : progress) {
            if (lesson.isCompleted()) {
                completed.add(lesson.getLessonId());
            }
        }
        for (Long lessonId : orderedLessonIds) {
            if (!completed.contains(lessonId)) {
                return lessonId;
            }
        }
        return orderedLessonIds.get(orderedLessonIds.size() - 1);  // 모두 끝냈으면 마지막 차시
    }
}
