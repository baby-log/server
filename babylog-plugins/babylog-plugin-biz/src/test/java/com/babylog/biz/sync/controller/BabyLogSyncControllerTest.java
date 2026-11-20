package com.babylog.biz.sync.controller;

import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.service.SyncService;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncChangeNotifier;
import com.babylog.biz.sync.vo.SyncPushResultVO;
import com.babylog.common.core.result.R;
import com.babylog.starter.core.context.LoginContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * M56 · 长轮询 ping / 可见范围级变更戳的 Controller 层回归。
 *
 * <p>这里的重点是「接线」而不是 notifier 内部算法（后者已有
 * {@link SyncChangeNotifierTest}）：
 * <ul>
 *   <li>旧包不带 wait → 立即返回，响应体与 M53 逐字一致（三键齐全）；</li>
 *   <li>已有变更 → 即使带 wait 也不挂起；</li>
 *   <li>挂起期间写入 → 立刻被唤醒并返回 changed=true（这是延迟能降到 1 RTT 的关键）；</li>
 *   <li>挂起超时 → 回 200 + changed=false，且等待者被清理，不泄漏；</li>
 *   <li>push 成功 → bump 的是<b>整个可见范围</b>，不是只 bump 推送者自己。</li>
 * </ul>
 *
 * <p>用同步阻塞式 CountDownLatch 而非 DeferredResult：后者在 Spring MVC async dispatch
 * 时会重跑 SaInterceptor.preHandle，Sa-Token ThreadLocal 尚未重建 → 1500（dev 服实测）。
 */
class BabyLogSyncControllerTest {

    private static final long USER = 9007199254740993L;

    private final SyncService syncService = mock(SyncService.class);
    private final SyncAccessScopeResolver scopeResolver = mock(SyncAccessScopeResolver.class);
    private final SyncChangeNotifier notifier = new SyncChangeNotifier();
    private final BabyLogSyncController controller =
            new BabyLogSyncController(syncService, notifier, scopeResolver);

    /** currentUserId() 走 LoginContext 静态 ThreadLocal，用与 DeviceBindControllerTest 同款手法 */
    private <T> T asUser(java.util.function.Supplier<T> block) {
        try (MockedStatic<LoginContext> context = mockStatic(LoginContext.class)) {
            context.when(LoginContext::getUserId).thenReturn(USER);
            return block.get();
        }
    }

    @Test
    void legacyPingWithoutWaitReturnsImmediatelyWithM53Shape() {
        notifier.markChanged(USER);
        long stamp = notifier.lastChangeAt(USER);

        R<Map<String, Object>> response = asUser(() -> controller.ping(0L, null));

        Map<String, Object> data = response.getData();
        assertThat(data).containsKeys("changed", "lastChangeAt", "serverTime");
        assertThat(data.get("changed")).isEqualTo(true);
        assertThat(data.get("lastChangeAt")).isEqualTo(stamp);
        assertThat((Long) data.get("serverTime")).isPositive();
    }

    @Test
    void pingWithWaitDoesNotBlockWhenServerAlreadyAhead() {
        notifier.markChanged(USER);
        long stamp = notifier.lastChangeAt(USER);

        long started = System.currentTimeMillis();
        R<Map<String, Object>> response = asUser(() -> controller.ping(0L, 25_000L));
        long elapsed = System.currentTimeMillis() - started;

        assertThat(response.getData().get("changed")).isEqualTo(true);
        assertThat(response.getData().get("lastChangeAt")).isEqualTo(stamp);
        assertThat(elapsed).as("已有变更必须立即返回，不能挂 25 秒").isLessThan(1_000L);
    }

    @Test
    void longPollIsWokenByChangeDuringWait() throws Exception {
        AtomicReference<R<Map<String, Object>>> result = new AtomicReference<>();

        Thread waiter = new Thread(() -> result.set(asUser(() -> controller.ping(0L, 5_000L))));
        waiter.start();
        // 等 controller 真正登记等待者后再写，覆盖「读到旧值 → 登记」之间的竞态
        long deadline = System.currentTimeMillis() + 2_000L;
        while (notifier.pendingWaiterCount(USER) == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(notifier.pendingWaiterCount(USER)).as("controller 应已登记等待者").isEqualTo(1);

        notifier.markChanged(List.of(USER, 42L));
        waiter.join(2_000L);
        assertThat(waiter.isAlive()).as("写入后必须被唤醒，不能等满 5 秒超时").isFalse();

        assertThat(result.get()).isNotNull();
        assertThat(result.get().getData().get("changed")).isEqualTo(true);
        assertThat(result.get().getData().get("lastChangeAt"))
                .isEqualTo(notifier.lastChangeAt(USER));
        // 唤醒后等待者必须被清理（否则每条挂起连接都永久留一个引用）
        assertThat(notifier.pendingWaiterCount(USER)).isZero();
    }

    @Test
    void longPollTimeoutReturnsOkFalseAndCleansWaiter() {
        long started = System.currentTimeMillis();
        R<Map<String, Object>> response = asUser(() -> controller.ping(0L, 150L));
        long elapsed = System.currentTimeMillis() - started;

        assertThat(response.getData().get("changed")).isEqualTo(false);
        assertThat(response.getData().get("lastChangeAt")).isEqualTo(0L);
        assertThat(elapsed).isGreaterThanOrEqualTo(100L);
        assertThat(elapsed).as("超时不能挂超过上限太多").isLessThan(2_000L);
        assertThat(notifier.pendingWaiterCount(USER)).as("超时后等待者必须清理").isZero();
    }

    @Test
    void negativeWaitBehavesLikeLegacyNoWait() {
        R<Map<String, Object>> response = asUser(() -> controller.ping(0L, -1L));

        assertThat(response.getData().get("changed")).isEqualTo(false);
        assertThat(notifier.pendingWaiterCount(USER)).isZero();
    }

    @Test
    void pushBumpsWholeVisibleScopeNotJustPusher() {
        SyncPushResultVO pushed = new SyncPushResultVO();
        pushed.setUpserted(1);
        when(syncService.push(eq(USER), any(SyncPushCmd.class))).thenReturn(pushed);
        when(scopeResolver.resolveAccess(USER))
                .thenReturn(new SyncAccessScope(1L, "OWNER", List.of(USER, 101L, 102L),
                        java.util.EnumSet.noneOf(com.babylog.biz.sync.support.SyncCategory.class),
                        java.util.EnumSet.noneOf(com.babylog.biz.sync.support.SyncCategory.class)));

        asUser(() -> controller.push(new SyncPushCmd()));

        long stamp = notifier.lastChangeAt(USER);
        assertThat(stamp).isPositive();
        assertThat(notifier.lastChangeAt(101L)).as("家庭成员也必须被 bump").isEqualTo(stamp);
        assertThat(notifier.lastChangeAt(102L)).as("家庭成员也必须被 bump").isEqualTo(stamp);
        assertThat(notifier.lastChangeAt(999L)).as("范围外账号不受影响").isZero();
    }

    @Test
    void pushWithNoUpsertedRowsDoesNotBump() {
        SyncPushResultVO pushed = new SyncPushResultVO();
        pushed.setUpserted(0);
        when(syncService.push(eq(USER), any(SyncPushCmd.class))).thenReturn(pushed);

        asUser(() -> controller.push(new SyncPushCmd()));

        assertThat(notifier.lastChangeAt(USER)).isZero();
    }
}
