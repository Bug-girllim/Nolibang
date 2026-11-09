package com.wanted.lmspractice.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.lmspractice.domain.notification.dto.NotificationPageDTO;
import com.wanted.lmspractice.domain.notification.dto.NotificationTab;
import com.wanted.lmspractice.domain.notification.entity.Notification;
import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import com.wanted.lmspractice.domain.notification.repository.NotificationRepository;
import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [역할] NotificationService 테스트 — 생성(대상 칼럼·중복 방지·입력 검증), 읽음·모두 읽음, 타인 알림 접근 차단, 대상 삭제, 페이지·탭
 * [담당] 팀원 4 · #17
 * [전제] Repository·링크 해석기는 Mockito 가짜로 대신한다(DB 없이 규칙만 검사).
 */
class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationLinkResolver linkResolver;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        linkResolver = mock(NotificationLinkResolver.class);
        service = new NotificationService(repository, linkResolver);
    }

    private Notification existing(long id, NotificationKind kind, long targetId) {
        Notification n = Notification.create(2L, kind, targetId, "제목", "본문", kind.name() + ":" + targetId);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    @DisplayName("알림을 저장하고 대상 칼럼은 종류가 가리키는 하나에만 들어간다")
    void notifyStoresTargetColumn() {
        service.notify(2L, NotificationKind.REVIEW_APPROVED, 12L, "평가 승인", "승인되었습니다.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getReviewRoundId()).isEqualTo(12L);
        assertThat(saved.getEnrollmentId()).isNull();
        assertThat(saved.getDedupeKey()).isEqualTo("REVIEW_APPROVED:12");
        assertThat(saved.getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 dedupe_key가 이미 있으면 저장하지 않고 예외도 던지지 않는다")
    void duplicateSkippedSilently() {
        when(repository.existsByUserIdAndDedupeKey(2L, "COURSE_COMPLETED:1")).thenReturn(true);

        service.notify(2L, NotificationKind.COURSE_COMPLETED, 1L, "수료", "수료했어요");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("dedupeKey를 직접 넘기면 그 키로 중복을 확인한다")
    void customDedupeKey() {
        service.notify(2L, NotificationKind.INQUIRY_ANSWERED, 5L, "문의 답변", "답변했어요", "INQUIRY_ANSWERED:5:msg9");

        verify(repository).existsByUserIdAndDedupeKey(2L, "INQUIRY_ANSWERED:5:msg9");
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getInquiryThreadId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("필수 값이 비어 있을 때만 INVALID_INPUT 예외")
    void invalidInputOnly() {
        assertThatThrownBy(() -> service.notify(null, NotificationKind.COURSE_COMPLETED, 1L, "수료", "본문"))
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> service.notify(2L, NotificationKind.COURSE_COMPLETED, null, "수료", "본문"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.notify(2L, NotificationKind.COURSE_COMPLETED, 1L, " ", "본문"))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("제목이 200자를 넘으면 예외 없이 잘라서 저장한다")
    void longTitleTruncated() {
        service.notify(2L, NotificationKind.COURSE_COMPLETED, 1L, "가".repeat(250), "본문");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(200);
    }

    @Test
    @DisplayName("알림 열기: 읽음 처리하고 대상 화면 URL을 돌려준다")
    void openMarksReadAndReturnsUrl() {
        Notification n = existing(7L, NotificationKind.REVIEW_REJECTED, 2L);
        when(repository.findByIdAndUserId(7L, 2L)).thenReturn(Optional.of(n));
        when(linkResolver.resolve(n)).thenReturn(Optional.of("/assignments/2/result"));

        Optional<String> url = service.open(2L, 7L);

        assertThat(url).contains("/assignments/2/result");
        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("남의 알림 id를 넣으면 NOT_FOUND로 존재를 숨기고 읽음 처리도 하지 않는다")
    void othersNotificationHidden() {
        when(repository.findByIdAndUserId(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("대상이 삭제된 알림은 URL 없이(empty) 돌려주되 읽음 처리는 남는다")
    void deletedTarget() {
        Notification n = existing(8L, NotificationKind.COMMENT_ON_MY_WORK, 3L);
        when(repository.findByIdAndUserId(8L, 2L)).thenReturn(Optional.of(n));
        when(linkResolver.resolve(n)).thenReturn(Optional.empty());

        assertThat(service.open(2L, 8L)).isEmpty();
        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 열어도 처음 읽은 시각은 바뀌지 않는다")
    void readAtKeptOnSecondOpen() {
        Notification n = existing(7L, NotificationKind.COURSE_COMPLETED, 1L);
        LocalDateTime first = LocalDateTime.of(2026, 11, 1, 10, 0);
        n.markRead(first);

        n.markRead(first.plusDays(3));

        assertThat(n.getReadAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("모두 읽음: 내 안 읽은 알림이 전부 읽음이 되고 건수를 돌려준다")
    void readAll() {
        Notification a = existing(1L, NotificationKind.WAITLIST_CONFIRMED, 1L);
        Notification b = existing(2L, NotificationKind.COURSE_COMPLETED, 1L);
        when(repository.findByUserIdAndReadAtIsNull(2L)).thenReturn(List.of(a, b));

        assertThat(service.readAll(2L)).isEqualTo(2);
        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
    }

    @Test
    @DisplayName("안 읽은 알림 수는 Repository 집계를 그대로 돌려준다")
    void countUnread() {
        when(repository.countByUserIdAndReadAtIsNull(2L)).thenReturn(3L);
        assertThat(service.countUnread(2L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("페이지: 범위를 벗어난 쪽 번호는 마지막 쪽으로 맞추고, 탭의 종류로만 조회한다")
    void pageClampedAndTabKinds() {
        when(repository.countByUserIdAndKindIn(anyLong(), anyCollection())).thenReturn(25L);
        when(repository.findPage(anyLong(), anyCollection(), anyInt(), anyInt())).thenReturn(List.of());

        NotificationPageDTO page = service.getPage(2L, NotificationTab.RESULT, 99);

        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.isHasNext()).isFalse();
        verify(repository).findPage(2L, List.of("REVIEW_APPROVED", "REVIEW_REJECTED", "COURSE_COMPLETED"),
                NotificationService.PAGE_SIZE, 2 * NotificationService.PAGE_SIZE);
    }

    @Test
    @DisplayName("알림이 없으면 0쪽·0페이지")
    void emptyPage() {
        when(repository.countByUserIdAndKindIn(anyLong(), anyCollection())).thenReturn(0L);
        when(repository.findPage(anyLong(), anyCollection(), anyInt(), anyInt())).thenReturn(List.of());

        NotificationPageDTO page = service.getPage(2L, NotificationTab.ALL, -3);

        assertThat(page.getPage()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.getItems()).isEmpty();
    }
}
