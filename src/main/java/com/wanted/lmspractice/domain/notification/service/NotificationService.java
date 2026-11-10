package com.wanted.lmspractice.domain.notification.service;

import com.wanted.lmspractice.domain.notification.dto.NotificationDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 알림 생성·목록·읽음 처리·대상 화면 이동 — 사이트 알림만(이메일 없음, D-57)
 * [담당] 팀원 4 · #17   [화면] U-S15·T-S09, 공통 헤더   [API] NT-01~NT-04   [기획] FR-035
 * [연결]
 *  - 호출받음: 팀원 2 SeatService·SectionService, 팀원 3 ReviewService·AssignmentService, 팀원 4 Comment·Inquiry·Moderation (notify),
 *             HeaderNotificationInterceptor(countUnread·getHeaderNotifications — 화면을 그리는 요청만), NotificationController(목록·열기·모두 읽음)
 *  - 호출함:   NotificationRepository, NotificationLinkResolver(대상 화면 URL·존재 확인)
 *  - 계약:     C7 notify 두 가지·countUnread — 시그니처 변경 금지
 * notify()는 호출한 쪽 트랜잭션 안에서 실행되며(@Transactional 기본 전파 = 참여), 입력 검증 실패 외에는 예외를 던지지 않는다(#01 합의).
 *
 * 저장 규칙: 대상 칼럼은 kind.getTargetType()이 가리키는 하나에만 넣고(Notification.create), dedupe_key 기본값은 "종류:대상id".
 * UNIQUE(user_id, dedupe_key) 위반 예외가 나면 호출한 쪽(수료 승인 등)까지 롤백되므로 저장 전에 existsBy…로 먼저 확인한다.
 * [기술] 🟢 JPA·@Transactional
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** ❓ 임시값: 한 페이지 알림 수 — API §1-9 페이지 크기 미정(#01 확정 필요) */
    public static final int PAGE_SIZE = 10;
    private static final int MAX_TITLE_LENGTH = 200;    // notifications.title VARCHAR(200)
    private static final int MAX_DEDUPE_LENGTH = 150;   // notifications.dedupe_key VARCHAR(150)

    private final NotificationRepository notificationRepository;
    private final NotificationLinkResolver linkResolver;

    /**
     * 계약 C7: 알림 생성 (호출: 팀원 2 SeatService·SectionService, 팀원 3 ReviewService·AssignmentService, 팀원 4 Comment·Inquiry·Moderation)
     *
     * @param receiverId 받는 사람 users.id
     * @param kind       알림 종류 (대상 칼럼은 kind.getTargetType()으로 결정)
     * @param targetId   대상 id (수강·평가 회차·작품·댓글·문의 스레드·분반 중 kind에 맞는 것)
     * @param title      알림 제목 (최대 200자, 넘으면 잘라서 저장)
     * @param body       알림 본문
     * @throws BusinessException INVALID_INPUT — 필수 값이 비었을 때만
     */
    @Transactional
    public void notify(Long receiverId, NotificationKind kind, Long targetId, String title, String body) {
        notify(receiverId, kind, targetId, title, body, kind == null ? null : kind.name() + ":" + targetId);
    }

    /** 계약 C7: 같은 대상에 여러 번 생기는 알림용 — dedupeKey 직접 지정 (최대 150자). 이미 같은 키가 있으면 저장하지 않는다. */
    @Transactional
    public void notify(Long receiverId, NotificationKind kind, Long targetId, String title, String body, String dedupeKey) {
        if (receiverId == null || kind == null || targetId == null || isBlank(title) || isBlank(body)
                || isBlank(dedupeKey) || dedupeKey.length() > MAX_DEDUPE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (notificationRepository.existsByUserIdAndDedupeKey(receiverId, dedupeKey)) {
            return;   // 같은 사건의 알림은 한 번만 (UNIQUE 위반 예외로 호출자 롤백 방지)
        }
        String safeTitle = title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
        notificationRepository.save(Notification.create(receiverId, kind, targetId, safeTitle, body, dedupeKey));
    }

    /** 계약 C7: 안 읽은 알림 수 (헤더 표시) */
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 헤더 알림창 미리보기 — 최근 5건. 호출: HeaderNotificationInterceptor.postHandle */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getHeaderNotifications(Long userId) {
        return notificationRepository.findTop5ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationDTO::from)
                .toList();
    }

    /**
     * 알림함 한 페이지 (탭 필터, 최신순).
     * 호출: NotificationController.list (GET /notifications, API NT-01)
     * @param page 0부터. 음수는 0으로, 마지막 쪽을 넘으면 마지막 쪽으로 맞춘다
     */
    @Transactional(readOnly = true)
    public NotificationPageDTO getPage(Long userId, NotificationTab tab, int page) {
        long total = notificationRepository.countByUserIdAndKindIn(userId, tab.getKinds());
        int totalPages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, Math.max(totalPages - 1, 0)));
        List<NotificationDTO> items = notificationRepository
                .findPage(userId, tab.getKinds().stream().map(Enum::name).toList(), PAGE_SIZE, safePage * PAGE_SIZE)
                .stream().map(NotificationDTO::from).toList();
        return new NotificationPageDTO(items, safePage, totalPages);
    }

    /**
     * 알림 열기 — 본인 알림인지 확인 → 읽음 처리 → 대상 화면 URL.
     * 호출: NotificationController.open (GET /notifications/{id}, API NT-02)
     * @return 이동할 URL. 대상이 삭제돼 열 수 없으면 empty(읽음 처리는 그대로 남는다)
     * @throws BusinessException NOT_FOUND — 없는 알림이거나 남의 알림(존재를 숨김)
     */
    @Transactional
    public Optional<String> open(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        notification.markRead(LocalDateTime.now());
        return linkResolver.resolve(notification);
    }

    /**
     * 모두 읽음 — 내 안 읽은 알림을 전부 읽음 처리한다 (API NT-03).
     * @return 읽음 처리한 건수
     */
    @Transactional
    public int readAll(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> unread = notificationRepository.findByUserIdAndReadAtIsNull(userId);
        unread.forEach(n -> n.markRead(now));
        return unread.size();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
