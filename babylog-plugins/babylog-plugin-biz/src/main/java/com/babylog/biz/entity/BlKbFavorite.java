package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 育儿知识库 · 收藏（K6-S / M76，服务端专用表）。
 *
 * <p><b>刻意不继承 {@code BaseDO}</b>：本表是纯关联表、不带 {@code deleted} 列，
 * 取消收藏走物理 DELETE。若继承带 {@code @TableLogic} 的 BaseDO，逻辑删的行仍占
 * {@code uk_user_faq} 唯一键，用户「取消 → 再收藏」会撞键（同 V2.2.20 对计次表的判断）。
 * 同理也不带 {@code update_by/update_time}，无审计必要。
 *
 * <p>不进 {@code SyncTable}、不参与 {@code /sync/changes}、不进 outbox。
 * {@code userId} 恒取自 app-user 裸 token，客户端绝不传 userId。
 */
@Data
@TableName("bl_kb_favorite")
public class BlKbFavorite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花，禁自增） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 收藏人 user_id */
    private Long userId;

    /** bl_kb_faq.id */
    private Long faqId;

    /** 收藏时间（由业务层显式写入，不经 AuditMetaObjectHandler） */
    private LocalDateTime createTime;
}
