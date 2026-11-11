package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 疫苗时间表（服务端运营表，后台维护、App 端增量拉取）。
 * 注意与 bl_vaccinations（用户接种实例）是两个概念。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_vaccine_schedule")
public class BlVaccineSchedule extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 疫苗名称 */
    private String vaccineName;

    /** 第几剂 */
    private Integer doseNo;

    /** 建议起始月龄（天） */
    private Integer startAgeDays;

    /** 建议截止月龄（天） */
    private Integer endAgeDays;

    /** 费用类别：FREE/SELF_PAID */
    private String chargeType;

    /** 类别（如 免疫规划/非免疫规划 的分组展示） */
    private String category;

    /** 备注 */
    private String note;

    /** 排序号 */
    private Integer sortOrder;
}
