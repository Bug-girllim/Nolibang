package com.wanted.lmspractice.global.file;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [역할] 삭제에 실패한 업로드 파일(고아 파일)을 주기적으로 다시 지운다 (#161)
 * [연결]
 *  - 호출함: FileStorage.retryPendingDeletes
 *  - 대기 목록에 넣는 곳: FileStorage.deleteQuietly (작품 이미지 교체 후 이전 파일, DB 롤백 후 새 파일, 취미 대표 사진 교체)
 * [근거] 파일 삭제는 DB 커밋 뒤에 일어나 실패해도 되돌릴 수 없다. 일시적인 실패(파일 잠김·원격 저장소 오류)는 다시 시도하면 대부분 풀린다.
 *        대기 목록은 업로드 폴더의 .pending-deletes 파일에 남아 서버를 재시작해도 이어서 지운다(FileStorage 생성 때 읽음).
 *        @EnableScheduling 은 EnrollmentExpiryScheduler 에 이미 있어(앱 전체에 적용) 여기에는 두지 않는다.
 * [기술] 🟡 @Scheduled (EnrollmentExpiryScheduler 와 같은 방식)
 */
@Component
@RequiredArgsConstructor
public class FileDeleteRetryScheduler {

    /** ❓ 임시값: 재처리 간격 10분 */
    static final long RETRY_INTERVAL_MS = 10 * 60 * 1000L;

    private final FileStorage fileStorage;

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS, initialDelay = RETRY_INTERVAL_MS)
    public void retry() {
        fileStorage.retryPendingDeletes();
    }
}
