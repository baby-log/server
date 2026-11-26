package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 育儿知识库 · 条目化 FAQ（K6-S / M76，服务端专用表）。
 *
 * <p><b>不进 {@code SyncTable}</b>：本表是服务端运营录入的权威资料摘录，不参与
 * {@code /sync/changes}、不进 outbox、端点不占 /sync/ 开头的同步端点（与 {@code bl_ai_quota}
 * 同口径）。四端只读浏览，不产生同步行。
 *
 * <p><b>红线一「来源可追溯」</b>：{@code source} 是必填字段（DB 有 DEFAULT '' 只为兼容
 * 历史迁移，业务层在写入前强制非空，否则 {@code 8465}）。{@code content} 由应用层卡
 * 2000 字上限，禁止把网络文章整篇搬运入库（{@code 10} 号 §4.1）。
 *
 * <p>上架状态用 {@code enabled}（1/0）：TINYINT 布尔按全局契约，JSON 里是整数 1/0，
 * 客户端读走 {@code JsonExt.boolOrNull}、push 必发 1/0 绝不发 true/false。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_kb_faq")
public class BlKbFaq extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类：FEEDING / SLEEP / HEALTH / GROWTH / SAFETY / OTHER */
    private String category;

    /** 条目标题 */
    private String title;

    /** 摘录正文（应用层限 2000 字） */
    private String content;

    /** 资料来源（机构 / 书名 / 文件名，必填，红线一） */
    private String source;

    /** 来源链接（可选） */
    private String sourceUrl;

    /** 是否上架：1 上架 0 下架（TINYINT，JSON 整数） */
    private Integer enabled;

    /** 同分类内排序（小的在前） */
    private Integer sortOrder;
}
