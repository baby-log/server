package com.babylog.biz.adminconsole.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * M53：管理端跨用户只读查询的分页结果。
 *
 * <p>同时暴露 {@code list}（框架 PageResult 口径）与 {@code records}（前端表格常用口径），
 * 两个字段指向同一份数据，避免前后端口径不一致导致的空表格。
 */
@Data
@Schema(description = "管理端只读分页结果")
public class AdminPageResult<T> {

    @Schema(description = "当前页数据（框架口径）")
    private List<T> list;

    @Schema(description = "当前页数据（表格口径，与 list 同一份）")
    private List<T> records;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "页码")
    private int pageNum;

    @Schema(description = "每页条数")
    private int pageSize;

    public AdminPageResult(List<T> rows, long total, int pageNum, int pageSize) {
        this.list = rows;
        this.records = rows;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}
