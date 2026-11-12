package com.babylog.biz.sync.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 同步游标编解码：{@code server_updated_at DATETIME(3)} ↔ epoch millis。
 *
 * <p><b>为什么不直接把 LocalDateTime 丢给客户端</b>：App 端（Room）全链路用 epoch millis，
 * 让 Kotlin 去解析 ISO 字符串只会多一处时区坑；统一发数字，客户端只做「原样回传」。
 *
 * <p><b>nextSince 是不透明游标，不是时间戳</b>——这一点必须写进客户端约定：
 * <ul>
 *   <li>MySQL 的 {@code CURRENT_TIMESTAMP(3)} 落在<b>数据库会话时区</b>的墙上时间，
 *       驱动读回来是裸 LocalDateTime（不带时区）。这里用 JVM 默认时区解释它，
 *       只要「写进去」和「读出来」用同一套解释，往返就是精确的；</li>
 *   <li>若数据库时区与 JVM 时区不同，nextSince 的绝对值会有固定偏移（例如差 8 小时），
 *       但<b>不影响增量正确性</b>——服务端永远按同一套编码解回同一个 LocalDateTime；</li>
 *   <li>所以客户端<b>不得</b>把 nextSince 拿去和手机时钟比较、也不得自己加减。
 *       要「从头拉」就传 since=0，要「接着拉」就原样回传 nextSince。</li>
 * </ul>
 */
public final class SyncCursor {

    private SyncCursor() {
    }

    /** 库内游标 → 客户端可回传的数值 */
    public static long toMillis(LocalDateTime serverUpdatedAt) {
        return serverUpdatedAt == null ? 0L : serverUpdatedAt.atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
    }

    /** 客户端回传的数值 → 查询用游标（与 {@link #toMillis} 同一套时区解释，往返精确） */
    public static LocalDateTime toLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /** 服务端当前时间（同样的编码，供客户端做「服务端时间」参考与时钟漂移诊断） */
    public static long now() {
        return toMillis(LocalDateTime.now());
    }

    /**
     * 客户端回传 limit 的钳制：null/非正 → 默认值，超上限 → 上限。
     *
     * <p>从 SyncServiceImpl 下沉到这（M8 基础数据通道复用）：两个通道的分页口径必须
     * 同一套，散在两个 service 里迟早一个改了另一个忘。
     */
    public static int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    /**
     * 游标下界的防御：负数/null 按 0（全量），远超服务端时间的按服务端时间（客户端误传手机时钟）。
     *
     * <p>容差由调用方给（两通道共用同一值）。注意 nextSince 是<b>不透明游标</b>，
     * 服务端自己解出来的游标不会触发纠正分支。
     */
    public static long normalizeSince(Long sinceMillis, long serverTime, long futureToleranceMillis) {
        if (sinceMillis == null || sinceMillis < 0) {
            return 0L;
        }
        if (sinceMillis > serverTime + futureToleranceMillis) {
            return serverTime;
        }
        return sinceMillis;
    }
}
