package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 产品库（同步表，App 端 Room products 对齐）。内置行由后台维护增量下发，
 * 用户自定义行由 App 上行；两态共存靠 is_builtin 区分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_products")
public class BlProduct extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER */
    private String category;

    /** 品牌 */
    private String brand;

    /** 品名 */
    private String name;

    /** 规格 */
    private String spec;

    /** 默认剂量（文本） */
    private String defaultDose;

    /** 展示排序：数字越大越靠前（M67，产品级共享，后台可维护含内置行） */
    private Integer sortOrder;

    /** 最近被记录使用时刻（epoch millis） */
    private Long lastUsedAt;

    /** 启用开关：0/1 */
    private Integer enabled;

    /** 内置产品标记：0/1（内置行 device_id='builtin'，id 取 1~99 预留小数值段，与雪花大数天然不撞） */
    private Integer isBuiltin;

    /** 备注 */
    private String note;

    /** 图标（后台维护时为 plugin-file 的 file_id；自定义行为 App 本地相对路径） */
    private String iconPath;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识（内置行固定 'builtin'） */
    private String deviceId;
}
