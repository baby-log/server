package com.babylog.biz.recordtype.service;

import com.babylog.biz.recordtype.dto.RecordTypeRuleEditCmd;
import com.babylog.biz.recordtype.dto.RecordTypeRulePageQuery;
import com.babylog.biz.recordtype.vo.RecordTypeRuleVO;
import com.babylog.common.core.page.PageResult;

/**
 * 管理端记录类型规则维护（M41）。
 *
 * <p>只有分页与编辑——<b>没有新增/删除</b>：边界写死「云端只能调已有类型的
 * 默认开关、起止月龄、排序、分组、显示文案，不能云端新增记录类型」
 * （防止运营需求膨胀成动态表单引擎，10 文档第八节）。
 *
 * <p>写路径：selectById 载入 → 改字段 → updateById。游标列 server_updated_at
 * 列级 ON UPDATE 自动维护，实体刻意不声明该字段（M10/M18 教训）——MP 不会把它
 * 显式写回旧值，ON UPDATE 必然触发，App 端 base 通道（按游标增量）能看到改动。
 */
public interface AdminRecordTypeRuleService {

    /** 规则分页（category/recordType 过滤，sort_order 升序） */
    PageResult<RecordTypeRuleVO> page(RecordTypeRulePageQuery query);

    /** 规则详情（8392 不存在） */
    RecordTypeRuleVO get(long id);

    /** 编辑规则（8392 不存在） */
    void edit(long id, RecordTypeRuleEditCmd cmd);
}
