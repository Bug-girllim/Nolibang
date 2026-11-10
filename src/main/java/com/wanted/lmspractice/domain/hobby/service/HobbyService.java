package com.wanted.lmspractice.domain.hobby.service;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.CategoryFormDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDetailDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyFormDTO;
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
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 대분류·세부 취미 조회 + 노출 규칙(운영 중/준비 중/비공개, 상위 비공개면 하위도 숨김), 관리자 대분류·세부 취미 추가(A-S02·A-S03)
 * [담당] 팀원 2 · #08   [화면] U-S01·U-S02, A-S15   [기획] 05 §3, D-59(수정·삭제 없음 — 추가만 관리자 화면에서)   [API] HB-01·02·03
 * [연결]
 *  - 호출받음: HobbyController(목록·상세), AdminHobbyController(관리자 전체), CourseCatalogService·CourseService(강의가 속한 취미의 노출 확인)
 *  - 호출함:   CategoryRepository, HobbyRepository
 *  - 규칙:     비공개 취미는 관리자(ADMIN)만, 준비 중 취미는 강사(INSTRUCTOR)·관리자만 본다. 그 밖의 사용자(비회원·학습자)에게는 "없는 취미"와 똑같이 HOBBY_NOT_FOUND로 보여
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
     * 비공개 대분류와 그 아래 세부 취미는 관리자에게만, 준비 중 대분류·세부 취미는 강사·관리자에게만 보인다(비회원·학습자에게는 보이지 않음).
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
     * 보이지 않는 대분류(비공개, 또는 학습자에게 준비 중)는 없는 것과 같이 HOBBY_NOT_FOUND로 처리한다.
     * 호출: HobbyController.list(categoryId가 있을 때)
     *
     * @throws BusinessException HOBBY_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public CategoryDTO getCategory(Long categoryId, Role viewerRole) {
        return pickCategory(getCategories(viewerRole), categoryId);
    }

    /**
     * [성능 개선] 이미 만든 "보이는 대분류 목록"(getCategories 결과)에서 대분류 한 개를 고른다 — 같은 요청에서 목록을 다시 조회하지 않도록.
     * 목록에 없으면(없는 대분류이거나 보이지 않는 대분류) getCategory와 똑같이 HOBBY_NOT_FOUND.
     * 호출: getCategory, HobbyController.list(사이드 메뉴용 menuCategories를 다시 씀)
     *
     * @throws BusinessException HOBBY_NOT_FOUND
     */
    public CategoryDTO pickCategory(List<CategoryDTO> visibleCategories, Long categoryId) {
        return visibleCategories.stream()
                .filter(category -> category.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
    }

    /**
     * 세부 취미 상세의 기본 정보(수준별 강의 목록은 비어 있음 — CourseCatalogService가 채운다). U-S02.
     * 호출: CourseCatalogService.getHobbyDetail ← HobbyController.detail (GET /hobbies/{hobbyId}, API HB-02)
     *
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 취미이거나, 보이지 않는 취미(비공개는 관리자 전용, 준비 중은 강사·관리자 전용)를 연 경우
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

    /**
     * 노출 상태별 열람 가능한 사람 (05 §3 — 새 노출 정책):
     * 운영 중 = 모두, 준비 중 = 강사·관리자(학습자·비회원에게는 보이지 않음), 비공개 = 관리자만.
     * 이미 수강 중인 학습자의 학습·기록 접근(D-20)은 이 규칙을 거치지 않는 경로(getHobbiesIgnoringVisibility,
     * CourseService.getCourseDetailForEnrolled)가 따로 맡는다.
     */
    private boolean isVisible(ExposureState state, Role viewerRole) {
        return switch (state) {
            case OPERATING -> true;
            case PREPARING -> viewerRole == Role.INSTRUCTOR || viewerRole == Role.ADMIN;
            case PRIVATE -> viewerRole == Role.ADMIN;
        };
    }

    // ===== 관리자 추가 (A-S02 대분류 관리 · A-S03 세부 취미) =====

    /** 대분류 이름이 아직 없는지 (A-S02 중복 확인). 호출: AdminHobbyController.createCategory */
    @Transactional(readOnly = true)
    public boolean isCategoryNameAvailable(String name) {
        return !categoryRepository.existsByName(name);
    }

    /** 대분류가 있는지 (A-S03 세부 취미 추가 시 대분류 확인). 호출: AdminHobbyController.createHobby */
    @Transactional(readOnly = true)
    public boolean categoryExists(Long categoryId) {
        return categoryId != null && categoryRepository.existsById(categoryId);
    }

    /** 그 대분류 안에 같은 이름의 세부 취미가 아직 없는지 (A-S03 중복 확인). 호출: AdminHobbyController.createHobby */
    @Transactional(readOnly = true)
    public boolean isHobbyNameAvailable(Long categoryId, String name) {
        return !hobbyRepository.existsByCategoryIdAndName(categoryId, name);
    }

    /**
     * 대분류 추가 (A-S02). 맨 뒤 순서로 넣고 code는 겹치지 않게 만들어 준다. 입력 검증·이름 중복은 컨트롤러가 먼저 확인한다.
     * 호출: AdminHobbyController.createCategory
     *
     * @return 만든 대분류 id
     */
    @Transactional
    public Long createCategory(CategoryFormDTO form) {
        int sortOrder = categoryRepository.findTopByOrderBySortOrderDesc()
                .map(category -> category.getSortOrder() + 1)
                .orElse(1);
        String code = newCode("cat-", categoryRepository::existsByCode);
        return categoryRepository.save(Category.create(code, form.getTrimmedName(), form.getState(), sortOrder)).getId();
    }

    /**
     * 세부 취미 추가 (A-S03). 그 대분류의 맨 뒤 순서로 넣는다. 강의는 아직 없으므로 상세 화면에는 "준비된 강의 없음"으로 보인다.
     * 호출: AdminHobbyController.createHobby
     *
     * @return 만든 세부 취미 id
     * @throws BusinessException HOBBY_NOT_FOUND — 대분류가 없을 때(화면은 목록에서만 고르므로 조작된 요청)
     */
    @Transactional
    public Long createHobby(HobbyFormDTO form) {
        if (!categoryExists(form.getCategoryId())) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_FOUND);
        }
        int sortOrder = hobbyRepository.findTopByCategoryIdOrderBySortOrderDesc(form.getCategoryId())
                .map(hobby -> hobby.getSortOrder() + 1)
                .orElse(1);
        String code = newCode("hobby-", hobbyRepository::existsByCode);
        return hobbyRepository.save(Hobby.create(form.getCategoryId(), code, form.getTrimmedName(),
                form.getTrimmedDescription(), form.getState(), sortOrder)).getId();
    }

    /** 겹치지 않는 code 만들기 (예: cat-3f9a1c2e). code는 UNIQUE 40자 — 시드 code(art·colored-pencil 등)와 겹칠 일이 없는 형식 */
    private String newCode(String prefix, Predicate<String> exists) {
        String code;
        do {
            code = prefix + UUID.randomUUID().toString().substring(0, 8);
        } while (exists.test(code));
        return code;
    }

    /**
     * 대분류 노출 상태 바꾸기 (A-S15 대분류 상태 편집). 호출: AdminHobbyController.changeCategoryState
     *
     * @return 바꾼 대분류 이름(안내 문구용)
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 대분류
     */
    @Transactional
    public String changeCategoryState(Long categoryId, ExposureState state) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        category.changeState(state);
        return category.getName();
    }

    /**
     * 세부 취미 노출 상태 바꾸기 (A-S15 세부 취미 상태 편집). 호출: AdminHobbyController.changeHobbyState
     *
     * @return 바꾼 세부 취미 이름(안내 문구용)
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 세부 취미
     */
    @Transactional
    public String changeHobbyState(Long hobbyId, ExposureState state) {
        Hobby hobby = hobbyRepository.findById(hobbyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        hobby.changeState(state);
        return hobby.getName();
    }

    // ===== 관리자 편집·삭제 (A-S02 대분류 관리 · A-S03 세부 취미) =====

    /** 편집할 대분류의 현재 값 (편집 패널을 미리 채운다). 호출: AdminHobbyController.categories(?edit=) */
    @Transactional(readOnly = true)
    public CategoryFormDTO getCategoryForm(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        return new CategoryFormDTO(category.getName(), category.getState());
    }

    /** 편집하는 대분류 말고 같은 이름이 없는지 (A-S02). 호출: AdminHobbyController.updateCategory */
    @Transactional(readOnly = true)
    public boolean isCategoryNameAvailable(String name, Long exceptCategoryId) {
        return !categoryRepository.existsByNameAndIdNot(name, exceptCategoryId);
    }

    /**
     * 대분류 편집 (A-S02) — 이름·노출 상태. 호출: AdminHobbyController.updateCategory
     *
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 대분류
     */
    @Transactional
    public void updateCategory(Long categoryId, CategoryFormDTO form) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        category.update(form.getTrimmedName(), form.getState());
    }

    /**
     * 대분류 삭제 (A-S02) — 세부 취미가 하나도 없을 때만 지운다(잘못 만든 대분류 정리용).
     * 호출: AdminHobbyController.deleteCategory
     *
     * @return 지운 대분류 이름
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 대분류, INVALID_INPUT — 세부 취미가 남아 있음
     */
    @Transactional
    public String deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        if (hobbyRepository.existsByCategoryId(categoryId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        categoryRepository.delete(category);
        return category.getName();
    }

    /** 편집할 세부 취미의 현재 값 (편집 패널을 미리 채운다). 호출: AdminHobbyController.subHobbies(?edit=) */
    @Transactional(readOnly = true)
    public HobbyFormDTO getHobbyForm(Long hobbyId) {
        Hobby hobby = hobbyRepository.findById(hobbyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        return new HobbyFormDTO(hobby.getCategoryId(), hobby.getName(), hobby.getDescription(), hobby.getState());
    }

    /** 편집하는 세부 취미 말고 그 대분류에 같은 이름이 없는지 (A-S03). 호출: AdminHobbyController.updateHobby */
    @Transactional(readOnly = true)
    public boolean isHobbyNameAvailable(Long categoryId, String name, Long exceptHobbyId) {
        return !hobbyRepository.existsByCategoryIdAndNameAndIdNot(categoryId, name, exceptHobbyId);
    }

    /**
     * 세부 취미 편집 (A-S03) — 대분류·이름·소개·노출 상태. 대분류를 옮기면 그 대분류의 노출 규칙을 따른다.
     * 호출: AdminHobbyController.updateHobby
     *
     * @throws BusinessException HOBBY_NOT_FOUND — 없는 세부 취미·없는 대분류
     */
    @Transactional
    public void updateHobby(Long hobbyId, HobbyFormDTO form) {
        Hobby hobby = hobbyRepository.findById(hobbyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOBBY_NOT_FOUND));
        if (!categoryExists(form.getCategoryId())) {
            throw new BusinessException(ErrorCode.HOBBY_NOT_FOUND);
        }
        hobby.update(form.getCategoryId(), form.getTrimmedName(), form.getTrimmedDescription(), form.getState());
    }
}
