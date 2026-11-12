package com.babylog.biz.sync.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游标编解码单测：往返必须精确到毫秒，否则同一毫秒的写入会在下一页被跳过或重复推进。
 */
class SyncCursorTest {

    @Test
    void roundTripKeepsMillisecondPrecision() {
        LocalDateTime value = LocalDateTime.of(2026, 11, 12, 16, 30, 45, 123_000_000);

        long millis = SyncCursor.toMillis(value);

        assertThat(SyncCursor.toLocalDateTime(millis)).isEqualTo(value);
    }

    @Test
    void nullCursorFallsBackToZero() {
        assertThat(SyncCursor.toMillis(null)).isZero();
    }

    @Test
    void orderingIsPreserved() {
        // 游标能用于「大于」比较的前提：编码保序（同一套时区解释下，时间大的编码也大）
        assertThat(SyncCursor.toMillis(LocalDateTime.of(2026, 11, 12, 16, 30, 45, 1_000_000)))
                .isLessThan(SyncCursor.toMillis(LocalDateTime.of(2026, 11, 12, 16, 30, 45, 2_000_000)));
    }

    @Test
    void nowIsUsableAsACursor() {
        long now = SyncCursor.now();

        assertThat(now).isGreaterThan(1_700_000_000_000L);
        // 解回来再编码必须稳定（客户端回传的就是这个数）
        assertThat(SyncCursor.toMillis(SyncCursor.toLocalDateTime(now))).isEqualTo(now);
    }
}
