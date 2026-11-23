-- ============================================================
-- K2 / M69 · 媒体存储底座（V3 前地基，零画面数据）
--
-- 契约来源：独立产品规划/24-后续链统一契约.md ② 媒体表 / ③ 存储适配器 / ④ 淘汰契约
--   与 bl_family / bl_device / bl_vaccine_schedule / bl_ai_quota 同口径：
--   **服务端专用表，明确不进 SyncTable 枚举、不参与 /sync/changes**（不新增第 20 张同步表）。
--
-- 全局契约坑（24 §五 / MEMORY.md）：
--   1. user_id 是直写 bl_* 表的必填语义列——正服迁移漏写落 DEFAULT 0 拉不到的坑；
--   2. 时间列用 BIGINT(epoch millis)：detected_at / confirmed_at / created_at；
--   3. TINYINT 布尔用 0/1，暴露给端时是整数（绝不 true/false）；
--   4. 逻辑删靠 deleted + server_updated_at ON UPDATE，不手写 version+1
--      （两表非同步表，preserveOnAbsentProperties / 同步游标专属坑不适用）。
--
-- V3 前零画面数据：本批只建底座表，不写任何画面行（E6 就医附件 / V3.1 拍照导入才产生数据）。
-- MySQL DDL 隐式提交，不进事务。
-- ============================================================

-- ---------- 媒体对象元数据（服务端专用表，不进同步通道） ----------
CREATE TABLE IF NOT EXISTS `bl_media_file` (
    `id`                 BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`           BIGINT(20)     NOT NULL                COMMENT '所属宝宝 id',
    `user_id`            BIGINT(20)     NOT NULL                COMMENT '归属用户 id（服务端按 token 写入，禁客户端传值；visibleUserIds 读侧口径）',
    `storage_backend`    VARCHAR(16)    NOT NULL                COMMENT '存储后端标识：local/cos/oss（按行路由；配置变更只影响新写入，存量键不变）',
    `object_key`         VARCHAR(512)   NOT NULL                COMMENT '对象键（含路径；行内固化，禁止改配置即隐式全量重定位）',
    `size_bytes`         BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '文件字节数',
    `mime_type`          VARCHAR(128)   DEFAULT NULL            COMMENT 'MIME 类型（如 image/jpeg；V3 前仅附件语义）',
    `linked_record_type` VARCHAR(32)    DEFAULT NULL            COMMENT '关联记录类型（与 linked_record_id 同时非空 ⇒ 已关联正式记录，不参与任何淘汰）',
    `linked_record_id`   BIGINT(20)     DEFAULT NULL            COMMENT '关联记录 id（雪花）',
    `detected_at`        BIGINT(20)     DEFAULT NULL            COMMENT '采集/识别时刻（epoch millis；未确认淘汰基准）',
    `confirmed_at`       BIGINT(20)     DEFAULT NULL            COMMENT '确认时刻（epoch millis；NULL=未确认，确认后 2h 淘汰）',
    `is_final_frame`     TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '定稿帧标记：0/1（确认瞬间转存记录附件，长期保留）',
    `deleted`            TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（DB 逻辑删 + 适配器物理删两步；@TableLogic）',
    `created_at`         BIGINT(20)     NOT NULL                COMMENT '创建时刻（epoch millis；容量满「删最早」排序键）',
    `server_updated_at`  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端变更游标（非同步表，仅审计/运维）',
    `create_by`          BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`        DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`          BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`        DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    PRIMARY KEY (`id`),
    KEY `idx_child_user` (`child_id`, `user_id`),
    KEY `idx_retention_confirmed` (`deleted`, `confirmed_at`),
    KEY `idx_retention_unconfirmed` (`deleted`, `detected_at`),
    KEY `idx_capacity` (`child_id`, `deleted`, `created_at`),
    KEY `idx_linked` (`linked_record_type`, `linked_record_id`),
    KEY `idx_backend` (`storage_backend`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体对象元数据（服务端专用表；不进 SyncTable/不参与 /sync/changes；V3 前零画面数据）';

-- ---------- 按宝宝容量配额（服务端专用表，不进同步通道） ----------
-- used_bytes 口径（24 ②-5 二选一）：本批选「由 bl_media_file 聚合」，不落冗余列，
-- 避免双写漂移；读侧 SUM(size_bytes) WHERE deleted=0，淘汰任务复用同一口径。
CREATE TABLE IF NOT EXISTS `bl_media_quota` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id（唯一，每行一宝宝）',
    `user_id`           BIGINT(20)     NOT NULL                COMMENT '归属用户 id（必填语义列；visibleUserIds 读侧口径）',
    `initial_bytes`     BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '初始容量（字节；读侧展示用，后台不改写）',
    `adjusted_bytes`    BIGINT(20)     DEFAULT NULL            COMMENT '后台调整后容量（字节；NULL=未调整，生效容量回落 initial_bytes）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（@TableLogic）',
    `created_at`        BIGINT(20)     NOT NULL                COMMENT '创建时刻（epoch millis）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端变更游标（非同步表，仅审计/运维）',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_child` (`child_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宝宝媒体容量配额（服务端专用表；初始容量+后台动态调整；不进 SyncTable）';
