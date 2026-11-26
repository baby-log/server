package com.babylog.biz.kb.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 育儿知识库条目（K6-S / M76，浏览 / 详情 / 收藏 / 管理端列表共用）。
 *
 * <p><b>全局契约</b>：所有 Long（id）由 fastjson2 序列化成字符串；
 * {@code enabled} / {@code favorited} 用整数 1/0 而不是 true/false
 * （TINYINT 布尔契约，客户端读走 {@code JsonExt.boolOrNull}）。
 * null 键缺省，四端容忍缺键。
 */
@Data
public class KbFaqVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String category;

    private String title;

    private String content;

    /** 资料来源（红线一，永不为空） */
    private String source;

    private String sourceUrl;

    /** 1 上架 / 0 下架 */
    private Integer enabled;

    private Integer sortOrder;

    /** 当前用户是否已收藏：1 是 / 0 否；管理端列表恒为 0 */
    private Integer favorited;
}
