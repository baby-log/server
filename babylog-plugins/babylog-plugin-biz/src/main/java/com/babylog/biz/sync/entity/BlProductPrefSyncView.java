package com.babylog.biz.sync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.biz.entity.BlProductPref;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * bl_product_prefs 的<b>同步通道视图实体</b>（M67，口径同 {@link BlProductSyncView}）。
 *
 * <p>遮蔽基类三处声明：deleted 去 @TableLogic（同步软删除是信号，墓碑行必须
 * 可读/可覆盖/可下发）、version 去 @Version（客户端载荷 + 服务端条件写入门禁，
 * 不走 MP 乐观锁自动 +1）、serverUpdatedAt 作只读游标列（由数据库
 * ON UPDATE CURRENT_TIMESTAMP(3) 维护）。
 */
@Getter
@Setter
@TableName("bl_product_prefs")
public class BlProductPrefSyncView extends BlProductPref {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 同步软删除信号（遮蔽 BaseDO.@TableLogic，见类注释） */
    @TableField("deleted")
    private Integer deleted;

    /** 同步版本号（遮蔽 BlSyncDO.@Version，见类注释） */
    @TableField("version")
    private Long version;

    /** 服务端同步游标（只读游标列，见类注释） */
    @TableField("server_updated_at")
    private LocalDateTime serverUpdatedAt;
}
