package com.babylog.biz.family.support;

import java.security.SecureRandom;

/**
 * 家庭邀请码生成（02 号文档：一个家庭一个邀请码，可重置）。
 *
 * <p>8 位、无歧义字符表（剔除 0/O/1/I，长辈手输/口述场景少踩坑）。
 * 32^8 ≈ 1.1 万亿组合，全局唯一约束（uk_invite_code）+ 重试兜底撞码。
 */
public final class InviteCodes {

    /** 无歧义字符表：没有 0/O/1/I */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int LENGTH = 8;

    private static final int MAX_RETRY = 5;

    private InviteCodes() {
    }

    /**
     * 生成一个新邀请码（大写）。
     *
     * @param exists 唯一性探针：传入 true 表示「该码已被占用」，生成器换一个再试；
     *               连续 {@value #MAX_RETRY} 次撞码（概率 1/万亿级）抛 IllegalStateException
     */
    public static String generate(java.util.function.Predicate<String> exists) {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!exists.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("邀请码连续撞码 " + MAX_RETRY + " 次，请检查 bl_family.invite_code 数据");
    }

    /** 入库/查询前的归一化：去首尾空白 + 转大写（用户手输小写也算对） */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }
}
