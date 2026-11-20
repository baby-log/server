package com.babylog.biz.sync.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M53：账号级「云端有变更」轻量探测。
 *
 * <p>用户验收问题 3：原实现没有服务端推送通道，Android 兜底周期是 6 小时、
 * iOS 只在进入前台时同步，其它设备写完记录本机往往要等很久才看到。
 *
 * <p>本组件在每个账号有成功写入时记一个单调递增的时间戳；客户端在前台
 * （或定时、页面可见时）调用 {@code GET /app/babylog/sync/ping?since=...}，
 * 拿到的值比本地大就立刻跑一轮 {@code /sync/changes}。小程序不支持 SSE/长连接，
 * 所以统一走普通 HTTP 短请求，四端都能承接。
 *
 * <p><b>边界</b>：进程内内存，服务重启后归零——客户端首次 ping 会拿到新值并触发
 * 一次全量增量拉取（幂等，成本可接受）；单机部署（当前 1Panel）语义完整。
 * 值只用于「是否变化」的比较，绝不当业务时间、不参与游标计算。
 */
@Component
public class SyncChangeNotifier {

    private final Map<Long, Long> lastChangeAt = new ConcurrentHashMap<>();

    /** 有记录成功落库时调用（在事务提交之后，避免回滚误报） */
    public void markChanged(long userId) {
        long now = System.currentTimeMillis();
        lastChangeAt.compute(userId, (key, old) -> old == null || now > old ? now : old + 1);
    }

    /** 返回该账号当前变更戳；从未写入过返回 0（客户端据此可只做一次基线同步） */
    public long lastChangeAt(long userId) {
        return lastChangeAt.getOrDefault(userId, 0L);
    }
}
