package com.babylog.biz.kb.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端 FAQ 保存入参（K6-S / M76）。
 *
 * <p><b>红线一</b>：{@code source} 必填（{@code 8465}）；{@code content} 限 2000 字，
 * 禁整篇搬运（{@code 10} 号 §4.1）。{@code id} 为空 = 新增，非空 = 编辑。
 */
@Data
public class KbFaqSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 为空 = 新增；非空 = 编辑 */
    private Long id;

    private String category;

    private String title;

    private String content;

    /** 资料来源（必填） */
    private String source;

    private String sourceUrl;

    /** 1 上架 / 0 下架（TINYINT 语义，JSON 整数 1/0） */
    private Integer enabled;

    private Integer sortOrder;
}
