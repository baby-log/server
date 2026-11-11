package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * AI 用量（服务端运营表，V3 启用；按用户 × 功能计次）。(user_id, feature) 唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_ai_quota")
public class BlAiQuota extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 id */
    private Long userId;

    /** 功能标识（如 PARSE_RECORD/WEEKLY_REPORT/HANDWRITING_IMPORT） */
    private String feature;

    /** 额度上限 */
    private Integer quotaLimit;

    /** 已用额度 */
    private Integer quotaUsed;

    /** 重置时刻 */
    private LocalDateTime resetAt;
}
