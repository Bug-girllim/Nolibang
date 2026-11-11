package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.review.dto.ReviewStatusDTO;
import com.wanted.lmspractice.domain.review.service.ReviewQueryService;
import com.wanted.lmspractice.domain.work.dto.TimelineDTO;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkFile;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.repository.WorkFileRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 개인 성장 타임라인(최종 과제 + 일반 작품을 날짜순으로), 취미별 첫/최근 작품 비교, 한 줄 회고 저장
 * [담당] 팀원 4 · #19   [화면] U-S12 — templates/work/growth.html   [기획] FR-028, D-13·D-14·D-17   [API] MW-06·MW-07
 * [연결]
 *  - 호출받음: MyWorkController.growth (GET /my/works/growth), MyWorkController.saveReflection (POST /my/works/{workId}/reflection)
 *  - 호출함:   Work·WorkVersion·WorkFile Repository(같은 도메인), HobbyService.getHobbiesIgnoringVisibility(취미 이름),
 *             ReviewQueryService.getLatestStatus (계약 C11 — 최종 과제의 평가 상태·공식 피드백 링크)
 * [규칙]
 *  - 순서: works.created_at 최신순 — DB가 처음 넣을 때만 기록하므로 작품을 수정해도 순서가 바뀌지 않는다
 *  - 준비 중·비공개로 바뀐 취미의 작품도 내 기록이므로 계속 보인다(D-20과 같은 취지)
 *  - 회고는 선택 입력이며 수료와 무관하다
 * [기술] 🟢 @Transactional(readOnly), 🟢 쿼리 메서드, 🟢 JPA 변경 감지(회고 저장)
 */
@Service
@RequiredArgsConstructor
public class GrowthService {

    private final WorkRepository workRepository;
    private final WorkVersionRepository workVersionRepository;
    private final WorkFileRepository workFileRepository;
    private final HobbyService hobbyService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 성장 타임라인 (API MW-07)
     * 흐름: 내 작품 전체 조회(최신순) → 필터 선택지(작품을 남긴 취미) → 고른 취미로 거르기 → 항목 변환 → 취미를 골랐으면 첫/최근 작품
     * 호출: MyWorkController.growth
     *
     * @param hobbyIdOrNull null 이면 전체 보기(첫/최근 작품 없음)
     */
    @Transactional(readOnly = true)
    public TimelineDTO getTimeline(Long userId, Long hobbyIdOrNull) {
        List<Work> works = workRepository.findByAuthorIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(work -> work.getDisplayVersionId() != null)   // 보여 줄 버전이 없는 작품은 기록에서 뺀다
                .toList();

        Map<Long, HobbyDTO> hobbies = hobbyService.getHobbiesIgnoringVisibility(
                works.stream().map(Work::getHobbyId).distinct().toList());
        List<HobbyDTO> hobbyOptions = hobbies.values().stream()
                .sorted(Comparator.comparing(HobbyDTO::getId))
                .toList();

        List<Work> shown = hobbyIdOrNull == null ? works
                : works.stream().filter(work -> work.getHobbyId().equals(hobbyIdOrNull)).toList();
        Map<Long, WorkVersion> versions = workVersionRepository.findAllById(
                        shown.stream().map(Work::getDisplayVersionId).toList()).stream()
                .collect(Collectors.toMap(WorkVersion::getId, Function.identity()));
        List<TimelineDTO.Item> items = shown.stream()
                .map(work -> toItem(work, versions.get(work.getDisplayVersionId()), hobbies.get(work.getHobbyId())))
                .toList();

        // 모든 항목에 대표 이미지(첫 장)를 채운다 — 한 번의 조회로 버전별 첫 장만 고른다
        Map<Long, Long> firstFileByVersion = new java.util.HashMap<>();
        workFileRepository.findByWorkVersionIdInOrderByWorkVersionIdAscSortOrderAsc(
                        shown.stream().map(Work::getDisplayVersionId).toList())
                .forEach(file -> firstFileByVersion.putIfAbsent(file.getWorkVersionId(), file.getId()));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setThumbnailFileId(firstFileByVersion.get(shown.get(i).getDisplayVersionId()));   // shown 과 items 는 같은 순서
        }

        TimelineDTO timeline = new TimelineDTO(hobbyIdOrNull, null, hobbyOptions, items, null, null);
        if (hobbyIdOrNull != null && !items.isEmpty()) {
            fillFirstAndLatest(timeline, shown, items, hobbies.get(hobbyIdOrNull));   // shown 과 items 는 같은 순서
        }
        return timeline;
    }

    /**
     * 한 줄 회고 저장 (API MW-06, D-14) — 내 작품(최종 과제·일반 작품)이면 저장, 빈 값이면 삭제.
     * 호출: MyWorkController.saveReflection
     * @throws BusinessException WORK_NOT_FOUND(없음·남의 작품 — 존재를 숨김), INVALID_INPUT(500자 초과)
     */
    @Transactional
    public void saveReflection(Long userId, Long workId, String reflection) {
        Work work = workRepository.findById(workId)
                .filter(w -> w.isOwnedActiveBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
        work.writeReflection(reflection);   // 변경 감지로 저장
    }

    // 작품 1개 → 타임라인 항목. 최종 과제면 최근 평가 회차(공식 피드백 링크·상태 칩)를 함께 담는다
    private TimelineDTO.Item toItem(Work work, WorkVersion version, HobbyDTO hobby) {
        ReviewStatusDTO review = work.isGeneral() || work.getEnrollmentId() == null
                ? ReviewStatusDTO.notSubmitted()
                : reviewQueryService.getLatestStatus(work.getEnrollmentId());
        return TimelineDTO.Item.of(work, version, hobby == null ? "" : hobby.getName(),
                review.getReviewRoundId(), review.getStatus());
    }

    /**
     * D-13: 취미를 고르면 "나의 첫 시작"(가장 오래된 작품)과 "가장 최근 실습"(가장 최근 작품) 썸네일 2개.
     * items 는 최신순이므로 맨 뒤가 첫 작품, 맨 앞이 최근 작품이다. 작품이 1개면 첫 작품만 보인다.
     */
    private void fillFirstAndLatest(TimelineDTO timeline, List<Work> shown, List<TimelineDTO.Item> items, HobbyDTO hobby) {
        int last = items.size() - 1;
        timeline.setSelectedHobbyName(hobby == null ? null : hobby.getName());
        timeline.setFirst(withThumbnail(items.get(last), shown.get(last)));
        if (items.size() > 1) {
            timeline.setLatest(withThumbnail(items.get(0), shown.get(0)));
        }
    }

    // 대표 이미지(첫 장) id 를 채운다 — 화면은 /files/works/{id} 권한 검사 URL로만 이미지를 띄운다(계약 C9)
    private TimelineDTO.Item withThumbnail(TimelineDTO.Item item, Work work) {
        item.setThumbnailFileId(workFileRepository.findFirstByWorkVersionIdOrderBySortOrder(work.getDisplayVersionId())
                .map(WorkFile::getId)
                .orElse(null));
        return item;
    }
}
