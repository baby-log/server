package com.babylog.biz.sync.support;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * M53 账号级「云端有变更」轻量探测；M56 扩展为可见范围级 + 长轮询挂起。
 *
 * <p>用户验收问题 3：原实现没有服务端推送通道，Android 兜底周期是 6 小时、
 * iOS 只在进入前台时同步，其它设备写完记录本机往往要等很久才看到。
 *
 * <p><b>M56 两处修正</b>：
 * <ul>
 *   <li><b>可见范围级</b>：{@code /sync/changes} 读的是家庭全部 ACTIVE 成员的行，
 *       而旧实现只 bump 推送者自己的 key，跨账号家庭共享时其它成员永远感知不到。
 *       现在 {@code markChanged(Collection)} 一次性抬高整个可见范围；</li>
 *   <li><b>长轮询</b>：{@code /sync/ping?wait=...} 在无变更时把请求挂起，
 *       有写入时由 {@code notifyWaiters} 立刻唤醒，把 9 秒轮询的固有等待降到 1 RTT。
 *       不带 {@code wait} 参数的旧包仍走「立即返回」原语义，兼容未重打的客户端。</li>
 * </ul>
 *
 * <p><b>边界</b>：进程内内存，服务重启后归零——客户端首次 ping 会拿到新值并触发
 * 一次增量拉取（幂等，成本可接受）；单机部署（当前 1Panel）语义完整。
 * 值只用于「是否变化」的比较，绝不当业务时间、不参与游标计算。
 * 等待者只存活于内存且带超时，服务重启时客户端会拿到超时/错误并按退避重连。
 */
@Component
public class SyncChangeNotifier {

    private final Map<Long, Long> lastChangeAt = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<Waiter>> waiters = new ConcurrentHashMap<>();

    /** 单账号变更（保留旧签名，供单测与非家庭路径使用） */
    public void markChanged(long userId) {
        markChanged(java.util.List.of(userId));
    }

    /**
     * 抬高整个可见范围的变更戳并唤醒各自挂起的 ping。
     * 同一次写入对范围内所有账号用同一个 {@code now}，各自 key 仍保持单调递增。
     */
    public void markChanged(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Long id : userIds) {
            if (id == null) {
                continue;
            }
            long uid = id;
            lastChangeAt.compute(uid, (key, old) -> old == null || now > old ? now : old + 1);
            notifyWaiters(uid);
        }
    }

    /** 返回该账号当前变更戳；从未写入过返回 0（客户端据此可只做一次基线同步） */
    public long lastChangeAt(long userId) {
        return lastChangeAt.getOrDefault(userId, 0L);
    }

    /**
     * 登记一个挂起的 ping。调用方必须在登记<b>之后</b>再查一次 {@link #lastChangeAt(long)}
     * 做二次判定，以覆盖「读取旧值 → 登记」之间恰好发生写入的竞态。
     *
     * @return 取消登记用的 token
     */
    public String subscribe(long userId, Consumer<Long> callback) {
        String token = UUID.randomUUID().toString();
        waiters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>())
                .add(new Waiter(token, callback));
        return token;
    }

    /** 请求完成/超时/被唤醒后移除登记，避免内存泄漏 */
    public void unsubscribe(long userId, String token) {
        CopyOnWriteArrayList<Waiter> list = waiters.get(userId);
        if (list == null) {
            return;
        }
        list.removeIf(waiter -> waiter.token.equals(token));
    }

    /** 仅供单测：当前挂起数量 */
    public int pendingWaiterCount(long userId) {
        CopyOnWriteArrayList<Waiter> list = waiters.get(userId);
        return list == null ? 0 : list.size();
    }

    /**
     * 逐个取出并唤醒。这里不整体 remove 列表，避免与并发 subscribe 竞态导致新等待者
     * 挂在已被移除的旧列表引用上；挂起方另有超时与二次判定兜底。
     */
    private void notifyWaiters(long userId) {
        CopyOnWriteArrayList<Waiter> list = waiters.get(userId);
        if (list == null) {
            return;
        }
        long stamp = lastChangeAt.getOrDefault(userId, 0L);
        for (Waiter waiter : list) {
            if (list.remove(waiter)) {
                waiter.callback.accept(stamp);
            }
        }
    }

    private record Waiter(String token, Consumer<Long> callback) {
    }
}
