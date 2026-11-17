package com.babylog.biz.sync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.biz.entity.BlSpit;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;
/**
 * bl_spits 的<b>同步通道视图实体</b>（PivotOS 严格整改 S3）。
 *
 * <p>与业务实体唯一差别：遮蔽若干字段的基类声明，让 MyBatis-Plus 的元数据对<b>本类</b>
 * 不再启用对应机制——
 * <ul>
 *   <li>deleted：遮蔽 BaseDO 的 @TableLogic——逻辑删除是 MP 建 MappedStatement 时
 *       静态烘焙进 SQL 的（3.5.17 无任何运行时绕过开关），而同步通道的软删除是
 *       <b>同步信号</b>（deleted=1 的行必须可读、可覆盖、可下发）。视图实体不带
 *       @TableLogic → 同步读写路径对 deleted 全量可见；业务实体原样不动，普通业务/
 *       管理查询「默认只看有效数据」的语义零变化；</li>
 *   <li>version：遮蔽 BlSyncDO 的 @Version——同步版本号是<b>客户端载荷、服务端条件
 *       写入</b>（WHERE version &lt; 推送版本 的原子门禁），绝不走 MP 乐观锁的自动 +1；</li>
 *   <li>serverUpdatedAt：遮蔽 BlSyncDO 的声明——本类里它是增量拉取的<b>只读游标列</b>，insert/update 一律由数据库 ON UPDATE CURRENT_TIMESTAMP(3) 维护</li>
 * </ul>
 *
 * <p>MP 字段收集<b>子类优先</b>（同名父类字段不进本类 TableInfo），遮蔽只影响视图类，
 * 不影响业务实体。新增业务列只改业务实体，这里自动继承。
 */
@Getter
@Setter
@TableName("bl_spits")
public class BlSpitSyncView extends BlSpit {

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
