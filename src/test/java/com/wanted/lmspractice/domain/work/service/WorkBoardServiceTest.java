package com.wanted.lmspractice.domain.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.repository.UserRepository;
import com.wanted.lmspractice.domain.work.dto.CommentDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDTO;
import com.wanted.lmspractice.domain.work.dto.WorkDetailDTO;
import com.wanted.lmspractice.domain.work.dto.WorkPageDTO;
import com.wanted.lmspractice.domain.work.dto.WorkSearchDTO;
import com.wanted.lmspractice.domain.work.entity.Visibility;
import com.wanted.lmspractice.domain.work.entity.Work;
import com.wanted.lmspractice.domain.work.entity.WorkKind;
import com.wanted.lmspractice.domain.work.entity.WorkVersion;
import com.wanted.lmspractice.domain.work.repository.WorkRepository;
import com.wanted.lmspractice.domain.work.repository.WorkVersionRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] WorkBoardService 통합 테스트 — 공개·미숨김 작품만 목록에 나오는지, 필터·검색·정렬·페이징, 강사 범위, 상세의 댓글 트리·권한
 * [담당] 팀원 4 · #18   [전제] 로컬 MySQL(nolibang)에 schema.sql·seed.sql 적재. 변경은 테스트 트랜잭션이 롤백한다.
 *       시드: 작품 1(최종 과제·색연필화·입문·좋아요 2·공개)만 게시판에 노출된다.
 *             작품 5(일반 작품·디저트 만들기)는 공개이지만 관리자 숨김, 작품 2·3·4·6은 비공개.
 *             작품 1 댓글 1(teacherA) ← 답글 2(작성자 learner).
 * 🟡 미학습 기술: @SpringBootTest (작업분배 §5 허용), ReflectionTestUtils (공개 전환 메서드가 #19 소관이라 필드를 직접 세팅)
 */
@SpringBootTest
@Transactional
class WorkBoardServiceTest {

    @Autowired
    private WorkBoardService workBoardService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkRepository workRepository;
    @Autowired
    private WorkVersionRepository workVersionRepository;

    private Long userId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    private List<Long> ids(WorkPageDTO page) {
        return page.getItems().stream().map(WorkDTO::getWorkId).toList();
    }

    private WorkPageDTO search(WorkSearchDTO cond, String loginId, Role role) {
        return workBoardService.search(cond, userId(loginId), role);
    }

    private ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    // 정렬·여러 건 시나리오용으로 공개 작품 하나를 더 만든다 (일반 작품·디저트·입문, 지금 공개, 좋아요 0). 시드 id 와 겹치지 않는다
    private Long createPublicGeneralWork() {
        Work work = Work.createFinal(userId("learner"), 2L, 3L, CourseLevel.BASIC);   // 수강 2 는 시드에서 작품이 없다
        ReflectionTestUtils.setField(work, "kind", WorkKind.GENERAL);
        ReflectionTestUtils.setField(work, "enrollmentId", null);
        ReflectionTestUtils.setField(work, "visibility", Visibility.PUBLIC);
        ReflectionTestUtils.setField(work, "publishedAt", LocalDateTime.now());
        workRepository.save(work);
        WorkVersion version = workVersionRepository.save(WorkVersion.create(work.getId(), 1, "나의 새 케이크", "설명"));
        work.display(version.getId());
        workRepository.flush();
        return work.getId();
    }

    @Test
    @DisplayName("목록에는 공개 + 숨김 아님 작품만 — 비공개(2·3·4·6)와 관리자 숨김(5)은 나오지 않고 카드 값이 채워진다")
    void onlyPublicVisibleWorks() {
        WorkPageDTO page = search(new WorkSearchDTO(), "learner2", Role.USER);

        assertThat(ids(page)).containsExactly(1L);
        assertThat(page.getTotalCount()).isEqualTo(1);
        assertThat(page.isHasNext()).isFalse();
        WorkDTO card = page.getItems().get(0);
        assertThat(card.getTitle()).isEqualTo("색연필화 기초 — 최종 과제");
        assertThat(card.getAuthorNickname()).isNotBlank();
        assertThat(card.getHobbyName()).isEqualTo("색연필화");
        assertThat(card.getLevel()).isEqualTo(CourseLevel.BASIC);
        assertThat(card.getKind()).isEqualTo(WorkKind.FINAL);
        assertThat(card.getLikeCount()).isEqualTo(2);
        assertThat(card.getCommentCount()).isEqualTo(2);
        assertThat(card.getThumbnailFileId()).isNotNull();
        assertThat(card.isLiked()).isTrue();   // learner2 는 시드에서 작품 1에 좋아요를 눌렀다
    }

    @Test
    @DisplayName("필터: 유형·세부 취미·수준·키워드가 조건에 맞는 작품만 남긴다")
    void filters() {
        Long extra = createPublicGeneralWork();

        WorkSearchDTO finalOnly = new WorkSearchDTO();
        finalOnly.setKind(WorkKind.FINAL);
        assertThat(ids(search(finalOnly, "learner2", Role.USER))).containsExactly(1L);

        WorkSearchDTO generalOnly = new WorkSearchDTO();
        generalOnly.setKind(WorkKind.GENERAL);
        assertThat(ids(search(generalOnly, "learner2", Role.USER))).containsExactly(extra);

        WorkSearchDTO dessert = new WorkSearchDTO();
        dessert.setHobbyId(3L);
        assertThat(ids(search(dessert, "learner2", Role.USER))).containsExactly(extra);

        WorkSearchDTO advanced = new WorkSearchDTO();
        advanced.setLevel(CourseLevel.ADVANCED);
        assertThat(search(advanced, "learner2", Role.USER).getItems()).isEmpty();

        WorkSearchDTO keyword = new WorkSearchDTO();
        keyword.setKeyword("케이크");
        assertThat(ids(search(keyword, "learner2", Role.USER))).containsExactly(extra);
    }

    @Test
    @DisplayName("정렬: 기본은 최신 공개순, sort=likes 는 좋아요 많은 순")
    void sorting() {
        Long extra = createPublicGeneralWork();   // 방금 공개, 좋아요 0

        assertThat(ids(search(new WorkSearchDTO(), "learner2", Role.USER))).containsExactly(extra, 1L);

        WorkSearchDTO byLikes = new WorkSearchDTO();
        byLikes.setSort(WorkSearchDTO.SORT_LIKES);
        assertThat(ids(search(byLikes, "learner2", Role.USER))).containsExactly(1L, extra);   // 좋아요 2 > 0
    }

    @Test
    @DisplayName("검색어의 %·_ 는 문자 그대로 검색한다 (전체 일치로 번지지 않는다)")
    void keywordWildcardsAreLiteral() {
        WorkSearchDTO percent = new WorkSearchDTO();
        percent.setKeyword("%");
        assertThat(search(percent, "learner2", Role.USER).getItems()).isEmpty();

        WorkSearchDTO underscore = new WorkSearchDTO();
        underscore.setKeyword("_");
        assertThat(search(underscore, "learner2", Role.USER).getItems()).isEmpty();
    }

    @Test
    @DisplayName("페이지: 범위를 넘어선 쪽은 빈 목록이고 전체 개수는 그대로, 음수 쪽은 첫 쪽으로 본다")
    void paging() {
        WorkSearchDTO beyond = new WorkSearchDTO();
        beyond.setPage(5);
        WorkPageDTO empty = search(beyond, "learner2", Role.USER);
        assertThat(empty.getItems()).isEmpty();
        assertThat(empty.getTotalCount()).isEqualTo(1);

        WorkSearchDTO negative = new WorkSearchDTO();
        negative.setPage(-3);
        assertThat(ids(search(negative, "learner2", Role.USER))).containsExactly(1L);
    }

    @Test
    @DisplayName("강사: 전체 공개 범위·담당 취미 범위·댓글 가능 표시, 내가 댓글 남긴 작품만 필터")
    void instructorScope() {
        Long extra = createPublicGeneralWork();   // 디저트 취미 작품 — teacherA 의 담당 취미가 아니다(시드: 색연필화·디지털 드로잉·목공)

        WorkSearchDTO all = new WorkSearchDTO();
        all.setScope(WorkSearchDTO.SCOPE_ALL);
        WorkPageDTO allPage = search(all, "teacherA", Role.INSTRUCTOR);
        assertThat(ids(allPage)).containsExactlyInAnyOrder(1L, extra);
        assertThat(allPage.getItems().stream().filter(w -> w.getWorkId() == 1L).findFirst().orElseThrow().isCommentable()).isTrue();
        assertThat(allPage.getItems().stream().filter(w -> w.getWorkId().equals(extra)).findFirst().orElseThrow().isCommentable()).isFalse();

        WorkSearchDTO mine = new WorkSearchDTO();   // 기본 scope = mine → 담당 취미 작품만
        assertThat(ids(search(mine, "teacherA", Role.INSTRUCTOR))).containsExactly(1L);

        WorkSearchDTO commented = new WorkSearchDTO();
        commented.setScope(WorkSearchDTO.SCOPE_ALL);
        commented.setMyCommented(true);
        WorkPageDTO commentedPage = search(commented, "teacherA", Role.INSTRUCTOR);
        assertThat(ids(commentedPage)).containsExactly(1L);
        assertThat(commentedPage.getItems().get(0).getMyCommentCount()).isEqualTo(1);
        assertThat(search(commented, "teacherB", Role.INSTRUCTOR).getItems()).isEmpty();   // teacherB 는 댓글을 남긴 적이 없다
    }

    @Test
    @DisplayName("상세: 댓글 트리(댓글 1 ← 답글 1), 작성자 표시, 좋아요 수·내가 눌렀는지, 배지 없는 사용자는 작성 불가")
    void detailForViewerWithoutBadge() {
        WorkDetailDTO detail = workBoardService.getDetail(1L, userId("learner2"), Role.USER);

        assertThat(detail.getWork().getTitle()).isEqualTo("색연필화 기초 — 최종 과제");
        assertThat(detail.getWork().getImageIds()).hasSize(2);
        assertThat(detail.getWork().getLikeCount()).isEqualTo(2);
        assertThat(detail.getWork().isLiked()).isTrue();
        assertThat(detail.getComments()).hasSize(1);
        CommentDTO root = detail.getComments().get(0);
        assertThat(root.getReplies()).hasSize(1);
        assertThat(root.getReplies().get(0).isWorkAuthor()).isTrue();
        assertThat(root.isWorkAuthor()).isFalse();
        assertThat(root.isMine()).isFalse();
        assertThat(detail.isCanComment()).isFalse();
        assertThat(detail.isCanReply()).isFalse();
        assertThat(detail.isOwner()).isFalse();
    }

    @Test
    @DisplayName("상세: 작성자는 답글 가능·내 글 표시, 강사는 취미 권한으로 댓글 가능")
    void detailForAuthorAndInstructor() {
        WorkDetailDTO asAuthor = workBoardService.getDetail(1L, userId("learner"), Role.USER);
        assertThat(asAuthor.isOwner()).isTrue();
        assertThat(asAuthor.isCanReply()).isTrue();
        assertThat(asAuthor.getComments().get(0).getReplies().get(0).isMine()).isTrue();

        WorkDetailDTO asTeacher = workBoardService.getDetail(1L, userId("teacherA"), Role.INSTRUCTOR);
        assertThat(asTeacher.isCanComment()).isTrue();
        assertThat(asTeacher.getComments().get(0).isMine()).isTrue();
    }

    @Test
    @DisplayName("비공개·숨김 작품 상세를 다른 학습자가 직접 URL로 열면 WORK_ACCESS_DENIED, 작성자는 열람 가능, 없는 작품은 WORK_NOT_FOUND")
    void privateAndHiddenDetail() {
        assertThat(codeOf(catchThrowable(() -> workBoardService.getDetail(2L, userId("learner2"), Role.USER))))
                .isEqualTo(ErrorCode.WORK_ACCESS_DENIED);                                  // 비공개(작품 2)
        assertThat(codeOf(catchThrowable(() -> workBoardService.getDetail(5L, userId("learner2"), Role.USER))))
                .isEqualTo(ErrorCode.WORK_ACCESS_DENIED);                                  // 관리자 숨김(작품 5)
        assertThat(workBoardService.getDetail(2L, userId("learner"), Role.USER).isOwner()).isTrue();
        assertThat(workBoardService.getDetail(5L, userId("learner"), Role.USER).isOwner()).isTrue();   // 숨겨도 작성자는 열람
        assertThatThrownBy(() -> workBoardService.getDetail(999999L, userId("learner"), Role.USER))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WORK_NOT_FOUND));
    }

    private Throwable catchThrowable(Runnable runnable) {
        return org.assertj.core.api.Assertions.catchThrowable(runnable::run);
    }
}
