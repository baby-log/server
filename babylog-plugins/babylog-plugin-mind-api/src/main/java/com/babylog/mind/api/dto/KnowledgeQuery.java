package com.babylog.mind.api.dto;

import com.babylog.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库列表查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeQuery extends PageQuery {

    /** 类型过滤 */
    private String type;

    /** 关键词（标题/内容） */
    private String keyword;
}
