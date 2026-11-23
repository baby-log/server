package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 产品偏好（第 19 张同步表，M67）：启停 / 默认 / 上次使用，从产品行拆出。
 *
 * <p>拆出原因：内置产品（bl_products id 1~99）走 base 通道单向下发、push 8345 拒，
 * 行不可写——「内置产品也可设默认/启停」只能由独立偏好表承载（偏好行自身 id 是新雪花，
 * 不触发内置段拒推）。
 *
 * <p>偏好语义 = 家庭共享（2026-11-23 拍板）：行随 visibleUserIds 下发，家庭内成员
 * 可见可改（M16 家庭维度归属放行）；客户端写前查同 product_id 已有行收敛，竞态产生的
 * 跨账号重复行由读侧「按 version 取最新」防御。
 *
 * <p>{@code category} 是从产品行冗余来的分类（FORMULA/SUPPLEMENT/MEDICINE/DIAPER），
 * 「每分类至多一个默认」的判定免 join；客户端写入时必须与产品行一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_product_prefs")
public class BlProductPref extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 id（内置 1~99 或自定义雪花） */
    private Long productId;

    /** 冗余产品分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER（默认唯一性判定免 join） */
    private String category;

    /** 启用开关：0/1（覆盖产品行展示；内置产品启停只存在这里） */
    private Integer enabled;

    /** 默认产品：0/1（家庭内每分类至多一个，客户端收敛） */
    private Integer isDefault;

    /** 最近被记录使用时刻（epoch millis） */
    private Long lastUsedAt;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
