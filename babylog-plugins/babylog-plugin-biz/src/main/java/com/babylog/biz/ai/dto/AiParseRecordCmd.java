package com.babylog.biz.ai.dto;

import lombok.Data;

/**
 * 自然语言补录入站（K5 / M73，范围 B）。
 *
 * <p>只收一段文本（口述/手打），不收结构化字段——结构化由模型产出草稿、由用户确认。
 * <b>本端点不落库</b>：入站的 {@code text} 只用于本次模型调用，不入库、不写日志正文。
 */
@Data
public class AiParseRecordCmd {

    /** 所属宝宝 id（字符串入站；须在当前账号的 visibleUserIds 家庭可见范围内） */
    private Long childId;

    /** 自然语言原文（如「10点喂了120ml奶粉」）；上限 500 字，防超长 prompt 烧额度 */
    private String text;
}
