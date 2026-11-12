-- ============================================================
-- BabyLog V2.0 第二批（会话 M5）：bl_device 补设备唯一标识
--
-- 背景：V2.2.4 建 bl_device 时只考虑了「换机/踢出/推送」的服务端视角，
-- 没有客户端设备标识列，登录时无法幂等落行（每次登录都会多一条孤儿行）。
-- 本迁移补 device_uuid（App 首次启动生成、登录时上报），
-- 并建 (user_id, device_uuid) 唯一键，使登录落行可走 upsert 语义。
--
-- 口径说明：
-- 1. device_uuid 允许 NULL（老版本 App 未上报设备标识时不阻断登录，
--    MySQL 唯一索引对 NULL 不去重，因此多行 NULL 不会冲突）；
-- 2. 不改 bl_device 的其余列，worker_id（设备级雪花位）留到 M6 同步接口会话，
--    与 sync/push 的归属校验一并落地，避免现在先埋一个没有消费方的列；
-- 3. 表仍无 tenant_id（多租户不启用）。
-- ============================================================

ALTER TABLE `bl_device`
    ADD COLUMN `device_uuid` VARCHAR(64) DEFAULT NULL COMMENT '设备唯一标识（App 首次启动生成，登录时上报）' AFTER `user_id`;

ALTER TABLE `bl_device`
    ADD UNIQUE KEY `uk_user_device` (`user_id`, `device_uuid`);
