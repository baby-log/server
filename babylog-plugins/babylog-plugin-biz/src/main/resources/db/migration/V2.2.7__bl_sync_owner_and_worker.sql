-- ============================================================
-- 同步归属列 + 设备 workerId（V2.2.7，M6 会话）
--
-- 口径（2026-11-12 拍板）：
-- 1. 18 张同步表统一补 `user_id`（归属用户）。M4 建表时只有 bl_children.owner_user_id，
--    其余 17 表没有归属列——拉取过滤与 push 归属校验若靠 join children 推导，
--    18 张表要写 18 条不同的归属路径，且每页查询都要回表。同步表去范式化归属列是标准做法：
--    读写都只需 (user_id, server_updated_at) 一个复合索引。
-- 2. `user_id` 由服务端按 token 写入，**不接受客户端传值**（push 请求体里的 user_id 一律忽略）。
-- 3. 索引 `idx_user_sync (user_id, server_updated_at)` 直接服务 changes 游标查询；
--    原有的 `idx_sync_cursor (server_updated_at)` 保留（跨用户游标统计/运营查询仍用得上）。
-- 4. DEFAULT 0 只为本迁移之前的历史行兜底（dev 探针数据）——这些行归属 user 0，
--    任何真实用户都拉不到，等价于不可见；生产库此时为空表，无影响。
-- 5. bl_device.worker_id：设备级雪花机器位（0-1023），登录时按「同用户内 max+1」分配，
--    由登录响应回传给 App 用于生成雪花 ID。uk(user_id, worker_id) 是并发分配的最终防线
--    （MySQL 唯一键允许多个 NULL，历史行不受影响）。
-- 6. bl_children 同时保留 owner_user_id（宝宝所有权，V2.1 转家庭）与 user_id（同步归属）：
--    V2.0 两者恒相等，语义分工写在实体注释里，不是冗余。
-- 7. bl_family_members 例外：M4 建表时就有 user_id（成员的用户 id），它<b>就是</b>本表需要的
--    同步归属列（V2.0 单用户模型下一个家庭只有 owner 一行，user_id 恒等于当前账号），
--    因此只补索引不补列。V2.1 家庭共享后该列语义会变成「被邀请成员」，届时同步归属
--    需改用 family_id 维度——已登记为 V2.1 待办，不是本迁移的遗漏。
-- ============================================================

-- ---------- 18 张同步表补归属列 ----------
ALTER TABLE `bl_children`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_feedings`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_diapers`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_sleeps`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_record_type_settings`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_family_members`
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_member_permissions`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_products`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_measurements`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_medications`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_baths`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_spits`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_pump_sessions`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_vaccinations`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_follow_ups`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_visits`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_attachments`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

ALTER TABLE `bl_reminders`
    ADD COLUMN `user_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '归属用户（同步隔离，服务端按 token 写入）' AFTER `id`,
    ADD KEY `idx_user_sync` (`user_id`, `server_updated_at`);

-- ---------- 设备级雪花机器位 ----------
ALTER TABLE `bl_device`
    ADD COLUMN `worker_id` INT(11) DEFAULT NULL COMMENT '设备级雪花 workerId（0-1023，登录时按同用户 max+1 分配）' AFTER `device_uuid`,
    ADD UNIQUE KEY `uk_user_worker` (`user_id`, `worker_id`);
