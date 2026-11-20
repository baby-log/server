package com.babylog.biz.sync.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M56 变更戳回归：可见范围级 bump、单调性、长轮询等待者唤醒与取消。
 */
class SyncChangeNotifierTest {

    @Test
    void markChangedBumpsWholeVisibleScope() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();

        notifier.markChanged(List.of(101L, 102L, 103L));

        long stamp = notifier.lastChangeAt(101L);
        assertThat(stamp).isPositive();
        assertThat(notifier.lastChangeAt(102L)).isEqualTo(stamp);
        assertThat(notifier.lastChangeAt(103L)).isEqualTo(stamp);
        // 范围外账号不受影响，避免全库误唤醒
        assertThat(notifier.lastChangeAt(999L)).isZero();
    }

    @Test
    void stampIsStrictlyMonotonic() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();

        notifier.markChanged(7L);
        long first = notifier.lastChangeAt(7L);
        notifier.markChanged(7L);
        long second = notifier.lastChangeAt(7L);
        notifier.markChanged(7L);
        long third = notifier.lastChangeAt(7L);

        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }

    @Test
    void waiterIsWokenOnChange() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();
        AtomicLong received = new AtomicLong(-1L);

        String token = notifier.subscribe(7L, received::set);
        assertThat(notifier.pendingWaiterCount(7L)).isEqualTo(1);

        notifier.markChanged(7L);

        assertThat(received.get()).isEqualTo(notifier.lastChangeAt(7L));
        assertThat(notifier.pendingWaiterCount(7L)).isZero();
        // 幂等：唤醒后再取消不应抛异常
        notifier.unsubscribe(7L, token);
    }

    @Test
    void waiterIsNotWokenWithoutChange() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();
        AtomicInteger calls = new AtomicInteger();

        notifier.subscribe(7L, stamp -> calls.incrementAndGet());
        notifier.markChanged(8L);

        assertThat(calls.get()).isZero();
        assertThat(notifier.pendingWaiterCount(7L)).isEqualTo(1);
    }

    @Test
    void unsubscribeRemovesWaiter() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();
        AtomicInteger calls = new AtomicInteger();

        String token = notifier.subscribe(7L, stamp -> calls.incrementAndGet());
        notifier.unsubscribe(7L, token);
        notifier.markChanged(7L);

        assertThat(calls.get()).isZero();
        assertThat(notifier.pendingWaiterCount(7L)).isZero();
    }

    @Test
    void emptyOrNullScopeIsNoOp() {
        SyncChangeNotifier notifier = new SyncChangeNotifier();

        notifier.markChanged(List.of());
        notifier.markChanged(null);

        assertThat(notifier.lastChangeAt(7L)).isZero();
    }
}
