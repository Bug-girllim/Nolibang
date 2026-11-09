package com.wanted.lmspractice.domain.notification.service;

import com.wanted.lmspractice.domain.notification.entity.NotificationKind;
import org.springframework.stereotype.Service;

/**
 * 알림 생성·목록·읽음 처리
 * 담당: 팀원 4 · #17
 * notify()는 호출한 쪽 트랜잭션 안에서 실행되며, 입력 검증 실패 외에는 예외를 던지지 않는다(#01 합의).
 *
 * DB 구조(notifications 표)에 맞춘 저장 규칙 (11-06):
 * - 대상 칼럼: kind.getTargetType()이 가리키는 칼럼 하나에만 targetId를 넣는다
 *   (ENROLLMENT→enrollment_id, REVIEW_ROUND→review_round_id, WORK→work_id, COMMENT→comment_id,
 *    INQUIRY_THREAD→inquiry_thread_id, SECTION→section_id). 나머지는 null — DB CHECK가 검사한다.
 * - 중복 방지: dedupe_key는 UNIQUE(user_id, dedupe_key). 기본값은 "종류:대상id" (예: REVIEW_APPROVED:12).
 *   같은 대상에 여러 번 생길 수 있는 알림(예: 같은 문의 스레드의 새 메시지)은 dedupeKey를 직접 넘긴다.
 * - 이미 같은 dedupe_key가 있으면 저장하지 않고 조용히 넘어간다(existsBy… 먼저 확인).
 *   UNIQUE 위반 예외가 나면 호출한 쪽 트랜잭션(수료 승인 등)까지 롤백되므로 반드시 먼저 확인한다.
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class NotificationService {

    /**
     * 계약 C7: 알림 생성 (호출: 팀원 2 SeatService·SectionService, 팀원 3 ReviewService·AssignmentService, 팀원 4 Comment·Inquiry·Moderation)
     *
     * @param receiverId 받는 사람 users.id
     * @param kind       알림 종류 (대상 칼럼은 kind.getTargetType()으로 결정)
     * @param targetId   대상 id (수강·평가 회차·작품·댓글·문의 스레드·분반 중 kind에 맞는 것)
     * @param title      알림 제목 (최대 200자, 예: "평가 승인")
     * @param body       알림 본문 (예: "색연필화 기초 과제가 승인되었습니다.")
     */
    public void notify(Long receiverId, NotificationKind kind, Long targetId, String title, String body) {
        notify(receiverId, kind, targetId, title, body, kind.name() + ":" + targetId);
    }

    /** 계약 C7: 같은 대상에 여러 번 생기는 알림용 — dedupeKey 직접 지정 (최대 150자) */
    public void notify(Long receiverId, NotificationKind kind, Long targetId, String title, String body, String dedupeKey) {
        // TODO #17: 구현 (현재는 스텁)
    }

    /** 계약 C7: 안 읽은 알림 수 (헤더 표시) */
    public long countUnread(Long userId) {
        // TODO #17: 구현 (현재는 스텁)
        return 0L;
    }
}
