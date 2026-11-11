package com.wanted.lmspractice.domain.hobby.service;

import com.wanted.lmspractice.domain.hobby.entity.Category;
import com.wanted.lmspractice.domain.hobby.entity.Hobby;
import com.wanted.lmspractice.domain.hobby.repository.CategoryRepository;
import com.wanted.lmspractice.domain.hobby.repository.HobbyRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * [역할] 대분류·세부 취미 전체 목록을 메모리에 잠깐(1분) 들고 있는 캐시 — 성능 개선(공용 원격 DB 왕복 줄이기)
 * [담당] 성능 개선   [화면] C-01 메인, 사이드 메뉴, U-S01·U-S02·U-S03 등 취미 목록이 나오는 모든 화면
 * [연결]
 *  - 호출받음: HobbyService(조회 메서드 → get, 관리자 추가·편집·삭제 메서드 → invalidate)
 *  - 호출함:   CategoryRepository·HobbyRepository (캐시가 비었거나 1분이 지났을 때만)
 * [왜]
 *  - 대분류·세부 취미는 관리자만 가끔 바꾸는데, 메인·사이드 메뉴·강의 목록 등 거의 모든 화면이 매번 두 표를 다시 읽었다.
 *    공용 DB는 왕복 1번에 100ms 이상이라 이 조회만으로 화면마다 수백 ms가 들었다.
 * [규칙]
 *  - 읽기 전용 트랜잭션(일반 화면 조회)에서만 캐시를 쓴다. 쓰기 트랜잭션 안(수강 신청처럼 상태를 보고 무언가를 저장하는 작업,
 *    @Transactional 테스트)에서는 항상 DB를 직접 읽고 캐시에 넣지도 않는다 — 아직 커밋 안 된 값이 캐시에 남지 않게.
 *  - HobbyService 의 추가·편집·삭제는 invalidate 로 즉시 + 트랜잭션이 끝난 뒤(커밋·롤백 모두) 한 번 더 비운다.
 *  - 팀원 PC마다 앱이 따로 떠 있어도(같은 공용 DB) 다른 앱에서 바꾼 값이 늦어도 1분 안에 보이도록 TTL 을 둔다.
 *  - 캐시에 든 Category·Hobby 엔티티는 여러 요청이 같이 읽는다 — 값을 바꾸지 말 것(바꿀 때는 Repository 로 새로 읽는다).
 * [기술] 🟡 TransactionSynchronizationManager (트랜잭션 상태 확인·완료 후 콜백)
 */
@Component
@RequiredArgsConstructor
public class HobbyCatalogCache {

    /** 다른 앱(팀원 PC 등)에서 바꾼 값이 늦어도 이 시간 안에 반영된다 */
    static final Duration TTL = Duration.ofMinutes(1);

    private final CategoryRepository categoryRepository;
    private final HobbyRepository hobbyRepository;

    /** 마지막으로 읽어 둔 목록 (없으면 null). 여러 요청 스레드가 같이 보므로 volatile */
    private volatile Entry entry;

    /**
     * 대분류·세부 취미 전체 (노출 규칙 적용 전, 정렬 순서 그대로).
     * 호출하는 쪽이 트랜잭션 안이어야 한다(HobbyService 조회 메서드는 모두 @Transactional(readOnly = true)).
     */
    public HobbyCatalog get() {
        if (inWriteTransaction()) {
            return load();  // 쓰기 트랜잭션: 항상 최신 값, 캐시에 넣지 않음
        }
        Entry current = entry;
        if (current != null && current.isFresh()) {
            return current.catalog();
        }
        HobbyCatalog loaded = load();
        entry = new Entry(loaded, System.nanoTime());
        return loaded;
    }

    /**
     * 캐시를 비운다 — 대분류·세부 취미를 추가·편집·삭제한 뒤.
     * 지금 바로 한 번, 트랜잭션이 끝난 뒤(커밋이든 롤백이든) 한 번 더 비운다:
     * 커밋 직전에 다른 요청이 옛 값을 다시 읽어 넣어 두었더라도 커밋 뒤에 지워진다.
     */
    public void invalidate() {
        entry = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    entry = null;
                }
            });
        }
    }

    private boolean inWriteTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    private HobbyCatalog load() {
        return HobbyCatalog.of(categoryRepository.findAllByOrderBySortOrderAscIdAsc(),
                hobbyRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    private record Entry(HobbyCatalog catalog, long loadedAtNanos) {
        boolean isFresh() {
            return System.nanoTime() - loadedAtNanos < TTL.toNanos();
        }
    }

    /**
     * 한 번에 읽어 둔 대분류·세부 취미 목록. 모두 읽기 전용(불변) 컬렉션이다.
     *
     * @param categories        대분류 전체 (sortOrder, id 순)
     * @param hobbiesByCategory 대분류 id → 그 아래 세부 취미 (sortOrder, id 순)
     * @param categoryById      대분류 id → 대분류
     * @param hobbyById         세부 취미 id → 세부 취미
     */
    public record HobbyCatalog(List<Category> categories,
                               Map<Long, List<Hobby>> hobbiesByCategory,
                               Map<Long, Category> categoryById,
                               Map<Long, Hobby> hobbyById) {

        static HobbyCatalog of(List<Category> categories, List<Hobby> hobbies) {
            return new HobbyCatalog(
                    List.copyOf(categories),
                    Map.copyOf(hobbies.stream().collect(Collectors.groupingBy(Hobby::getCategoryId,
                            Collectors.collectingAndThen(Collectors.toList(), List::copyOf)))),
                    categories.stream().collect(Collectors.toUnmodifiableMap(Category::getId, Function.identity())),
                    hobbies.stream().collect(Collectors.toUnmodifiableMap(Hobby::getId, Function.identity())));
        }
    }
}
