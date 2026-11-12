package com.babylog.biz.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.babylog.common.core.page.PageQuery;
import com.babylog.common.core.page.PageResult;

import java.util.List;

/**
 * 分页对象转换工具（PageQuery → MP Page → PageResult）。
 *
 * <p>与 message/system 插件各自的 PageUtils 同款实现——各插件自带一份、
 * 不跨插件抽公共依赖（A1/A2：实现包之间零依赖）。
 */
public final class PageUtils {

    private PageUtils() {
    }

    /** PageQuery → MyBatis-Plus Page */
    public static <T> Page<T> toMpPage(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /** MP Page + 转换后的记录列表 → PageResult */
    public static <T, R> PageResult<R> toPageResult(Page<T> page, List<R> records) {
        return new PageResult<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
