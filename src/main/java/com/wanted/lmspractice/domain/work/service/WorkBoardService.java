package com.wanted.lmspractice.domain.work.service;

import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.user.service.UserService;
import com.wanted.lmspractice.domain.work.dto.CommentDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDetailDTO;
import com.wanted.lmspractice.domain.work.dto.WorkPageDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkComment;
import com.wanted.lmspractice.domain.work.policy.CommentPolicy;
import com.wanted.lmspractice.domain.work.repository.WorkBoardQuery;
import com.wanted.lmspractice.domain.work.repository.WorkCommentRepository;
import com.wanted.lmspractice.domain.work.repository.WorkLikeRepository;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 공개 작품 목록(검색·필터·정렬·페이징)과 작품 상세(작품 + 댓글 트리 + 작성 가능 여부) 조립
 * [담당] 팀원 4 · #18   [화면] U-S09, U-S10, T-S10   [API] WB-01·WB-02   [기획] FR-033·034, D-09
 * [연결]
 *  - 호출받음: WorkBoardController
 *  - 호출함:   WorkBoardQuery(목록), WorkService.getWork(상세 기본 값 + 열람 권한), CommentPolicy(작성 가능 여부),
 *              UserService.getPublicNames(닉네임 — 계약 추가 요청), HobbyService(취미 이름·분류 필터),
 *              InstructorAccountService.getActiveGrantHobbyIds(강사 담당 취미 — C3와 같은 기준의 일괄 조회)
 * [규칙] 목록에는 공개 + 관리자 숨김 아님 + 삭제 아님 작품만 나온다(DB 뷰). 숨김·비공개 작품의 상세는 작성자·담당 강사·관리자만(WorkAccessPolicy).
 *        실명·로그인 아이디는 어디에도 담지 않는다.
 * [기술] 🟢 @Transactional(readOnly), 🟢 EntityManager native 쿼리
 */
@Service
@RequiredArgsConstructor
public class WorkBoardService {

    /** ❓ 임시값: 페이지 크기 12 (API §1-9 — #18에서 확정 필요, 3열 카드 4줄) */
    public static final int PAGE_SIZE = 12;

    private final WorkBoardQuery workBoardQuery;
    private final WorkRepository workRepository;
    private final WorkCommentRepository workCommentRepository;
    private final WorkLikeRepository workLikeRepository;
    private final WorkService workService;
    private final CommentPolicy commentPolicy;
    private final UserService userService;
    private final HobbyService hobbyService;
    private final InstructorAccountService instructorAccountService;

    /**
     * 게시판 목록 한 페이지 (WB-01) — 조건에 맞는 공개 작품 카드
     * 강사(scope=mine, 기본)는 자신이 취미 권한을 가진 취미의 작품만, scope=all 이면 전체 공개 작품.
     * 강사에게는 카드마다 "댓글 가능(담당 취미)" 여부가 붙는다.
     */
    @Transactional(readOnly = true)
    public WorkPageDTO search(WorkSearchDTO cond, Long viewerId, Role viewerRole) {
        int page = Math.max(cond.getPage(), 0);
        // [성능 개선] 강사는 보이는 취미 목록을 한 번만 만들어 담당 취미 계산·분류 필터에 함께 쓴다(예전: 같은 목록을 2번 조회)
        List<CategoryDTO> instructorCategories = viewerRole == Role.INSTRUCTOR ? hobbyService.getCategories(Role.INSTRUCTOR) : null;
        Set<Long> grantedHobbyIds = instructorCategories != null
                ? grantedHobbyIds(viewerId, hobbiesOf(instructorCategories)) : Set.of();
        List<Long> hobbyIds = hobbyFilter(cond, viewerRole, grantedHobbyIds, instructorCategories);

        long total = workBoardQuery.count(cond, hobbyIds, viewerId);
        List<WorkDTO> rows = workBoardQuery.find(cond, hobbyIds, viewerId, PAGE_SIZE + 1, page * PAGE_SIZE);  // +1: 다음 쪽 유무 확인
        return toPage(rows, total, page, grantedHobbyIds);
    }

