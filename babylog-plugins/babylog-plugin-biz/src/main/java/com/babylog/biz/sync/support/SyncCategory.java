package com.babylog.biz.sync.support;

import java.util.Arrays;
import java.util.Locale;

/**
 * 同步分类（与 02 号文档「权限模型」的四分类一致，V2.1 家庭共享时按它下发读写权限）。
 *
 * <p>分类只用于<b>拉取过滤</b>（客户端按需拉，省流量）与 V2.1 的权限矩阵；
 * V2.0 单用户模型下 owner 全权，分类过滤不构成安全边界——真正的边界是
 * {@code user_id} 归属校验（拉取只返回自己的行，推送只写自己的行）。
 *
 * <p>不挂分类的表（宝宝档案 / 产品库 / 提醒 / 家庭成员 / 成员权限 / 记录类型开关）
 * 视为「基础设施数据」，无论客户端传什么 categories 都随行下发——少了它们，
 * 拉回来的业务记录连 child_id 都对不上号。
 */
public enum SyncCategory {

    /** 喂养 */
    FEEDING,
    /** 护理（尿布 / 睡眠 / 洗澡 / 吐奶 / 泵奶） */
    CARE,
    /** 健康（测量） */
    HEALTH,
    /** 医疗（用药 / 疫苗 / 随访 / 就医 / 附件） */
    MEDICAL;

    /**
     * 解析客户端的 {@code categories=FEEDING,CARE} 参数。
     *
     * @return {@code null} 表示「不限分类」（参数为空）；否则为请求的分类集合（可能为空集合）
     * @throws IllegalArgumentException 出现未知分类标识（由调用方翻译成 8346）
     */
    public static java.util.Set<SyncCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.Set<SyncCategory> result = java.util.EnumSet.noneOf(SyncCategory.class);
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.add(Arrays.stream(values())
                    .filter(c -> c.name().equals(trimmed.toUpperCase(Locale.ROOT)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未知同步分类：" + trimmed)));
        }
        return result;
    }
}
