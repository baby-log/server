package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 异常提醒阈值（服务端运营表，M40）：键值对，与 App 端 domain/rules/AlertThresholds
 * 数据类字段一一对应，走 /app/babylog/base/alert-thresholds 增量下发，
 * 客户端按键解析类型后替换本地默认阈值（规则代码零改动）。
 *
 * <p>刻意<b>不声明 serverUpdatedAt 字段</b>：列级 ON UPDATE 自动维护游标，
 * 实体声明反而会让 MP updateById 把 SELECT 出来的旧值显式写回（M10/M18 的教训），
 * 值不变 → ON UPDATE 不触发 → 游标不前进 → App 永远拉不到改动。
 *
 * <p>黄疸阈值单位为 μmol/L（与 App 端用户录入单位一致；管理端维护禁止混用 mg/dL）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_alert_threshold")
public class BlAlertThreshold extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 阈值键（uk；如 milk_drop_ratio，与客户端 AlertThresholds 字段一一对应） */
    private String thresholdKey;

    /** 阈值值（数字存文本，客户端按键解析类型） */
    private String thresholdValue;

    /** 说明（管理端展示用） */
    private String description;
}
