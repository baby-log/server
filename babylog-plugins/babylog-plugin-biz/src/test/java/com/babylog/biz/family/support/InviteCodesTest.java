package com.babylog.biz.family.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 邀请码生成单测。
 */
class InviteCodesTest {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Test
    void generatesEightUppercaseUnambiguousChars() {
        String code = InviteCodes.generate(c -> false);
        assertThat(code).hasSize(8);
        assertThat(code.chars()).allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    @Test
    void normalizesTrimAndUppercase() {
        assertThat(InviteCodes.normalize("  abcd2345 ")).isEqualTo("ABCD2345");
        assertThat(InviteCodes.normalize(null)).isNull();
    }

    @Test
    void retriesOnCollisionAndEventuallySucceeds() {
        // 第 1 次撞码、第 2 次成功
        AtomicInteger calls = new AtomicInteger();
        String code = InviteCodes.generate(c -> calls.incrementAndGet() == 1);
        assertThat(code).hasSize(8);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void throwsAfterMaxRetries() {
        // 全部判占用：验证连续撞码 5 次后的兜底异常（概率 1/万亿级，仅锁行为）
        assertThatThrownBy(() -> InviteCodes.generate(c -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("连续撞码");
    }
}
