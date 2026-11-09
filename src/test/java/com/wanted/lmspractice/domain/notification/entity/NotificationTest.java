package com.wanted.lmspractice.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.lmspractice.domain.notification.dto.NotificationTab;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [역할] Notification.create의 대상 칼럼 매핑(13종 모두)과 NotificationTab 묶음 검사
 * [담당] 팀원 4 · #17
 */
class NotificationTest {

    @Test
    @DisplayName("13종 모두 종류가 가리키는 대상 칼럼 하나에만 id가 들어간다")
    void exactlyOneTargetColumn() {
        for (NotificationKind kind : NotificationKind.values()) {
            Notification n = Notification.create(2L, kind, 77L, "제목", "본문", kind.name() + ":77");
            List<Long> columns = new ArrayList<>(java.util.Arrays.asList(
                    n.getEnrollmentId(), n.getReviewRoundId(), n.getWorkId(),
                    n.getCommentId(), n.getInquiryThreadId(), n.getSectionId()));
            columns.removeIf(java.util.Objects::isNull);
            assertThat(columns).as(kind.name()).containsExactly(77L);
            assertThat(n.getTargetId()).as(kind.name()).isEqualTo(77L);
        }
    }

    @Test
    @DisplayName("탭 묶음: 학습자·강사 탭이 13종을 빠짐없이 한 번씩 나눠 가진다")
    void tabsCoverAllKindsOnce() {
        List<NotificationKind> all = new ArrayList<>();
        for (NotificationTab tab : NotificationTab.values()) {
            if (tab != NotificationTab.ALL) {
                all.addAll(tab.getKinds());
            }
        }
        assertThat(all).containsExactlyInAnyOrder(NotificationKind.values());
    }

    @Test
    @DisplayName("모르는 탭 값은 전체로 본다")
    void unknownTabIsAll() {
        assertThat(NotificationTab.of(null)).isEqualTo(NotificationTab.ALL);
        assertThat(NotificationTab.of("nonsense")).isEqualTo(NotificationTab.ALL);
        assertThat(NotificationTab.of("result")).isEqualTo(NotificationTab.RESULT);
    }
}
