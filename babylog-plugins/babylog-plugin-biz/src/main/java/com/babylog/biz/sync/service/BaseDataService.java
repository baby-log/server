package com.babylog.biz.sync.service;

import com.babylog.biz.sync.vo.SyncChangesVO;

/**
 * 基础数据下发（M8）：内置产品等全局数据的增量通道。
 *
 * <p>为什么单独开通道而不并进 {@code /sync/changes}：同步通道的归属过滤是
 * {@code user_id = 自己}，而内置行是全局共享（user_id=0 占位）；把 products 表
 * 特殊化成「user_id=自己 OR is_builtin=1」会让 18 表统一的游标/归并逻辑开第一个
 * 例外口子。单独端点 + 单独游标，App 端只是多存一对游标。
 */
public interface BaseDataService {

    /**
     * 内置产品增量拉取。
     *
     * <p>行格式与 {@code /sync/changes} 完全一致（表名作键、行内 camelCase、
     * 含 deleted/version），App 端复用同一套解析与 upsert。
     *
     * @param sinceMillis 游标（{@code null} 视为 0，全量）
     * @param sinceId     同毫秒 tiebreak
     * @param limit       单页上限
     */
    SyncChangesVO pullBuiltinProducts(Long sinceMillis, Long sinceId, Integer limit);

    /**
     * 疫苗时间表增量下发（M40）：运营表 bl_vaccine_schedule 单向通道，changes 键
     * {@code vaccine_schedule}。App 端 M40 只落缓存，消费方（云端规则批）后续接。
     */
    SyncChangesVO pullVaccineSchedule(Long sinceMillis, Long sinceId, Integer limit);

    /**
     * 异常提醒阈值增量下发（M40）：键值对行（thresholdKey/thresholdValue），changes 键
     * {@code alert_thresholds}。App 端按键解析后替换 AlertThresholds 本地默认值。
     */
    SyncChangesVO pullAlertThresholds(Long sinceMillis, Long sinceId, Integer limit);

    /**
     * 版本与公告增量下发（M40）：只下发 PUBLISHED 行（DRAFT 是后台草稿），changes 键
     * {@code app_versions}。不按 platform 过滤——客户端按自己平台挑行，
     * 避免「platform 参数 × 游标分页」的组合陷阱。
     */
    SyncChangesVO pullPublishedVersions(Long sinceMillis, Long sinceId, Integer limit);

    /**
     * 记录类型云端规则增量下发（M41）：bl_record_type_rule 单向通道，changes 键
     * {@code record_type_rules}。App 端落规则缓存表后按优先级链合并
     * （用户覆盖 &gt; 云端规则 &gt; 代码默认），月龄判定最外层过滤照旧。
     */
    SyncChangesVO pullRecordTypeRules(Long sinceMillis, Long sinceId, Integer limit);
}
