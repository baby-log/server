package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.Version;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * BabyLog 同步表基类：在 BaseDO（雪花主键 + 审计 + 逻辑删除）之上增加同步两件套。
 *
 * <p>同步表与 App 端（Room）表结构一一对应，设计口径（2026-11-11 拍板）：
 * <ul>
 *   <li>主键统一雪花 BIGINT（App 端 V2.0 改造时迁离 UUID 字符串主键）</li>
 *   <li>业务时间列（started_at / created_at / updated_at 等）一律 BIGINT 存 epoch millis，
 *       与 App 端 Room 列直接对齐；BaseDO 审计四件套（create_time 等 DATETIME）保留服务端管理口径，两层并存</li>
 *   <li>{@code deleted} 与 App 端软删除标记同名同义，既是 @TableLogic 又是同步软删除信号</li>
 *   <li>{@code version} 乐观锁 = 同步协议的记录版本号（last-write-wins 裁决依据）</li>
 *   <li>{@code server_updated_at} 服务端时钟的同步游标（changes?since= 基准），
 *       由数据库 DEFAULT/ON UPDATE CURRENT_TIMESTAMP(3) 自动维护，业务代码禁手工赋值</li>
 *   <li>{@code user_id} 同步归属（V2.2.7 补，M6）：拉取过滤与 push 归属校验的唯一依据，
 *       由服务端按 token 写入，<b>不接受客户端传值</b>。
 *       与 {@code bl_children.owner_user_id} 的分工：owner_user_id 是「宝宝属于谁」（业务语义，
 *       V2.1 家庭共享时转成 family_id），user_id 是「这条副本属于哪个账号」（同步隔离语义）。
 *       V2.0 单用户模型下两者恒相等，但不是同一件事，故不合并。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BlSyncDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 同步归属用户（服务端按 token 写入；禁止从客户端 payload 取值） */
    private Long userId;

    /** 同步版本号（乐观锁，每次写 +1；App 端 last-write-wins 裁决依据） */
    @Version
    private Long version;

    /** 服务端同步游标（拉取 since 基准，数据库自动维护） */
    private LocalDateTime serverUpdatedAt;
}