    /**
     * 관리자 목록 한 페이지 (AW-01, #20) — 공개 작품 전체(숨김 포함), cond.hidden 으로 숨김만/공개 중만. 목록 서비스를 재사용한다.
     * 호출: AdminWorkController.list (A-S08·A-S14)
     */
    @Transactional(readOnly = true)
    public WorkPageDTO searchForAdmin(WorkSearchDTO cond, Long adminId) {
        int page = Math.max(cond.getPage(), 0);
        List<Long> hobbyIds = hobbyFilter(cond, Role.ADMIN, Set.of(), null);
        long total = workBoardQuery.countForAdmin(cond, hobbyIds, adminId);
        List<WorkDTO> rows = workBoardQuery.findForAdmin(cond, hobbyIds, adminId, PAGE_SIZE + 1, page * PAGE_SIZE);
        return toPage(rows, total, page, Set.of());
    }

    // 한 건 더 읽은 결과를 한 페이지로 자르고 취미 이름·강사 댓글 가능 표시를 채운다
    private WorkPageDTO toPage(List<WorkDTO> rows, long total, int page, Set<Long> grantedHobbyIds) {
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<WorkDTO> items = new ArrayList<>(hasNext ? rows.subList(0, PAGE_SIZE) : rows);

        Map<Long, HobbyDTO> hobbies = hobbyService.getHobbiesIgnoringVisibility(
                items.stream().map(WorkDTO::getHobbyId).collect(Collectors.toSet()));
        items.forEach(item -> {
            HobbyDTO hobby = hobbies.get(item.getHobbyId());
            item.setHobbyName(hobby == null ? "" : hobby.getName());
            item.setCommentable(grantedHobbyIds.contains(item.getHobbyId()));
        });
        return new WorkPageDTO(items, total, page, hasNext);
    }

    /** 필터 셀렉트에 보여 줄 세부 취미 목록 (보는 사람 기준 노출 규칙 적용) */
    @Transactional(readOnly = true)
    public List<HobbyDTO> getHobbyOptions(Role viewerRole) {
        return hobbiesOf(hobbyService.getCategories(viewerRole));
    }

    /** 강사 게시판 머리의 "담당 세부 취미" 이름들 */
    @Transactional(readOnly = true)
    public List<String> getGrantedHobbyNames(Long instructorId) {
        // [성능 개선] 보이는 취미 목록을 한 번만 만들어 담당 취미 계산과 이름 뽑기에 함께 쓴다(예전: 같은 목록을 2번 조회)
        List<HobbyDTO> options = getHobbyOptions(Role.INSTRUCTOR);
        Set<Long> granted = grantedHobbyIds(instructorId, options);
        return options.stream().filter(h -> granted.contains(h.getId())).map(HobbyDTO::getName).toList();
    }

    /** 강사 머리의 "강사 A" 이름 표시용 공개 이름 */
    @Transactional(readOnly = true)
    public String getPublicName(Long userId) {
        return userService.getPublicNames(List.of(userId)).getOrDefault(userId, "");
    }

