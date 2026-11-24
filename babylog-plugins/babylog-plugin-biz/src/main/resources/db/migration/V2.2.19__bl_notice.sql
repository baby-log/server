-- ============================================================
-- K4 / M71 · 站内通知 + 单人确认 + 时间口径 + 无头异常（接收侧闭环，零画面）
--
-- 契约来源：独立产品规划/24-后续链统一契约.md ⑤ 站内通知 / ⑥ 单人确认 / ⑦ 无头异常
--   与 bl_media_file / bl_media_quota / bl_family / bl_device 等同口径：
--   **服务端专用表，明确不进 SyncTable 枚举、不参与 /sync/changes**（不新增第 20 张同步表），
--   端点不占 /sync/* 命名空间，按 visibleUserIds 拉取式下发。
--
-- 全局契约坑（24 §五 / MEMORY.md / 13 §1）：
--   1. user_id 是直写 bl_* 表的必填语义列——服务端按 token 盖章，读侧 user_id IN visibleUserIds；
--   2. 所有时间/ID/游标列用 BIGINT(epoch millis)，暴露给四端的 JSON 一律字符串（WriteLongAsString）；
--   3. TINYINT 布尔用 0/1（is_headless / confirmed / abnormal / deleted），绝不 true/false；
--   4. 逻辑删靠 deleted + server_updated_at ON UPDATE，不手写 version+1；
--      version 是「单人确认」乐观锁 CAS 的 WHERE 条件（先到者赢），并非同步游标——
--      与 /sync/changes 的 server_updated_at 游标是两套东西，绝不混用（api/sync.js 同款纪律）。
--
-- V3 前零画面数据：本表只存结构化事件 / 站内消息，不存任何画面/帧/字节（K9 才接采集侧）。
-- 自动检测永不自动落库保持：PENDING 待确认事件必须经成员确认才「落正式记录」（走客户端 /sync/push
--   到既有 19 张同步表）；本表是接收侧消息通道，服务端不代写正式记录、不新增同步表。
-- MySQL DDL 隐式提交，不进事务。
-- ============================================================

CREATE TABLE IF NOT EXISTS `bl_notice` (
    `id`                 BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`           BIGINT(20)     NOT NULL                COMMENT '所属宝宝 id',
    `user_id`            BIGINT(20)     NOT NULL                COMMENT '归属用户 id（服务端按 token 写入；visibleUserIds 读侧口径，非家庭成员不可见）',
    `notice_type`        VARCHAR(24)    NOT NULL                COMMENT '通知类型：PENDING_CONFIRM 待确认事件 / HEADLESS_ANOMALY 无头异常（24 ⑤-6）',
    `title`              VARCHAR(128)   DEFAULT NULL            COMMENT '站内消息标题（轻量）',
    `record_type`        VARCHAR(32)    DEFAULT NULL            COMMENT '关联记录类型（事件语义，如 SLEEP/FEEDING；24 ⑥ detectedAt 落正式记录用）',
    `detected_at`        BIGINT(20)     DEFAULT NULL            COMMENT '识别/检测时刻（epoch millis；13:30 detected 的业务时间来源，落正式记录用此值）',
    `confirmed_at`       BIGINT(20)     DEFAULT NULL            COMMENT '确认时刻（epoch millis；13:50 confirm 仅作审计，正式记录业务时间不取此值）',
    `confirmed_by`       BIGINT(20)     DEFAULT NULL            COMMENT '确认人 user id（先到者；NULL=未确认）',
    `event_at`           BIGINT(20)     DEFAULT NULL            COMMENT '合成单点行的单个时间点（epoch millis；仅无头异常，非计时行、无配对、无 endedAt，24 ⑦-2）',
    `anomaly_kind`       VARCHAR(32)    DEFAULT NULL            COMMENT '无头异常分类：WAKE_WITHOUT_SLEEP 醒来无入睡 / END_WITHOUT_START 结束无开始（24 ⑦-1）',
    `is_headless`        TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '无头异常标记：0/1（合成单点行 + 异常标记，不伪造配对、不假装完整会话）',
    `is_confirmed`       TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '确认状态：0/1（单人确认原子状态机的最终态；全体成员可见）',
    `is_abnormal`        TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '异常标记：0/1（与 is_headless 同源，供读侧统一过滤）',
    `version`            BIGINT(20)     NOT NULL DEFAULT 1      COMMENT '乐观锁版本（单人确认 CAS：WHERE version=期望值，先到者赢、后到者出 conflicts 拉取；非同步游标）',
    `deleted`            TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（@TableLogic）',
    `created_at`         BIGINT(20)     NOT NULL                COMMENT '创建时刻（epoch millis）',
    `server_updated_at`  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端变更游标（双段 since+sinceId 增量拉；确认后 ON UPDATE 自动推进，让后到者拉到已确认态）',
    `create_by`          BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`        DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`          BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`        DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    PRIMARY KEY (`id`),
    KEY `idx_user_cursor` (`user_id`, `server_updated_at`, `id`),
    KEY `idx_child_type` (`child_id`, `notice_type`),
    KEY `idx_pending` (`notice_type`, `is_confirmed`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知（服务端专用表；不进 SyncTable/不参与 /sync/changes；按 visibleUserIds 拉取式；V3 前零画面数据）';
