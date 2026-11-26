package com.babylog.biz.kb.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 知识库分类及该分类下已上架条目数（K6-S / M76）。 */
@Data
public class KbCategoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类码：FEEDING / SLEEP / HEALTH / GROWTH / SAFETY / OTHER */
    private String category;

    /** 已上架条目数 */
    private Integer count;
}
