package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * AI 用量（服务端运营表，V3 启用；按<b>家庭 × 功能</b>计次）。(scope_key, feature) 唯一。
 *
 * <p>K5 / M73 口径变更（龙哥 2026-11-24 拍板）：额度从「用户 × 功能」改为「家庭 × 功能」共享。
 * {@code scope_key} = {@code F:<familyId>}（有 ACTIVE 家庭成员行，家庭共用一份额度）
 * 或 {@code U:<userId>}（无家庭，个人额度，行为与 V2.x 一致）。
 * {@code user_id} 从唯一键成员退化为「最后操作人 + 审计」。
 *
 * <p>本表不进 {@code SyncTable} 枚举、不参与 {@code /sync/changes}，App 端无对应表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_ai_quota")
public class BlAiQuota extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 最后操作人 user_id（审计；唯一性由 scopeKey 表达，不再用它判重） */
    private Long userId;

    /** 额度归属：F:&lt;family_id&gt; 家庭共享 / U:&lt;user_id&gt; 个人（无家庭） */
    private String scopeKey;

    /** 功能标识（如 PARSE_RECORD/WEEKLY_REPORT/HANDWRITING_IMPORT） */
    private String feature;

    /** 额度上限（&lt;=0 表示不限次，只计次） */
    private Integer quotaLimit;

    /** 已用额度 */
    private Integer quotaUsed;

    /** 重置时刻（到达后 quota_used 归零并顺延下一个窗口） */
    private LocalDateTime resetAt;
}
