package com.wanted.lmspractice.domain.hobby.service;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.entity.Category;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import com.wanted.lmspractice.domain.hobby.entity.Hobby;
import com.wanted.lmspractice.domain.hobby.repository.CategoryRepository;
import com.wanted.lmspractice.domain.hobby.repository.HobbyRepository;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 대분류·세부 취미 조회 + 노출 규칙(운영 중/준비 중/비공개, 상위 비공개면 하위도 숨김) — 조회 전용
 * [담당] 팀원 2 · #08   [화면] U-S01·U-S02, A-S15   [기획] 05 §3, D-59(시드 데이터라 등록·수정 없음)   [API] HB-01·02·03
 * [연결]
 *  - 호출받음: HobbyController(목록·상세), AdminHobbyController(관리자 전체), CourseCatalogService·CourseService(강의가 속한 취미의 노출 확인)
 *  - 호출함:   CategoryRepository, HobbyRepository
 *  - 규칙:     비공개 취미는 관리자(ADMIN)만 본다. 그 밖의 사용자(비회원·학습자·강사)에게는 "없는 취미"와 똑같이 HOBBY_NOT_FOUND로 보여
 *             존재 자체를 숨긴다(API HB-02). 이 서비스는 다른 도메인(강의·수강)을 알지 못한다 — 의존 방향: course → hobby.
 * [기술] 🟢 @Service·@Transactional(readOnly)·Repository 쿼리 메서드
 */
@Service
@RequiredArgsConstructor
public class HobbyService {

    private final CategoryRepository categoryRepository;
    private final HobbyRepository hobbyRepository;

    /**
     * 노출 규칙을 적용한 대분류·세부 취미 목록 (HB-01, 사이드 메뉴).
     * 비공개 대분류와 그 아래 세부 취미는 관리자에게만 보인다. 비공개 세부 취미도 마찬가지다.
     * 호출: HobbyController.list·category, CourseController(사이드 메뉴), AdminHobbyController(ADMIN)
     *
     * @param viewerRole 보는 사람의 역할 — 비회원이면 null
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getCategories(Role viewerRole) {
        Map<Long, List<Hobby>> hobbiesByCategory = hobbyRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.groupingBy(Hobby::getCategoryId));

        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(category -> isVisible(category.getState(), viewerRole))
                .map(category -> CategoryDTO.from(category, toVisibleHobbies(
                        category, hobbiesByCategory.getOrDefault(category.getId(), List.of()), viewerRole)))
                .toList();
    }

    /**
     * 대분류 한 개와 그 아래 세부 취미 (대분류별 보기 U-S01-A).
     * 보이지 않는 대분류(비공개)는 없는 것과 같이 HOBBY_NOT_FOUND로 처리한다.
     * 호출: HobbyController.list(categoryId가 있을 때)
     *
     * @throws BusinessException HOBBY_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public CategoryDTO getCategory(Long categoryId, Role viewerRole) {
        return getCategories(viewerRole).stream()
                .filter(category -> category.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
    }

    /**
     * 세부 취미 상세의 기본 정보(수준별 강의 목록은 비어 있음 — CourseCatalogService가 채운다). U-S02.
     * 호출: CourseCatalogService.getHobbyDetail ← HobbyController.detail (GET /hobbies/{hobbyId}, API HB-02)
     *
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 취미이거나, 비공개 취미를 관리자가 아닌 사람이 연 경우
     */
    @Transactional(readOnly = true)
    public HobbyDetailDTO getHobby(Long hobbyId, Role viewerRole) {
        Hobby hobby = getVisibleHobby(hobbyId, viewerRole);
        Category category = categoryRepository.findById(hobby.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        return HobbyDetailDTO.from(hobby, effectiveState(category, hobby, viewerRole), category.getName());
    }

    /**
     * 세부 취미 한 개의 목록용 정보. 강의 목록·상세가 "이 취미를 볼 수 있는지"와 "신청 가능한 취미인지"를 확인할 때 쓴다.
     * 호출: CourseService(강의가 속한 취미의 노출 확인), CourseCatalogService, CourseController(강의 목록 머리)
     *
     * @throws BusinessException HOBBY_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public HobbyDTO getHobbySummary(Long hobbyId, Role viewerRole) {
        Hobby hobby = getVisibleHobby(hobbyId, viewerRole);
        Category category = categoryRepository.findById(hobby.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        return HobbyDTO.from(hobby, effectiveState(category, hobby, viewerRole));
    }

    /**
     * 세부 취미 여러 개의 목록용 정보를 노출 규칙 없이 읽는다 — 본인 수강 기록 화면용.
     * 취미가 준비 중으로 바뀌어도 이미 수강 중인 강의는 계속 보여야 하므로(D-20) 비공개 여부로 걸러내지 않는다.
     * 상태는 학습자 기준(effectiveState)이라 상위 대분류가 준비 중이면 "준비 중"으로 나온다.
     * 호출: CourseService.getCourseBriefs ← MyLearningService (GET /my/learning)
     *
     * @return 취미 id → HobbyDTO (없는 id는 빠진다)
     */
    @Transactional(readOnly = true)
    public Map<Long, HobbyDTO> getHobbiesIgnoringVisibility(Collection<Long> hobbyIds) {
        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        return hobbyRepository.findAllById(hobbyIds).stream()
                .collect(Collectors.toMap(Hobby::getId, hobby -> HobbyDTO.from(
                        hobby, effectiveState(categories.get(hobby.getCategoryId()), hobby, Role.USER))));
    }

    // ───────────────────────── 노출 규칙 ─────────────────────────

    /** 보는 사람에게 보이는 세부 취미만 찾는다. 없거나 보이지 않으면 HOBBY_NOT_FOUND */
    private Hobby getVisibleHobby(Long hobbyId, Role viewerRole) {
        Hobby hobby = hobbyRepository.findById(hobbyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        Category category = categoryRepository.findById(hobby.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        if (!isVisible(effectiveState(category, hobby, viewerRole), viewerRole)) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_FOUND);  // 존재를 숨기려고 "없음"으로 응답 (API HB-02)
        }
        return hobby;
    }

    /** 한 대분류 아래에서 보는 사람에게 보이는 세부 취미만 DTO로 바꾼다 */
    private List<HobbyDTO> toVisibleHobbies(Category category, List<Hobby> hobbies, Role viewerRole) {
        return hobbies.stream()
                .filter(hobby -> isVisible(effectiveState(category, hobby, viewerRole), viewerRole))
                .map(hobby -> HobbyDTO.from(hobby, effectiveState(category, hobby, viewerRole)))
                .toList();
    }

    /**
     * 화면에 적용되는 세부 취미의 노출 상태 (05 §3).
     * 상위 대분류가 비공개면 하위도 비공개, 상위가 준비 중이면 운영 중이던 하위도 준비 중으로 본다(신청 불가).
     * 관리자는 운영을 위해 취미 자신의 값을 그대로 본다.
     */
    private ExposureState effectiveState(Category category, Hobby hobby, Role viewerRole) {
        if (viewerRole == Role.ADMIN) {
            return hobby.getState();
        }
        if (category.getState() == ExposureState.PRIVATE) {
            return ExposureState.PRIVATE;
        }
        if (category.getState() == ExposureState.PREPARING && hobby.getState() == ExposureState.OPERATING) {
            return ExposureState.PREPARING;
        }
        return hobby.getState();
    }

    /** 비공개가 아니거나 관리자면 보인다 */
    private boolean isVisible(ExposureState state, Role viewerRole) {
        return state != ExposureState.PRIVATE || viewerRole == Role.ADMIN;
    }
}
