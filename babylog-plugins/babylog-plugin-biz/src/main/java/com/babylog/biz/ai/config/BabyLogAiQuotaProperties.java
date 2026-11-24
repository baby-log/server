package com.babylog.biz.ai.config;

import com.babylog.biz.ai.enums.AiFeature;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 免费额度配置（K5 / M73；龙哥 2026-11-24 拍板：设额度 + 超限拒绝）。
 *
 * <p><b>数值不入代码</b>：只有这里与 yml 的 {@code babylog.ai.quota.features.*} 持有具体次数，
 * 服务层一律读本配置。改额度只改 yml + 重启，不动代码、不改库。
 *
 * <p><b>口径（拍板时确认）</b>：
 * <ul>
 *   <li>计次粒度 = <b>家庭 × 功能</b>（{@code bl_ai_quota.scope_key}）：家里三人共用一份额度；</li>
 *   <li>超限行为 = <b>拒绝</b>（{@code 8460}），不发模型请求、不降级伪造内容；</li>
 *   <li>{@code limit <= 0} 或缺配置 = <b>不限次、只计次</b>——兜底方向选「不挡人」，
 *       新增功能忘了配额度也不会把用户锁在门外。</li>
 * </ul>
 *
 * <p>窗口按服务端 JVM 时区计算：{@code DAILY} = 次日 00:00 归零，{@code WEEKLY} = 下周一 00:00 归零。
 */
@Data
@ConfigurationProperties(prefix = "babylog.ai.quota")
public class BabyLogAiQuotaProperties {

    /** key = {@link AiFeature#code()}；未出现的功能按「不限次」处理 */
    private Map<String, FeatureQuota> features = new LinkedHashMap<>();

    /** 取某功能的额度配置；返回 {@code null} 表示未配置（不限次） */
    public FeatureQuota of(AiFeature feature) {
        return feature == null ? null : features.get(feature.code());
    }

    @Data
    public static class FeatureQuota {

        /** 每窗口免费次数；{@code <= 0} = 不限次（只计次） */
        private int limit = 0;

        /** 归零窗口 */
        private Window window = Window.DAILY;
    }

    /** 额度窗口（按服务端本地时区对齐自然日 / 自然周） */
    public enum Window {
        /** 次日 00:00 归零 */
        DAILY,
        /** 下周一 00:00 归零 */
        WEEKLY
    }
}
