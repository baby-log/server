package com.babylog.biz.sync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.biz.entity.BlAppVersion;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * bl_app_version 的<b>基础数据通道视图实体</b>（PivotOS 严格整改 S3）。
 *
 * <p>与业务实体唯一差别：遮蔽/补齐两个字段的基类声明——
 * <ul>
 *   <li>deleted：遮蔽 BaseDO 的 @TableLogic。base 通道是单向下发，但下发的行同样含
 *       软删除事实（客户端要应用墓碑），所以拉取必须对 deleted 全量可见；业务实体原样
 *       不动，管理端查询「默认只看有效数据」的语义零变化；</li>
 *   <li>serverUpdatedAt：base 运营表的业务实体刻意不声明该字段（M10/M18 教训：被 MP
 *       显式写回旧值会导致游标不前进），视图层补上——它是增量下发的<b>只读游标列</b>，
 *       base 通道无任何写路径，不存在写回风险。</li>
 * </ul>
 *
 * <p>MP 字段收集<b>子类优先</b>，遮蔽只影响视图类。新增业务列只改业务实体，这里自动继承。
 */
@Getter
@Setter
@TableName("bl_app_version")
public class BlAppVersionSyncView extends BlAppVersion {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 软删除事实（遮蔽 BaseDO.@TableLogic，见类注释） */
    @TableField("deleted")
    private Integer deleted;

    /** 服务端同步游标（只读游标列，见类注释） */
    @TableField("server_updated_at")
    private LocalDateTime serverUpdatedAt;
}