    /**
     * 작품 상세 (WB-02) — 열람 권한 확인 후 작품·댓글 트리·작성 가능 여부를 조립한다
     * @throws BusinessException WORK_NOT_FOUND, WORK_ACCESS_DENIED(비공개·숨김 작품을 권한 없이 열람)
     */
    @Transactional(readOnly = true)
    public WorkDetailDTO getDetail(Long workId, Long viewerId, Role viewerRole) {
        WorkDTO dto = workService.getWork(workId, viewerId, viewerRole);   // 열람 권한(WorkAccessPolicy) 검사 포함
        Work work = workRepository.findById(workId).orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));

        List<WorkComment> comments = workCommentRepository.findByWorkIdOrderById(workId);
        Set<Long> userIds = new HashSet<>(Set.of(work.getAuthorId()));
        comments.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, String> names = userService.getPublicNames(userIds);

        dto.setAuthorNickname(names.get(work.getAuthorId()));
        HobbyDTO hobby = hobbyService.getHobbiesIgnoringVisibility(List.of(work.getHobbyId())).get(work.getHobbyId());
        dto.setHobbyName(hobby == null ? "" : hobby.getName());
        dto.setLikeCount(workLikeRepository.countByWorkId(workId));
        dto.setLiked(workLikeRepository.existsByWorkIdAndUserId(workId, viewerId));
        dto.setCommentCount(comments.stream().filter(c -> !c.isDeleted()).count());

        // [성능 개선] 댓글 자격(배지 계산 또는 강사 권한 조회)을 한 번만 구하고 답글 자격에 다시 쓴다(예전: 같은 계산 2번)
        boolean canComment = commentPolicy.canWriteComment(work, viewerId, viewerRole);
        return new WorkDetailDTO(dto, buildCommentTree(comments, work, names, viewerId),
                canComment,
                commentPolicy.canWriteReply(work, viewerId, viewerRole, canComment),
                work.isAuthor(viewerId));
    }

    // 댓글 아래에 답글을 묶는다(1단계). 삭제된 댓글은 문구만 남고 답글은 유지된다
    private List<CommentDTO> buildCommentTree(List<WorkComment> comments, Work work, Map<Long, String> names, Long viewerId) {
        Map<Long, CommentDTO> roots = new LinkedHashMap<>();
        for (WorkComment c : comments) {
            if (!c.isReply()) {
                roots.put(c.getId(), toDto(c, work, names, viewerId));
            }
        }
        for (WorkComment c : comments) {
            CommentDTO parent = c.isReply() ? roots.get(c.getParentId()) : null;
            if (parent != null) {
                parent.getReplies().add(toDto(c, work, names, viewerId));
            }
        }
        return new ArrayList<>(roots.values());
    }

    private CommentDTO toDto(WorkComment c, Work work, Map<Long, String> names, Long viewerId) {
        return CommentDTO.of(c, names.get(c.getAuthorId()), work.isAuthor(c.getAuthorId()), c.isAuthor(viewerId));
    }

    // 분류·세부 취미·강사 담당 범위를 합쳐 최종 취미 id 목록으로 만든다. null = 제한 없음
    // viewerCategories: 같은 역할로 이미 만든 대분류 목록(없으면 null → 분류 필터가 있을 때만 조회)
    private List<Long> hobbyFilter(WorkSearchDTO cond, Role viewerRole, Set<Long> grantedHobbyIds,
                                   List<CategoryDTO> viewerCategories) {
        Set<Long> allowed = null;
        if (cond.getCategoryId() != null) {
            List<CategoryDTO> categories = viewerCategories != null ? viewerCategories : hobbyService.getCategories(viewerRole);
            allowed = categories.stream()
                    .filter(c -> c.getId().equals(cond.getCategoryId()))
                    .flatMap(c -> c.getHobbies().stream()).map(HobbyDTO::getId).collect(Collectors.toSet());
        }
        if (cond.getHobbyId() != null) {
            allowed = intersect(allowed, Set.of(cond.getHobbyId()));
        }
        if (viewerRole == Role.INSTRUCTOR && !WorkSearchDTO.SCOPE_ALL.equals(cond.getScope())) {
            allowed = intersect(allowed, grantedHobbyIds);   // 내 담당 취미 작품
        }
        return allowed == null ? null : List.copyOf(allowed);
    }

    private Set<Long> intersect(Set<Long> current, Set<Long> other) {
        if (current == null) {
            return new HashSet<>(other);
        }
        Set<Long> result = new HashSet<>(current);
        result.retainAll(other);
        return result;
    }

    // 강사가 취미 권한을 가진 세부 취미 id — 강사에게 보이는 세부 취미 중 활동 권한이 있는 것
    // [성능 개선] 예전에는 취미마다 C3 hasHobbyGrant를 불러 취미 수만큼 조회했다(N+1) → 권한 목록 1번 조회 후 교집합(판단 기준 같음)
    private Set<Long> grantedHobbyIds(Long instructorId, List<HobbyDTO> visibleHobbies) {
        Set<Long> active = instructorAccountService.getActiveGrantHobbyIds(instructorId);
        return visibleHobbies.stream().map(HobbyDTO::getId).filter(active::contains).collect(Collectors.toSet());
    }

    // 대분류 목록 → 세부 취미 목록(메뉴 순서)
    private List<HobbyDTO> hobbiesOf(List<CategoryDTO> categories) {
        return categories.stream().flatMap(c -> c.getHobbies().stream()).toList();
    }
}
