package com.babylog.biz.sync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.biz.entity.BlDevice;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * bl_device 的<b>无逻辑删除视图实体</b>（PivotOS 严格整改 S3）。
 *
 * <p>workerId 号段查询需要看到「被软删但仍占号」的设备行，而 BaseDO 的 @TableLogic
 * 会把它们从查询路径滤掉（S2 曾用 deleted=0 OR deleted=1 恒真条件绕过——属表面绕过，
 * 本阶段清除）。视图实体遮蔽 deleted 的 @TableLogic 声明后，查询语义即「全行」，
 * 条件里<b>一个字都不需要提 deleted</b>。bl_device 全插件无软删代码路径（踢出 = status
 * KICKED），该列实际恒 0，视图只是把这个事实钉进类型系统。业务实体与 BlDeviceMapper
 * 原样不动。
 */
@Getter
@Setter
@TableName("bl_device")
public class BlDeviceView extends BlDevice {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 遮蔽 BaseDO.@TableLogic（见类注释） */
    @TableField("deleted")
    private Integer deleted;
}
