package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 记录类型云端规则（服务端运营表，M41）：一类型一行，只调「已有记录类型」的
 * 默认开关 / 起止月龄 / 记录页排序 / 分组 / 显示文案——<b>不能云端新增记录类型</b>
 * （10 文档第八节边界写死，防止膨胀成动态表单引擎；管理端无新增入口）。
 *
 * <p>走 /app/babylog/base/record-type-rules 增量下发（base 通道，独立游标），
 * App 端落在规则缓存表后按优先级链合并：
 * 用户覆盖（record_type_settings）&gt; 云端规则包 &gt; 代码默认注册表，
 * 月龄判定照旧最外层过滤。
 *
 * <p>enabled / startAgeDays / endAgeDays 允许为 NULL = 「该项不覆盖，回落客户端
 * 代码默认」——运营只想改文案或排序时不用被迫填齐所有字段。
 *
 * <p>刻意<b>不声明 serverUpdatedAt 字段</b>：列级 ON UPDATE 自动维护游标，
 * 实体声明反而会让 MP updateById 把 SELECT 出来的旧值显式写回（M10/M18 的教训），
 * 值不变 → ON UPDATE 不触发 → 游标不前进 → App 永远拉不到改动。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_record_type_rule")
public class BlRecordTypeRule extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录类型标识（uk；与 App 端 RecordType 枚举 name 逐字一致） */
    private String recordType;

    /** 分类（FEEDING/CARE/HEALTH/MEDICAL，只读展示用，客户端以注册表为准） */
    private String category;

    /** 默认开关：NULL=回落代码默认，0=默认关，1=默认开 */
    private Integer enabled;

    /** 起始月龄（天）：NULL=回落代码默认 */
    private Integer startAgeDays;

    /** 截止月龄（天）：NULL=回落代码默认；代码默认也 NULL = 永不自动收起 */
    private Integer endAgeDays;

    /** 记录页类内排序（小的在前；0=按注册表默认顺序） */
    private Integer sortOrder;

    /** 类内分组键（客户端按组聚合排序；NULL=默认不分组） */
    private String groupKey;

    /** 显示文案：NULL=回落代码默认文案 */
    private String displayLabel;
}
